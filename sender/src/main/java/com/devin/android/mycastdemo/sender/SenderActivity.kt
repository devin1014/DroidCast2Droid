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
import com.devin.android.mycastdemo.sender.SenderWebSocketServer.SenderWebSocketServerCallback
import org.java_websocket.WebSocket
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class SenderActivity : AppCompatActivity(), SenderWebSocketServerCallback, Runnable {

    private val tvStatus: TextView by lazy { findViewById(R.id.tvStatus) }
    private var wsServer: SenderWebSocketServer? = null
    private val tvTimeView: TextView by lazy { findViewById(R.id.tvTime) }
    private val tvScreenInfo: TextView by lazy { findViewById(R.id.tvScreenInfo) }

    private val handler = Handler(Looper.getMainLooper())
    private val simpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (ScreenCaptureService.startForegroundService(this, result)) {
            bindService(Intent(this, ScreenCaptureService::class.java), serviceConnection, BIND_AUTO_CREATE)
            "开始投屏"
        } else {
            "投屏失败，请授权..."
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private var screenCaptureService: ScreenCaptureService? = null
    private var isBindService = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            logInfo()
            ((service as ScreenCaptureService.LocalBinder).getService()).also {
                it.webSocketServerCallback = this@SenderActivity
                screenCaptureService = it
            }
            isBindService = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logInfo()
            screenCaptureService?.webSocketServerCallback = null
            screenCaptureService = null
            isBindService = false
        }
    }

    override fun run() {
        tvTimeView.text = simpleDateFormat.format(Date())
        handler.postDelayed(this, 200L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)
        Timber.plant(Timber.DebugTree())

        findViewById<View>(R.id.btnStart).setOnClickListener {
            screenCaptureLauncher.launch(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .createScreenCaptureIntent()
            )
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            if (isBindService) {
                unbindService(serviceConnection)
                isBindService = false
            }
            stopService(Intent(this, ScreenCaptureService::class.java))
        }

        resetSizeInfo()

        handler.postDelayed(this, 200L)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetSizeInfo()
    }

    private fun resetSizeInfo() {
        tvScreenInfo.text = Utils.getDisplaySizeInfo(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(this)
        if (isBindService) {
            unbindService(serviceConnection)
        }
        stopWebsocketServer()
    }

    private fun stopWebsocketServer() {
        wsServer?.stopServer()
        wsServer = null
    }

    override fun onClientConnected(conn: WebSocket) {
        tvStatus.text = "已经连接：${conn.remoteSocketAddress}"
    }

    override fun onClientDisconnected(conn: WebSocket) {
        tvStatus.text = "已经断开：${conn.remoteSocketAddress}"
    }

    override fun onError(t: Throwable) {
        tvStatus.text = "错误：$t"
    }

    override fun onSendError(t: Throwable) {
    }
}