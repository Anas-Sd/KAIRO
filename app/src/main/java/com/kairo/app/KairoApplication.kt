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
        com.kairo.app.data.sync.SyncManager.initialize(this)
    }
}
