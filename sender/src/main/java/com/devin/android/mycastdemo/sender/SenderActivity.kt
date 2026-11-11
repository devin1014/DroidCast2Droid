package com.devin.android.mycastdemo.sender

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.devin.android.cast.core.Constants.URL_HOLDER
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.bindServiceCompat
import com.devin.android.cast.core.broadcast.LocalNsdManager
import com.devin.android.cast.core.broadcast.UdpDiscoveryServer
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.network.NetworkUtils
import com.devin.android.cast.core.service.ServiceBinderConnection
import com.devin.android.cast.core.startForegroundServiceCompat
import com.devin.android.cast.core.stopServiceCompat
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.video.OnCodecOutputCallback
import com.devin.android.mycastdemo.sender.video.ScreenCaptureService
import com.devin.android.mycastdemo.sender.websocket.WebSocketServer.WebSocketServerCallback
import com.devin.android.mycastdemo.sender.websocket.WebSocketService
import org.java_websocket.WebSocket
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
    private var mediaPlayer: MediaPlayer? = null
    private var udpDiscoveryServer = UdpDiscoveryServer(8080)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (ScreenCaptureService.startForegroundService(this, result)) {
            bindServiceCompat<ScreenCaptureService>(
                this,
                screenCaptureConnection
            )
            "开始录屏"
        } else {
            "录屏失败，请授权..."
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "需要授权录音权限", Toast.LENGTH_SHORT).show()
        }
    }

    private var screenCaptureService: ScreenCaptureService? = null
    private var websocketService: WebSocketService? = null

    private val screenCaptureConnection = object : ServiceBinderConnection<ScreenCaptureService>() {
        override fun onServiceConnected(service: ScreenCaptureService) {
            service.callback = this@SenderActivity
            screenCaptureService = service
        }

        override fun onServiceDisconnected(service: ScreenCaptureService) {
            screenCaptureService?.callback = null
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

    private val nsdListener = object : RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            logInfo()
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            logInfo()
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            logError("errorCode=$errorCode")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            logError("errorCode=$errorCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)


        findViewById<TextView>(R.id.tvAddress).apply {
            val serverUrl = URL_HOLDER.replace("@ip", NetworkUtils.getLocalIpAddress() ?: "unknown")
            text = serverUrl
        }
        findViewById<View>(R.id.btnStart).setOnClickListener {
            screenCaptureLauncher.launch(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
            )
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            screenCaptureConnection.unbindService(this)
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
        resetSizeInfo()
        startForegroundServiceCompat<WebSocketService>(this)
        bindServiceCompat<WebSocketService>(this, websocketConnection)
        handler.post(this)
        startMusic()
    }

    private fun startMusic() {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.water).apply {
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        LocalNsdManager.registerService(this, nsdListener)
        udpDiscoveryServer.start(this)
    }

    override fun onStop() {
        LocalNsdManager.unregisterService(this, nsdListener)
        udpDiscoveryServer.stop()
        super.onStop()
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

        // release media
        mediaPlayer?.release()
        mediaPlayer = null

        super.onDestroy()
    }

    override fun run() {
        // update current time
        tvTimeView.text = simpleDateFormat.format(Date())

        // update circle color
        colorIndex++
        val index = (colorIndex / 10) % displayColors.size
        (displayColorView.background as GradientDrawable).setColor(displayColors[index])

        // update
        handler.postDelayed(this, 100L)
    }

    override fun onClientConnected(conn: WebSocket) {
        tvStatus.text = "connected：${conn.remoteSocketAddress}"
        screenCaptureService?.notifyNewClientConnected()
    }

    override fun onClientDisconnected(conn: WebSocket) {
        tvStatus.text = "disconnect：${conn.remoteSocketAddress}"
    }

    override fun onError(t: Throwable) {
        tvStatus.text = "error：$t"
    }

    override fun onOutputBufferUpdate(data: ByteArray) {
        websocketService?.sendData(data)
    }
}