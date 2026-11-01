package com.statusvideo.statussaver

import android.app.Application
import com.statusvideo.statussaver.core.dunta.DuntaSdkController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StatusSaverApp : Application() {
    @Inject lateinit var duntaSdkController: DuntaSdkController

    override fun onCreate() {
        super.onCreate()
        duntaSdkController.prepare()
    }
}
