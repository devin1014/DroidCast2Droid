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
import androidx.appcompat.app.AppCompatActivity
import com.devin.android.cast.core.Constants
import com.devin.android.cast.core.Constants.URL_HOLDER
import com.devin.android.cast.core.Utils
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import com.devin.android.mycastdemo.receiver.FrameData.VideoConfigData
import com.devin.android.mycastdemo.receiver.FrameData.VideoFrameData

class ReceiverActivity : AppCompatActivity() {

    private val tvStatus: TextView by lazy { findViewById(R.id.tvStatus) }
    private val tvDisplayInfo: TextView by lazy { findViewById(R.id.tvSizeInfo) }
    private val placeHolder: View by lazy { findViewById(R.id.surfaceViewPlaceHolder) }
    private val surfaceView: SurfaceView by lazy { findViewById(R.id.surfaceView) }

    private var surfaceHolder: SurfaceHolder? = null
    private var okHttpWebSocket: OkHttpWebSocket? = null
    private var decoder: VideoDecoder? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        val urlEditView = findViewById<EditText>(R.id.etUrl)
        urlEditView.setText(Utils.getSharePreferenceUrl(this))

        findViewById<View>(R.id.btnUrl).setOnClickListener {
            UrlPickerDialog(this, Constants.serverUrls) { url ->
                val serverUrl = URL_HOLDER.replace("@ip", url)
                Utils.putUrl2SharePreference(this, serverUrl)
                urlEditView.setText(serverUrl)
            }.show()
        }

        findViewById<View>(R.id.btnConnect).setOnClickListener {
            connect(urlEditView.text.toString().trim())
        }

        findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            disconnect()
        }

        surfaceView.holder.addCallback(object : Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                logInfo("holder=0x${holder.hashCode().toHexString()}", clazzName = "SurfaceCallback")
                surfaceHolder = holder

            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // 如需根据分辨率重新配置解码器，可在这里处理
                logInfo("holder=0x${holder.hashCode().toHexString()}, format=$format, ${width}x${height}", clazzName = "SurfaceCallback")
                resetDisplaySizeInfo()
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

    private var formatWidth: Int = 0
    private var formatHeight: Int = 0

    private fun resetDisplaySizeInfo() {
        // screen info
        var displaySizeText = Utils.getDisplaySizeInfo(this)

        // surface info
        val surfaceWidth = surfaceView.width
        val surfaceHeight = surfaceView.height
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            displaySizeText = "$displaySizeText\n" +
                    "Surface: ${surfaceWidth}x${surfaceHeight}"
        }

        // format info
        if (formatWidth > 0 && formatHeight > 0) {
            displaySizeText = "$displaySizeText\n" +
                    "format: ${formatWidth}x${formatHeight}"
        }

        tvDisplayInfo.post {
            tvDisplayInfo.text = displaySizeText
        }
    }

    private fun resetSurfaceViewSize(width: Int, height: Int) {
        val viewWidth = surfaceView.width
        val viewHeight = surfaceView.height

        if (viewWidth <= 0 || viewHeight <= 0 || width <= 0 || height <= 0) return

        // 原始视频比例
        val videoRatio = width.toFloat() / height.toFloat()
        // 容器比例
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()

        val targetWidth: Int
        val targetHeight: Int

        if (videoRatio > viewRatio) {
            // 视频更宽，以宽度为准，高度缩放
            targetWidth = viewWidth
            targetHeight = (viewWidth / videoRatio).toInt()
        } else {
            // 视频更高，以高度为准，宽度缩放
            targetHeight = viewHeight
            targetWidth = (viewHeight * videoRatio).toInt()
        }

        surfaceView.post {
            val layoutParams = surfaceView.layoutParams
            layoutParams.width = targetWidth
            layoutParams.height = targetHeight
            surfaceView.layoutParams = layoutParams
        }

        placeHolder.post {
            val layoutParams = placeHolder.layoutParams
            layoutParams.width = targetWidth
            layoutParams.height = targetHeight
            placeHolder.layoutParams = layoutParams
        }
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
                formatWidth = width
                formatHeight = height
                resetDisplaySizeInfo()
                // surface
                resetSurfaceViewSize(width, height)
                // decoder
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

        placeHolder.fadeOut()
    }

    private fun disconnect() {
        logInfo()
        okHttpWebSocket?.disconnect()
        okHttpWebSocket = null
        decoder?.release()
        decoder = null
        updateStatus("已断开")

        placeHolder.fadeIn()
    }

    private fun updateStatus(text: String) {
        mainHandler.post { tvStatus.text = text }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    private fun View.fadeOut(duration: Long = 500L) {
        post {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }
    }

    private fun View.fadeIn(duration: Long = 500L) {
        post {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(duration)
                .withEndAction {
                    visibility = View.VISIBLE
                }
                .start()
        }
    }
}