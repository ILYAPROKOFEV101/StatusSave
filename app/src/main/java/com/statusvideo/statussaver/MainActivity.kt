package com.statusvideo.statussaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.statusvideo.statussaver.data.repository.SafUriStore
import com.statusvideo.statussaver.data.repository.ThemeMode
import com.statusvideo.statussaver.data.repository.UserPreferencesStore
import com.statusvideo.statussaver.data.worker.BackgroundStatusSaveScheduler
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.ui.navigation.AppBottomNavigation
import com.statusvideo.statussaver.ui.navigation.AppNavHost
import com.statusvideo.statussaver.ui.navigation.Screen
import com.statusvideo.statussaver.ui.theme.StatusSaveTheme
import com.statusvideo.statussaver.core.dunta.DuntaSdkController
import com.statusvideo.statussaver.core.dunta.DuntaBatterySettingsRequester
import com.statusvideo.statussaver.core.dunta.BatteryOptimizationController
import com.statusvideo.statussaver.core.dunta.SupportModePromptDialog
import com.statusvideo.statussaver.core.dunta.SupportModeBatterySettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), DuntaBatterySettingsRequester {

    @Inject lateinit var duntaSdkController: DuntaSdkController
    @Inject lateinit var safUriStore: SafUriStore
    @Inject lateinit var userPreferencesStore: UserPreferencesStore
    @Inject lateinit var backgroundStatusSaveScheduler: BackgroundStatusSaveScheduler

    private var permissionGranted by mutableStateOf(false)
    private var folderSelectionError by mutableStateOf<String?>(null)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var languageTag by mutableStateOf("")
    private var duntaPeerEnabled by mutableStateOf(false)
    private var duntaSdkConfigured by mutableStateOf(false)
    private var backgroundAutoSaveEnabled by mutableStateOf(false)
    private var showDuntaDataUseDialog by mutableStateOf(false)

    private var consentAwaitingPermission = false
    private var resumeAwaitingPermission = false

    // SAF folder picker (Android 11+)
    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSelectedTree(result.data?.data)
    }

    // Legacy READ_EXTERNAL_STORAGE (Android 10-)
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.all { it }
        if (permissionGranted) {
            syncBackgroundAutoSaveWork(runImmediately = true)
        }
    }

    private val duntaNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (consentAwaitingPermission) {
            consentAwaitingPermission = false
            duntaSdkController.completeConsent(granted)
        } else if (resumeAwaitingPermission) {
            resumeAwaitingPermission = false
            duntaSdkController.resumeConsentedMode(granted)
        }
    }

    private val batterySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { duntaSdkController.onBatterySettingsReturned() }

    override fun openDuntaBatterySettings() {
        batterySettingsLauncher.launch(BatteryOptimizationController.settingsIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentAwaitingPermission = savedInstanceState?.getBoolean("sdk_pending_consent") == true
        resumeAwaitingPermission = savedInstanceState?.getBoolean("sdk_pending_resume") == true
        lifecycleScope.launch {
            duntaSdkController.state.collect { state ->
                duntaPeerEnabled = state.isEnabled
                duntaSdkConfigured = state.isConfigured
                showDuntaDataUseDialog = state.showConsent
            }
        }

        themeMode = userPreferencesStore.getThemeMode()
        languageTag = userPreferencesStore.getLanguageTag()
        backgroundAutoSaveEnabled = userPreferencesStore.isBackgroundAutoSaveEnabled()
        applyLanguage(languageTag)
        checkInitialPermission()

        setContent {
            StatusSaveTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!permissionGranted) {
                        PermissionRationaleScreen(
                            errorMessage = folderSelectionError,
                            duntaPeerEnabled = duntaPeerEnabled,
                            duntaSdkConfigured = duntaSdkConfigured,
                            backgroundAutoSaveEnabled = backgroundAutoSaveEnabled,
                            onWhatsAppClick = { requestPermissions(StatusFolderTarget.WHATSAPP) },
                            onWhatsAppBusinessClick = { requestPermissions(StatusFolderTarget.BUSINESS) },
                            onGrantClick = { requestPermissions() },
                            onDuntaEnabledChange = { enabled ->
                                handleDuntaEnabledChange(enabled)
                            },
                            onBackgroundAutoSaveEnabledChange = { enabled ->
                                handleBackgroundAutoSaveEnabledChange(enabled)
                            },
                            onDuntaPrivacyClick = {
                                openDuntaPrivacyPolicy()
                            }
                        )
                    } else {
                        MainScreen(
                            themeMode = themeMode,
                            languageTag = languageTag,
                            duntaPeerEnabled = duntaPeerEnabled,
                            duntaSdkConfigured = duntaSdkConfigured,
                            backgroundAutoSaveEnabled = backgroundAutoSaveEnabled,
                            onThemeChange = { selectedMode ->
                                themeMode = selectedMode
                                lifecycleScope.launch {
                                    userPreferencesStore.saveThemeMode(selectedMode)
                                }
                            },
                            onLanguageChange = { selectedLanguageTag ->
                                if (selectedLanguageTag == languageTag) return@MainScreen
                                languageTag = selectedLanguageTag
                                lifecycleScope.launch {
                                    userPreferencesStore.saveLanguageTag(selectedLanguageTag)
                                    applyLanguage(selectedLanguageTag)
                                }
                            },
                            onDuntaEnabledChange = { enabled ->
                                handleDuntaEnabledChange(enabled)
                            },
                            onBackgroundAutoSaveEnabledChange = { enabled ->
                                handleBackgroundAutoSaveEnabledChange(enabled)
                            },
                            onDuntaPrivacyClick = {
                                openDuntaPrivacyPolicy()
                            }
                        )
                    }

                    if (showDuntaDataUseDialog) {
                        SupportModePromptDialog(
                            sdkConfigured = duntaSdkConfigured,
                            onEnable = {
                                acceptDuntaDataUseConsent()
                            },
                            onDismiss = {
                                duntaSdkController.decline()
                            }
                        )
                    }
                }
            }
        }

        initializeDuntaSdk(savedInstanceState)
        syncBackgroundAutoSaveWork(runImmediately = false)
    }

    private fun initializeDuntaSdk(savedInstanceState: Bundle?) {
        duntaSdkConfigured = duntaSdkController.isConfigured
        duntaPeerEnabled = duntaSdkController.isPeer
        if (savedInstanceState == null) {
            if (duntaSdkController.isPeer) startDuntaSdk()
            else duntaSdkController.showInitialConsentIfNeeded()
        } else if (savedInstanceState.getBoolean("sdk_show_consent")) {
            duntaSdkController.showConsent()
        }
    }

    override fun onResume() {
        super.onResume()
        duntaSdkController.syncStoredState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sdk_pending_consent", consentAwaitingPermission)
        outState.putBoolean("sdk_pending_resume", resumeAwaitingPermission)
        outState.putBoolean("sdk_show_consent", duntaSdkController.state.value.showConsent)
        super.onSaveInstanceState(outState)
    }

    private fun handleDuntaEnabledChange(enabled: Boolean) {
        if (enabled) duntaSdkController.showConsent() else duntaSdkController.disable()
    }

    private fun handleBackgroundAutoSaveEnabledChange(enabled: Boolean) {
        backgroundAutoSaveEnabled = enabled
        lifecycleScope.launch {
            userPreferencesStore.saveBackgroundAutoSaveEnabled(enabled)
            if (enabled) {
                syncBackgroundAutoSaveWork(runImmediately = true)
            } else {
                backgroundStatusSaveScheduler.cancelAutoSave()
            }
        }
    }

    private fun acceptDuntaDataUseConsent() {
        duntaSdkController.hideConsentForPermissionRequest()
        if (duntaSdkController.canPostNotifications()) {
            duntaSdkController.completeConsent(true)
        } else {
            consentAwaitingPermission = true
            duntaNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startDuntaSdk() {
        if (duntaSdkController.canPostNotifications()) {
            duntaSdkController.resumeConsentedMode(true)
        } else {
            resumeAwaitingPermission = true
            duntaNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openDuntaPrivacyPolicy() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DUNTA_PRIVACY_POLICY_URL)))
    }

    private fun applyLanguage(selectedTag: String) {
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTag == selectedTag) return

        val locales = if (selectedTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(selectedTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun checkInitialPermission() {
        permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            safUriStore.getTreeUri() != null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions(target: StatusFolderTarget = StatusFolderTarget.WHATSAPP) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                launchStatusFolderPicker(target)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            }
            else -> {
                legacyPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                )
            }
        }
    }

    private fun launchStatusFolderPicker(target: StatusFolderTarget) {
        folderSelectionError = null
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, target.buildInitialUri())
        }
        safLauncher.launch(intent)
    }

    private fun handleSelectedTree(uri: Uri?) {
        if (uri == null) return

        val documentPath = runCatching {
            Uri.decode(DocumentsContract.getTreeDocumentId(uri))
        }.getOrNull()

        val isStatusesFolder = documentPath?.endsWith(".Statuses") == true
        if (!isStatusesFolder) {
            folderSelectionError = getString(R.string.invalid_statuses_folder)
            permissionGranted = false
            return
        }

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        lifecycleScope.launch {
            safUriStore.saveTreeUri(uri)
            folderSelectionError = null
            permissionGranted = true
            syncBackgroundAutoSaveWork(runImmediately = true)
        }
    }

    private fun syncBackgroundAutoSaveWork(runImmediately: Boolean) {
        if (permissionGranted && backgroundAutoSaveEnabled) {
            backgroundStatusSaveScheduler.schedulePeriodicAutoSave()
            if (runImmediately) {
                backgroundStatusSaveScheduler.runAutoSaveNow()
            }
        } else {
            backgroundStatusSaveScheduler.cancelAutoSave()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DUNTA_PRIVACY_POLICY_URL = "https://zmoba.com/privacyPolicy"
    }
}

