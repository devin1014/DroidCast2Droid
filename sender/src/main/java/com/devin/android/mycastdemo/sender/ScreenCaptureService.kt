package com.devin.android.mycastdemo.sender

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.IBinder
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.devin.android.cast.core.CaptureConfig
import com.devin.android.cast.core.logInfo
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.NotificationHelper.startForegroundWithNotification
import com.devin.android.mycastdemo.sender.NotificationHelper.stopForegroundNotification

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001

        fun startForegroundService(context: Context, result: ActivityResult): Boolean {
            if (result.resultCode != RESULT_OK || result.data == null) return false
            val intent = Intent(context, ScreenCaptureService::class.java)
                .apply {
                    action = ACTION_START
                    putExtra(EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(EXTRA_RESULT_DATA, result.data)
                }
            ContextCompat.startForegroundService(context, intent)
            return true
        }

        private fun parseIntentData(intent: Intent): Pair<Int, Intent> {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val resultData = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultData == null) return Pair(resultCode, Intent())
            return Pair(resultCode, resultData)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    private val binder = LocalBinder()

    var captureManager: CaptureManager? = null
        private set

    override fun onCreate() {
        super.onCreate()
        logInfo()

        NotificationHelper.ensureNotificationChannel(
            applicationContext,
            id = CHANNEL_ID,
            name = "屏幕采集服务",
            description = "正在共享屏幕",
            importance = NotificationManager.IMPORTANCE_LOW
        )

        @SuppressLint("InlinedApi")
        startForegroundWithNotification(
            channelId = CHANNEL_ID,
            id = NOTIFICATION_ID,
            title = "屏幕采集中",
            description = "正在共享屏幕画面",
            icon = R.drawable.ic_notification,
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logInfo("flags=$flags, startId=$startId")

        when (intent?.action) {
            ACTION_START -> {
                val (resultCode, resultData) = parseIntentData(intent)
                startCapture(resultCode, resultData)
            }

            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logInfo()
        stopCapture()
        stopForegroundNotification(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun startCapture(
        resultCode: Int,
        resultData: Intent,
    ) {
        // media projection has started.
        if (captureManager != null) return
        val manager = CaptureManager.create(this, resultCode, resultData)
        if (manager == null) {
            stopSelf()
            return
        }
        captureManager = manager

        with(applicationContext.resources.displayMetrics) {
            manager.start(
                CaptureConfig(widthPixels, 720, heightPixels, densityDpi)
            )
        }
    }

    private fun stopCapture() {
        logInfo()
        captureManager?.stop()
    }

    fun notifyNewClientConnected() {
        captureManager?.notifyOutputFormat()
    }

}