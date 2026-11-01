package com.statusvideo.statussaver.core.dunta

import android.content.Context
import android.net.ConnectivityManager
import androidx.fragment.app.FragmentActivity
import com.progun.dunta_sdk.api.DuntaManager
import com.statusvideo.statussaver.BuildConfig
import com.statusvideo.statussaver.R
import android.content.res.Configuration
import java.util.Locale

internal class DuntaSdkEngine(context: Context) {
    private val appContext = context.applicationContext
    private val manager: DuntaManager = DuntaManager.create(appContext)
    private val partnerId: Int = BuildConfig.DUNTA_PARTNER_ID
    private val applicationId: Int = BuildConfig.DUNTA_APPLICATION_ID

    init {
        SupportModeDiagnostics.install(manager, appContext)
        diagnosticSnapshot("controller_created")
    }

    val isConfigured: Boolean
        get() = partnerId > 0 && applicationId > 0

    val isPeer: Boolean
        get() = runCatching { manager.isPeer() }.getOrDefault(false)

    val userState: DuntaManager.UserState
        get() = runCatching { manager.getUserState() }.getOrDefault(DuntaManager.UserState.NOT_INIT)

    fun start(context: Context, language: String): Boolean {
        if (!isConfigured || userState != DuntaManager.UserState.PEER) {
            diagnosticSnapshot("start_skipped", result = "not_ready")
            return false
        }
        diagnosticSnapshot("start_requested")
        prepare(context, language)
        val started = runCatching { manager.start(context.applicationContext) }.isSuccess
        syncRecovery()
        diagnosticSnapshot("start_finished", result = if (started) "success" else "failure")
        return started
    }

    fun enable(context: Context, language: String): Boolean {
        if (!isConfigured) {
            diagnosticSnapshot("enable_skipped", result = "not_configured")
            return false
        }
        diagnosticSnapshot("enable_requested")
        prepare(context, language)
        val safeContext = context.applicationContext
        val started = runCatching {
            manager.changeUserStatusTo(safeContext, true)
            manager.start(safeContext)
        }.isSuccess
        syncRecovery()
        diagnosticSnapshot("enable_finished", result = if (started) "success" else "failure")
        return started
    }

    fun stop(context: Context) {
        diagnosticSnapshot("stop_requested")
        val safeContext = context.applicationContext
        runCatching { manager.changeUserStatusTo(safeContext, false) }
        runCatching { manager.stop(safeContext) }
        SupportModeRecoveryScheduler.cancel(safeContext)
        diagnosticSnapshot("stop_finished", result = "success")
    }

    fun decline(context: Context) {
        val safeContext = context.applicationContext
        runCatching { manager.changeUserStatusTo(safeContext, false) }
        stop(safeContext)
    }

    internal fun restartIfEnabled(): Boolean {
        if (!isConfigured || userState != DuntaManager.UserState.PEER) {
            diagnosticSnapshot("restart_skipped", result = "not_ready")
            return false
        }
        diagnosticSnapshot("restart_requested")
        manager.setPartnerId(partnerId)
        manager.setApplicationId(applicationId)
        val restarted = runCatching { manager.start(appContext) }.isSuccess
        diagnosticSnapshot("restart_finished", result = if (restarted) "success" else "failure")
        return restarted
    }

    fun syncRecovery() {
        val batteryExempt = BatteryOptimizationController.isIgnoring(appContext)
        SupportModeRecoveryScheduler.sync(
            context = appContext,
            enabled = isConfigured && userState == DuntaManager.UserState.PEER,
            batteryOptimizationExempt = batteryExempt
        )
        diagnosticSnapshot(
            event = "recovery_synced",
            batteryExempt = batteryExempt,
            result = if (isConfigured && isPeer && batteryExempt) "scheduled" else "cancelled"
        )
    }

    fun diagnosticSnapshot(event: String) {
        diagnosticSnapshot(event = event, result = null)
    }

    fun showConsentDialog(
        activity: FragmentActivity,
        language: String,
        forceShow: Boolean,
        onChoice: (Boolean) -> Unit
    ) {
        if (!isConfigured) {
            onChoice(false)
            return
        }

        prepare(activity, language)
        manager.showDialog(
            activity,
            activity.supportFragmentManager,
            forceShow
        ) { granted ->
            if (!granted) {
                stop(activity)
            }
            onChoice(granted)
        }
    }

    private fun prepare(context: Context, language: String) {
        manager.setPartnerId(partnerId)
        manager.setApplicationId(applicationId)
        manager.setDialogTitle(context, localizedString(context, language, R.string.dunta_dialog_title))
        manager.setNotificationTitle(context, localizedString(context, language, R.string.dunta_notification_title))
        manager.setNotificationContent(context, localizedString(context, language, R.string.dunta_notification_content))
    }

    private fun diagnosticSnapshot(
        event: String,
        batteryExempt: Boolean? = null,
        result: String? = null
    ) {
        SupportModeDiagnostics.state(
            event = event,
            configured = isConfigured,
            peer = isPeer,
            batteryExempt = batteryExempt,
            result = result
        )
        SupportModeDiagnostics.networkSnapshot(
            event = "${event}_network",
            connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        )
    }

    private fun localizedString(context: Context, language: String, resourceId: Int): String {
        val tag = language.takeIf { it.isNotBlank() } ?: return context.getString(resourceId)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return context.createConfigurationContext(configuration).getString(resourceId)
    }
}
