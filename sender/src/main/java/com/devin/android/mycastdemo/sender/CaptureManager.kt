package com.devin.android.mycastdemo.sender

import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodec.Callback
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.createBitmap
import com.devin.android.cast.core.FrameProtocol
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class CaptureManager private constructor(
    private val mediaProjection: MediaProjection,
) {
    companion object {
        private const val MIME_TYPE_VIDEO = "video/avc"
        private const val KEY_CSD_0 = "csd-0"
        private const val KEY_CSD_1 = "csd-1"
        private const val VIRTUAL_DISPLAY_NAME = "ScreenCapture"

        fun create(
            service: ScreenCaptureService,
            resultCode: Int,
            resultData: Intent,
        ): CaptureManager? {
            return try {
                val projection = (service.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .getMediaProjection(resultCode, resultData)
                projection?.let { CaptureManager(it) }
            } catch (e: Exception) {
                Timber.e("getMediaProjection failed: $e")
                return null
            }
        }
    }

    interface OnCodecOutputCallback {
        fun onOutputBufferUpdate(data: ByteArray)
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    var callback: OnCodecOutputCallback? = null
    private var curOutputFormatData: ByteArray? = null
    private var lastOutputKeyFrameData: ByteArray? = null

    private val projectionCallback = object : MediaProjection.Callback() {

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            logInfo("isVisible=$isVisible", clazzName = "MediaProjection.Callback")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            logInfo("width=$width, height=$height", clazzName = "MediaProjection.Callback")
        }

        override fun onStop() {
            logInfo("", clazzName = "MediaProjection.Callback")
        }
    }

    private val codecCallback = object : Callback() {
        override fun onError(codec: MediaCodec, e: CodecException) {
            logInfo("error=$e", clazzName = "MediaCodec-Callback")
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            logInfo("index=$index", clazzName = "MediaCodec-Callback")
        }

        private var lastBufferStampMs: Long = 0L

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: BufferInfo) {
            logDebug("info flag=${info.flags}, size=${info.size}, offset=${info.offset}, timeUs=${info.presentationTimeUs}", clazzName = "MediaCodec-Callback")
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                val configBuffer = codec.getOutputBuffer(index)
                if (configBuffer != null) {
                    val data = ByteArray(info.size)
                    configBuffer.get(data)
                    val videoBytes = FrameProtocol.packConfig(data, info.presentationTimeUs)
                    callback?.onOutputBufferUpdate(videoBytes)
                }
            } else if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                val configBuffer = codec.getOutputBuffer(index)
                if (configBuffer != null) {
                    val data = ByteArray(info.size)
                    configBuffer.get(data)
                    val videoBytes = FrameProtocol.packVideo(data, info.presentationTimeUs, true)
                    callback?.onOutputBufferUpdate(videoBytes.also {
                        lastOutputKeyFrameData = it
                    })
                }
            } else if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                //TODO
            } else {
                if (info.presentationTimeUs - lastBufferStampMs > 500) {
                    lastBufferStampMs = info.presentationTimeUs
                }

                if (info.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(index)
                    val data = ByteArray(info.size)
                    outputBuffer?.position(info.offset)
                    outputBuffer?.get(data)
                    val videoBytes = FrameProtocol.packVideo(data, info.presentationTimeUs, false)
                    callback?.onOutputBufferUpdate(videoBytes)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            logInfo("format=$format", clazzName = "MediaCodec-Callback")

            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                format.getInteger(MediaFormat.KEY_BIT_RATE)
            } else {
                0
            }

            // 先算 csd 数据大小
            val csd0 = format.getByteBuffer(KEY_CSD_0)
            val csd1 = format.getByteBuffer(KEY_CSD_1)
            val csd0Bytes = csd0?.let {
                it.rewind()
                ByteArray(it.remaining()).also { bytes -> it.get(bytes) }
            } ?: byteArrayOf()
            val csd1Bytes = csd1?.let {
                it.rewind()
                ByteArray(it.remaining()).also { bytes -> it.get(bytes) }
            } ?: byteArrayOf()

            callback?.onOutputBufferUpdate(
                FrameProtocol.packFormat(width, height, bitrate, csd0Bytes, csd1Bytes).also {
                    curOutputFormatData = it
                }
            )
        }
    }

    init {
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
    }

    fun start(
        context: Context,
        config: CaptureConfig,
    ) {
        val screenWidth = config.width
        val screenHeight = config.height
        val dpi = config.dpi
        val targetWidth = config.targetWidth
        val targetHeight = ((screenHeight * targetWidth) / screenWidth) / 16 * 16

        logInfo(
            "$config" +
                    "\n    视频比例缩放: $screenWidth*$screenHeight => $targetWidth*$targetHeight"
        )

        with(MediaCodec.createEncoderByType(MIME_TYPE_VIDEO)) {
            setCallback(codecCallback)

            configure(
                MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, targetWidth, targetHeight).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameInterval)
                },
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            val inputSurface = if (config.useImageReader) {
                val imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2
                )
                setupImageReader(imageReader, context)
                imageReader.surface
            } else {
                this.createInputSurface()
            }
            start()
            encoder = this

            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                targetWidth,
                targetHeight,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                inputSurface,
                null,
                null
            )
        }
    }

    fun stop() {
        encoder?.stop()
        encoder?.release()
        encoder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection.unregisterCallback(projectionCallback)
        mediaProjection.stop()
    }

    fun notifyOutputFormat() {
        curOutputFormatData?.also { bytes ->
            callback?.onOutputBufferUpdate(bytes)
        }
        lastOutputKeyFrameData?.also { bytes ->
            callback?.onOutputBufferUpdate(bytes)
        }
    }

    private fun setupImageReader(reader: ImageReader, context: Context) {
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val width = image.width
                val height = image.height
                val planes = image.planes
                if (planes.isEmpty()) {
                    logError("planes is empty")
                    return@setOnImageAvailableListener
                }
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                logInfo("image size=${width}x${height}, pixelStride=$pixelStride, rowStride=$rowStride")
                val bitmap = if (pixelStride == 4 && rowStride == width * 4) {
                    buffer.rewind()
                    createBitmap(width, height).apply {
                        copyPixelsFromBuffer(buffer)
                    }
                } else {
                    val paddedWidth = rowStride / 4
                    val paddedBitmap = createBitmap(paddedWidth, height)
                    buffer.rewind()
                    paddedBitmap.copyPixelsFromBuffer(buffer)

                    Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                        paddedBitmap.recycle()
                    }
                }
                val dir = context.getExternalFilesDir(null)
                    ?: throw IllegalStateException("external files dir is null")
                val file = File(dir, "capture_frame.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                logInfo("saved to ${file.absolutePath}")
                bitmap.recycle()
            } catch (e: Exception) {
                logError("save frame failed: $e")
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))
    }
}