package com.kairo.app

import android.app.Application

class KairoApplication : Application() {

    companion object {
        lateinit var instance: KairoApplication
            private set

        val isInitialized: Boolean
            get() = ::instance.isInitialized

        val instanceOrNull: KairoApplication?
            get() = if (::instance.isInitialized) instance else null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (com.kairo.app.feature.tasks.alarm.DirectBootAlarmStore.isUserUnlocked(this)) {
            try {
                com.kairo.app.data.sync.SyncManager.initialize(this)
                com.kairo.app.feature.sync.FcmTokenManager.syncTokenIfLoggedIn()
            } catch (e: Throwable) {
                android.util.Log.e("KairoApplication", "Error initializing services: ${e.message}", e)
            }
        } else {
            android.util.Log.i("KairoApplication", "App starting in Direct Boot mode (device locked). Deferring sync/token init until unlock.")
        }
    }
}
