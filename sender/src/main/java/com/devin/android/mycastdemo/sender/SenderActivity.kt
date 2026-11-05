package com.devin.android.mycastdemo.sender

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.bindServiceCompat
import com.devin.android.cast.core.service.ServiceBinderConnection
import com.devin.android.cast.core.startForegroundServiceCompat
import com.devin.android.cast.core.stopServiceCompat
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.CaptureManager.OnCodecOutputCallback
import com.devin.android.mycastdemo.sender.websocket.WebSocketServer.WebSocketServerCallback
import com.devin.android.mycastdemo.sender.websocket.WebSocketService
import org.java_websocket.WebSocket
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class SenderActivity : AppCompatActivity(), WebSocketServerCallback, Runnable, OnCodecOutputCallback {

    private var colorIndex = 0
    private val displayColors: IntArray by lazy { Utils.getColors(this, R.array.display_colors) }
    private val displayColorView: View by lazy { findViewById(R.id.displayColor) }
    private val tvStatus: TextView by lazy { findViewById(R.id.tvStatus) }
    private val tvTimeView: TextView by lazy { findViewById(R.id.tvTime) }
    private val tvScreenInfo: TextView by lazy { findViewById(R.id.tvScreenInfo) }

    private val handler = Handler(Looper.getMainLooper())
    private val simpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (ScreenCaptureService.startForegroundService(this, result)) {
            bindServiceCompat<ScreenCaptureService>(
                this,
                screenCaptureConnection
            )
            "开始投屏"
        } else {
            "投屏失败，请授权..."
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private var screenCaptureService: ScreenCaptureService? = null
    private var websocketService: WebSocketService? = null

    private val screenCaptureConnection = object : ServiceBinderConnection<ScreenCaptureService>() {
        override fun onServiceConnected(service: ScreenCaptureService) {
            service.captureManager?.callback = this@SenderActivity
            screenCaptureService = service
        }

        override fun onServiceDisconnected(service: ScreenCaptureService) {
            screenCaptureService?.captureManager?.callback = null
            screenCaptureService = null
        }
    }

    private val websocketConnection = object : ServiceBinderConnection<WebSocketService>() {
        override fun onServiceConnected(service: WebSocketService) {
            service.webSocketServerCallback = this@SenderActivity
            websocketService = service
        }

        override fun onServiceDisconnected(service: WebSocketService) {
            websocketService?.webSocketServerCallback = null
            websocketService = null
        }
    }

    override fun run() {
        tvTimeView.text = simpleDateFormat.format(Date())
        handler.postDelayed(this, 100L)

        colorIndex++
        val index = (colorIndex / 10) % displayColors.size
        displayColorView.setBackgroundColor(displayColors[index])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        startForegroundServiceCompat<WebSocketService>(this)
        bindServiceCompat<WebSocketService>(this, websocketConnection)

        findViewById<View>(R.id.btnStart).setOnClickListener {
            screenCaptureLauncher.launch(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
            )
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            screenCaptureConnection.unbindService(this)
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
        resetSizeInfo()
        handler.post(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetSizeInfo()
    }

    private fun resetSizeInfo() {
        tvScreenInfo.text = Utils.getDisplaySizeInfo(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(this)
        // capture service
        screenCaptureConnection.unbindService(this)

        // websocket service
        websocketConnection.unbindService(this)
        stopServiceCompat<WebSocketService>(this)

        super.onDestroy()
    }

    override fun onClientConnected(conn: WebSocket) {
        tvStatus.text = "已经连接：${conn.remoteSocketAddress}"
        screenCaptureService?.notifyNewClientConnected()
    }

    override fun onClientDisconnected(conn: WebSocket) {
        tvStatus.text = "已经断开：${conn.remoteSocketAddress}"
    }

    override fun onError(t: Throwable) {
        tvStatus.text = "错误：$t"
    }

    override fun onOutputBufferUpdate(data: ByteArray) {
        websocketService?.sendData(data)
    }
}