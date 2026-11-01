package com.statusvideo.statussaver.core.dunta

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.statusvideo.statussaver.R
import com.statusvideo.statussaver.StatusSaverApp

@Composable
fun SupportModeBatterySettings() {
    val context = LocalContext.current
    val controller = (context.applicationContext as StatusSaverApp).duntaSdkController
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.isEnabled) return
    Column {
        Text(
            stringResource(
                if (state.batteryOptimizationExempt) R.string.settings_dunta_battery_unrestricted
                else R.string.settings_dunta_battery_restricted
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { (context as? DuntaBatterySettingsRequester)?.openDuntaBatterySettings() }) {
            Text(stringResource(R.string.settings_dunta_battery_title))
        }
    }
}
