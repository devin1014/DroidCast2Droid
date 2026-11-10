package com.devin.android.mycastdemo.sender.video

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.DisplayMetrics
import android.view.Display
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.devin.android.cast.core.NotificationHelper
import com.devin.android.cast.core.NotificationHelper.startForegroundWithNotification
import com.devin.android.cast.core.NotificationHelper.stopForegroundNotification
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.service.BaseService
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.CaptureConfig
import com.devin.android.mycastdemo.sender.audio.RecordAudioManager

class ScreenCaptureService : BaseService(), OnCodecOutputCallback {

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

    private var captureManager: CaptureManager? = null
    private var recordAudioManager: RecordAudioManager? = null

    @Volatile
    var callback: OnCodecOutputCallback? = null
        set(value) {
            field = value
            updateOutputCallback(value)
        }

    private var isRecording: Boolean = false
    private val displayManager by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }

    private val displayListener = object : DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            if (!isRecording) return

            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            logInfo("${screenWidth}x${screenHeight}")

            captureManager?.updateScreenSize(screenWidth, screenHeight)
        }

        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

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
        displayManager.registerDisplayListener(displayListener, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

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
        displayManager.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    private fun startCapture(
        resultCode: Int,
        resultData: Intent,
    ) {
        // media projection has started.
        if (captureManager != null) return
        val manager = CaptureManager.create(this, this, resultCode, resultData)
        if (manager == null) {
            stopSelf()
            return
        }
        captureManager = manager

        val (width, height, densityDpi) = Utils.getScreenSize(applicationContext)
        manager.start(
            applicationContext,
            CaptureConfig(width, height, densityDpi)
        )

        RecordAudioManager(callback = this).also { audioManager ->
            audioManager.start(
                applicationContext,
                manager.mediaProjection
            )
            recordAudioManager = audioManager
        }

        isRecording = true
    }

    private fun stopCapture() {
        isRecording = false
        logInfo()
        captureManager?.stop()
        recordAudioManager?.stop()
    }

    fun notifyNewClientConnected() {
        logInfo()
        captureManager?.notifyOutputFormat()
        recordAudioManager?.notifyOutputFormat()
    }

    private val queue = ArrayDeque<ByteArray>()

    override fun onOutputBufferUpdate(data: ByteArray) {
        val cb = callback
        if (cb == null) {
            // callback 为空，数据入队
            synchronized(queue) {
                queue.add(data)
                logInfo("add queue")
                // 可选：防止内存溢出，设置上限丢弃最旧数据
                if (queue.size > 128) {
                    queue.removeFirst()
                    logInfo("queue size is bigger than 128")
                }
            }
            return
        }

        if (queue.isNotEmpty()) {
            logInfo("queue is not empty. size=[${queue.size}]")
            // callback 有值，先 flush queue，再发当前帧
            val toSend = synchronized(queue) {
                val list = ArrayList<ByteArray>(queue)
                queue.clear()
                list
            }

            for (frame in toSend) {
                cb.onOutputBufferUpdate(frame)
            }
        }

        cb.onOutputBufferUpdate(data)
    }

    private fun updateOutputCallback(callback: OnCodecOutputCallback?) {
        // 如果新 callback 不为空，设置前先 flush 一次 queue
        if (callback != null) {
            val toSend = synchronized(queue) {
                val list = ArrayList<ByteArray>(queue)
                queue.clear()
                list
            }
            logInfo("reset callback, queue is [${toSend.size}]")
            for (frame in toSend) {
                callback.onOutputBufferUpdate(frame)
            }
        }
    }
}