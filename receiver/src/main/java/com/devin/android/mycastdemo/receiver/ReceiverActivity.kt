package com.devin.android.mycastdemo.receiver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import com.devin.android.mycastdemo.receiver.FrameData.VideoConfigData
import com.devin.android.mycastdemo.receiver.FrameData.VideoFrameData
import timber.log.Timber

class ReceiverActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_URL = "ws://192.168.5.126:8080/stream"
    }

    private val tvStatus: TextView by lazy { findViewById(R.id.tvStatus) }
    private val tvDisplayInfo: TextView by lazy { findViewById(R.id.tvSizeInfo) }
    private val placeHolder: View by lazy { findViewById(R.id.surfaceViewPlaceHolder) }

    private var surfaceHolder: SurfaceHolder? = null
    private var okHttpWebSocket: OkHttpWebSocket? = null
    private var decoder: VideoDecoder? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        Timber.plant(Timber.DebugTree())

        val urlEditView = findViewById<EditText>(R.id.etUrl)
        urlEditView.setText(DEFAULT_URL)

        findViewById<View>(R.id.btnConnect).setOnClickListener {
            val url = urlEditView.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入 WebSocket 地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connect(url)
        }

        findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            disconnect()
        }

        findViewById<SurfaceView>(R.id.surfaceView).holder.addCallback(object : Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                logInfo("holder=0x${holder.hashCode().toHexString()}", clazzName = "SurfaceCallback")
                surfaceHolder = holder

            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // 如需根据分辨率重新配置解码器，可在这里处理
                logInfo("holder=0x${holder.hashCode().toHexString()}, format=$format, ${width}x${height}", clazzName = "SurfaceCallback")
                resetDisplaySizeInfo(width, height)
                if (decoder == null) {
                    okHttpWebSocket?.reconnect()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                logWarn("holder=0x${holder.hashCode().toHexString()}", clazzName = "SurfaceCallback")
                surfaceHolder = null
                decoder?.release()
                decoder = null
            }
        })

        resetDisplaySizeInfo()
    }

    private fun resetDisplaySizeInfo(
        surfaceWidth: Int = 0,
        surfaceHeight: Int = 0,
    ) {
        var displaySizeText = Utils.getDisplaySizeInfo(this)
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            displaySizeText = "$displaySizeText\n\n" +
                    "Surface size:\n" +
                    "    ${surfaceWidth}x${surfaceHeight}"
        }
        tvDisplayInfo.text = displaySizeText
    }

    private fun connect(url: String) {
        if (okHttpWebSocket != null) {
            logWarn("已经连接")
            return
        }
        updateStatus("连接中...")
        logInfo(url)

        OkHttpWebSocket(
            url,
            onConnected = {
                updateStatus("已连接，等待推流配置")
            },
            onVideoFormatReceived = { width, height, bitrate, csd0, csd1 ->
                mainHandler.post {
                    findViewById<SurfaceView>(R.id.surfaceView).also { surfaceView ->
                        val layoutParams = surfaceView.layoutParams
                        layoutParams.width = width
                        layoutParams.height = height
                        surfaceView.layoutParams = layoutParams
                    }
                }
                initDecoder(width, height, bitrate, csd0, csd1, surfaceHolder!!.surface)
            },
            onVideoConfigReceived = { data, time ->
                decoder?.feed(VideoConfigData(data, time))
            },
            onVideoFrameReceived = { data, time, isKeyFrame ->
                decoder?.feed(VideoFrameData(data, time, isKeyFrame))
            },
            onDisconnected = { reason ->
                updateStatus("已断开: $reason")
            })
            .also { streamReceiver ->
                streamReceiver.connect()
                okHttpWebSocket = streamReceiver
            }
    }

    private fun initDecoder(
        width: Int,
        height: Int,
        bitrate: Int,
        csd0: ByteArray,
        csd1: ByteArray,
        surface: Surface,
    ) {
        decoder?.release()
        decoder = VideoDecoder.create(surface, width, height, bitrate, csd0, csd1)
        decoder?.start()
        updateStatus("解码器已启动 ${width}x${height}")

        placeHolder.post {
            placeHolder
                .animate()
                .alpha(0f)
                .setDuration(1000L)
                .withEndAction { placeHolder.visibility = View.GONE }
                .start()
        }
    }

    private fun disconnect() {
        logInfo()
        okHttpWebSocket?.disconnect()
        okHttpWebSocket = null
        decoder?.release()
        decoder = null
        updateStatus("已断开")
        placeHolder.visibility = View.VISIBLE
    }

    private fun updateStatus(text: String) {
        mainHandler.post { tvStatus.text = text }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}