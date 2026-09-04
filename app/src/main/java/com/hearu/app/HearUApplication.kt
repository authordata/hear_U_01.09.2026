package com.hearu.app

import android.app.Application
import com.hearu.app.service.HearUFirebaseMessagingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HearUApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HearUFirebaseMessagingService.createNotificationChannels(this)
    }
}
