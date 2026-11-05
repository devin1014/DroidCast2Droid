package com.devin.android.mycastdemo.sender

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.logInfo
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.CaptureManager.OnCodecOutputCallback
import com.devin.android.mycastdemo.sender.websocket.MyWebSocketServer.SenderWebSocketServerCallback
import com.devin.android.mycastdemo.sender.websocket.MyWebSocketService
import org.java_websocket.WebSocket
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class SenderActivity : AppCompatActivity(), SenderWebSocketServerCallback, Runnable, OnCodecOutputCallback {

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
            bindService(Intent(this, ScreenCaptureService::class.java), screenCaptureConnection, BIND_AUTO_CREATE)
            "开始投屏"
        } else {
            "投屏失败，请授权..."
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private var screenCaptureService: ScreenCaptureService? = null
    private var isBindCaptureService = false
    private var websocketService: MyWebSocketService? = null
    private var isBindWebsocketService = false

    private val screenCaptureConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            logInfo()
            ((service as ScreenCaptureService.LocalBinder).getService()).also {
                it.captureManager?.callback = this@SenderActivity
                screenCaptureService = it
            }
            isBindCaptureService = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logInfo()
            screenCaptureService?.captureManager?.callback = null
            screenCaptureService = null
            isBindCaptureService = false
        }
    }

    private val websocketConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            logInfo()
            ((service as MyWebSocketService.LocalBinder).getService()).also {
                it.webSocketServerCallback = this@SenderActivity
                websocketService = it
            }
            isBindWebsocketService = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logInfo()
            websocketService?.webSocketServerCallback = null
            websocketService = null
            isBindWebsocketService = false
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
        Timber.plant(Timber.DebugTree())

        MyWebSocketService.startForegroundService(this)
        MyWebSocketService.bindService(this, websocketConnection)

        findViewById<View>(R.id.btnStart).setOnClickListener {
            screenCaptureLauncher.launch(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .createScreenCaptureIntent()
            )
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            if (isBindCaptureService) {
                unbindService(screenCaptureConnection)
                isBindCaptureService = false
            }
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
        if (isBindCaptureService) {
            unbindService(screenCaptureConnection)
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
        // websocket service
        if (isBindWebsocketService) {
            unbindService(websocketConnection)
            stopService(Intent(this, MyWebSocketService::class.java))
        }
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