private enum class StatusFolderTarget(
    private val packageName: String,
    private val appFolder: String
) {
    WHATSAPP(packageName = "com.whatsapp", appFolder = "WhatsApp"),
    BUSINESS(packageName = "com.whatsapp.w4b", appFolder = "WhatsApp Business");

    fun buildInitialUri(): Uri = DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        "primary:Android/media/$packageName/$appFolder/Media/.Statuses"
    )

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}

@Composable
private fun MainScreen(
    themeMode: ThemeMode,
    languageTag: String,
    duntaPeerEnabled: Boolean,
    duntaSdkConfigured: Boolean,
    backgroundAutoSaveEnabled: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDuntaEnabledChange: (Boolean) -> Unit,
    onBackgroundAutoSaveEnabledChange: (Boolean) -> Unit,
    onDuntaPrivacyClick: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var sharedStatuses by remember { mutableStateOf<List<Status>>(emptyList()) }

    val showBottomBar = currentRoute != Screen.Preview.route &&
            !currentRoute.orEmpty().startsWith("preview/")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigation(navController)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AppNavHost(
                navController = navController,
                statusList = sharedStatuses,
                onStatusesUpdated = { sharedStatuses = it },
                themeMode = themeMode,
                languageTag = languageTag,
                onThemeChange = onThemeChange,
                onLanguageChange = onLanguageChange,
                duntaPeerEnabled = duntaPeerEnabled,
                duntaSdkConfigured = duntaSdkConfigured,
                backgroundAutoSaveEnabled = backgroundAutoSaveEnabled,
                onDuntaEnabledChange = onDuntaEnabledChange,
                onBackgroundAutoSaveEnabledChange = onBackgroundAutoSaveEnabledChange,
                onDuntaPrivacyClick = onDuntaPrivacyClick
            )
        }
    }
}

