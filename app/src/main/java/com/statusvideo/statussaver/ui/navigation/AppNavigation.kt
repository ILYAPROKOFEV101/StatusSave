package com.statusvideo.statussaver.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.statusvideo.statussaver.core.dunta.SupportModeBatterySettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.statusvideo.statussaver.R
import com.statusvideo.statussaver.data.repository.ThemeMode
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.ui.preview.PreviewScreen
import com.statusvideo.statussaver.ui.saved.SavedScreen
import com.statusvideo.statussaver.ui.statuslist.StatusListScreen

sealed class Screen(val route: String) {
    data object StatusList : Screen("status_list")
    data object Saved : Screen("saved")
    data object Settings : Screen("settings")
    data object Preview : Screen("preview/{initialIndex}") {
        fun createRoute(initialIndex: Int) = "preview/$initialIndex"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: @Composable () -> Unit
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.StatusList, R.string.nav_statuses) {
        Icon(Icons.Filled.Home, contentDescription = null)
    },
    BottomNavItem(Screen.Saved, R.string.nav_saved) {
        Icon(Icons.Filled.Favorite, contentDescription = null)
    },
    BottomNavItem(Screen.Settings, R.string.nav_settings) {
        Icon(Icons.Filled.Settings, contentDescription = null)
    }
)

@Composable
fun AppBottomNavigation(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                icon = item.icon,
                label = { Text(stringResource(item.labelRes)) }
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    statusList: List<Status>,
    onStatusesUpdated: (List<Status>) -> Unit,
    themeMode: ThemeMode,
    languageTag: String,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    duntaPeerEnabled: Boolean,
    duntaSdkConfigured: Boolean,
    backgroundAutoSaveEnabled: Boolean,
    onDuntaEnabledChange: (Boolean) -> Unit,
    onBackgroundAutoSaveEnabledChange: (Boolean) -> Unit,
    onDuntaPrivacyClick: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.StatusList.route
    ) {
        composable(Screen.StatusList.route) {
            StatusListScreen(
                onStatusClick = { statuses, index ->
                    onStatusesUpdated(statuses)
                    navController.navigate(Screen.Preview.createRoute(index))
                }
            )
        }

        composable(Screen.Saved.route) {
            SavedScreen(
                onStatusClick = { statuses, index ->
                    onStatusesUpdated(statuses)
                    navController.navigate(Screen.Preview.createRoute(index))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
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

        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("initialIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            PreviewScreen(
                statuses = statusList,
                initialIndex = initialIndex,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    languageTag: String,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    duntaPeerEnabled: Boolean,
    duntaSdkConfigured: Boolean,
    backgroundAutoSaveEnabled: Boolean,
    onDuntaEnabledChange: (Boolean) -> Unit,
    onBackgroundAutoSaveEnabledChange: (Boolean) -> Unit,
    onDuntaPrivacyClick: () -> Unit
) {
    val themeOptions = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark
    )
    val languageOptions = listOf(
        "" to R.string.settings_language_system,
        "en" to R.string.settings_language_english,
        "hi" to R.string.settings_language_hindi,
        "ur-PK" to R.string.settings_language_urdu_pakistan
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    SectionHeader(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_theme_title)
                    )
                    themeOptions.forEach { (option, labelRes) ->
                        SelectableOptionButton(
                            text = stringResource(labelRes),
                            selected = option == themeMode,
                            onClick = { onThemeChange(option) }
                        )
                    }

                    SectionHeader(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.settings_language_title)
                    )
                    languageOptions.forEach { (option, labelRes) ->
                        SelectableOptionButton(
                            text = stringResource(labelRes),
                            selected = option == languageTag,
                            onClick = {
                                if (option != languageTag) {
                                    onLanguageChange(option)
                                }
                            }
                        )
                    }

                    SectionHeader(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_data_title)
                    )
                    DataTransparencySection(onReviewClick = onDuntaPrivacyClick)
                    BackgroundAutoSaveOption(
                        enabled = backgroundAutoSaveEnabled,
                        onEnabledChange = onBackgroundAutoSaveEnabledChange
                    )

                    if (duntaSdkConfigured) {
                        SectionHeader(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_dunta_title)
                        )
                        DuntaSdkOption(
                            enabled = duntaPeerEnabled,
                            onEnabledChange = onDuntaEnabledChange,
                            onPrivacyClick = onDuntaPrivacyClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataTransparencySection(
    onReviewClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DisclosureText(
                title = stringResource(R.string.settings_data_app_title),
                body = stringResource(R.string.settings_data_app_body)
            )
            DisclosureText(
                title = stringResource(R.string.settings_data_sdk_title),
                body = stringResource(R.string.settings_data_sdk_body)
            )
            TextButton(onClick = onReviewClick) {
                Text(text = stringResource(R.string.settings_data_review))
            }
        }
    }
}

@Composable
private fun BackgroundAutoSaveOption(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_auto_save_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.settings_auto_save_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun DisclosureText(
    title: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DuntaSdkOption(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_dunta_switch_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.settings_dunta_switch_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(
                onClick = onPrivacyClick,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(text = stringResource(R.string.settings_dunta_privacy))
            }
            SupportModeBatterySettings()
        }

        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectableOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = text)
        }
    }
}
