package com.devin.android.cast.core.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn

abstract class BaseService : Service() {

    private val binder: Binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BaseService = this@BaseService
    }

    override fun onCreate() {
        super.onCreate()
        logInfo()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logInfo("flags=$flags, startId=$startId")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        logWarn("")
        super.onDestroy()
    }
}