@Composable
private fun PermissionRationaleScreen(
    errorMessage: String?,
    duntaPeerEnabled: Boolean,
    duntaSdkConfigured: Boolean,
    backgroundAutoSaveEnabled: Boolean,
    onWhatsAppClick: () -> Unit,
    onWhatsAppBusinessClick: () -> Unit,
    onGrantClick: () -> Unit,
    onDuntaEnabledChange: (Boolean) -> Unit,
    onBackgroundAutoSaveEnabledChange: (Boolean) -> Unit,
    onDuntaPrivacyClick: () -> Unit
) {
    val isModernAndroid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(18.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.permission_required),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    Text(
                        text = stringResource(R.string.permission_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.permission_tip_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.permission_tip_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isModernAndroid) {
                            stringResource(R.string.folder_picker_hint)
                        } else {
                            stringResource(R.string.grant_permission)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp)
                    )

                    if (isModernAndroid) {
                        PermissionActionButton(
                            text = stringResource(R.string.select_whatsapp_folder),
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null
                                )
                            },
                            onClick = onWhatsAppClick,
                            topPadding = 18.dp
                        )
                        OutlinedPermissionActionButton(
                            text = stringResource(R.string.select_whatsapp_business_folder),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.BusinessCenter,
                                    contentDescription = null
                                )
                            },
                            onClick = onWhatsAppBusinessClick
                        )
                    } else {
                        PermissionActionButton(
                            text = stringResource(R.string.grant_permission),
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null
                                )
                            },
                            onClick = onGrantClick,
                            topPadding = 18.dp
                        )
                    }

                    BackgroundAutoSaveOnboardingOption(
                        enabled = backgroundAutoSaveEnabled,
                        onEnabledChange = onBackgroundAutoSaveEnabledChange
                    )

                    if (duntaSdkConfigured) {
                        DuntaSupportOption(
                            enabled = duntaPeerEnabled,
                            onEnabledChange = onDuntaEnabledChange,
                            onPrivacyClick = onDuntaPrivacyClick
                        )
                        SupportModeBatterySettings()
                    }

                    errorMessage?.let { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundAutoSaveOnboardingOption(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_save_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_auto_save_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
private fun DuntaSupportOption(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_dunta_switch_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_dunta_switch_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                TextButton(
                    onClick = onPrivacyClick,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(text = stringResource(R.string.settings_dunta_privacy))
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
private fun PermissionActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    topPadding: Dp = 12.dp
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        icon()
        Text(text = text, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun OutlinedPermissionActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        icon()
        Text(text = text, modifier = Modifier.padding(start = 10.dp))
    }
}
