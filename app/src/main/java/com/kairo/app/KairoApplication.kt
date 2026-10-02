package com.kairo.app

import android.app.Application

class KairoApplication : Application() {

    companion object {
        lateinit var instance: KairoApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.kairo.app.data.sync.SyncManager.initialize(this)
        } catch (e: Throwable) {
            android.util.Log.e("KairoApplication", "Error initializing SyncManager: ${e.message}", e)
        }
    }
}
