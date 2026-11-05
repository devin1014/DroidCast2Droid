package com.devin.android.cast.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn

abstract class BaseService : Service() {

    override fun onCreate() {
        super.onCreate()
        logInfo()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logInfo("flags=$flags, startId=$startId")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logWarn("")
        super.onDestroy()
    }
}