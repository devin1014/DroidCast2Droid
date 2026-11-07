package com.devin.android.mycastdemo.sender.audio

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodec.Callback
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.core.content.ContextCompat
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import com.devin.android.mycastdemo.sender.video.OnCodecOutputCallback
import java.util.concurrent.LinkedBlockingQueue

class RecordAudioManager(
    private val sampleRate: Int = AUDIO_SAMPLE_RATE,
    private val channelCount: Int = AUDIO_CHANNEL_COUNT,
    private val bitRate: Int = AUDIO_BIT_RATE,
) {
    companion object {
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BIT_RATE = 64_000
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC

        private const val TYPE_VIDEO = 1
        private const val TYPE_AUDIO = 2
    }

    private var mediaCodec: MediaCodec? = null

    private var audioCaptureThread: Thread? = null
    private var encodeThread: Thread? = null

    private val pcmQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>(16)
    private val freeInputBuffers = LinkedBlockingQueue<Int>(8)
    var callback: OnCodecOutputCallback? = null

    init {
        logInfo("${this::class.simpleName}")
    }

    fun start(
        context: Context,
        mediaProjection: MediaProjection,
    ) {
        logInfo()
        if (ContextCompat.checkSelfPermission(context, permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            logError("missing 'RECORD_AUDIO' permission")
            return
        }

        if (mediaCodec != null) return

        // create audio record
        val audioRecord = createAudioRecord(mediaProjection)

        val codec = createMediacodec(object : Callback() {
            override fun onError(codec: MediaCodec, e: CodecException) {
                logError("$e", "MediaCodec-Callback-A")
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                logDebug("index=$index", "MediaCodec-Callback-A")
                freeInputBuffers.put(index)
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: BufferInfo) {
                logDebug("info flag=${info.flags}, size=${info.size}, offset=${info.offset}, timeUs=${info.presentationTimeUs}", clazzName = "MediaCodec-Callback-A")
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val aac = ByteArray(info.size)
                    buffer.get(aac)
                    //TODO
//                    callback?.onOutputBufferUpdate(aac)
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                logInfo("$format", "MediaCodec-Callback-A")
//                callback?.onOutputBufferUpdate(aac)
            }
        })

        mediaCodec = codec

        // 1. 采集线程：只负责读 PCM
        audioCaptureThread?.start()
        AudioCaptureThread(
            audioRecord,
            pcmQueue
        ).apply {
            start()
            audioCaptureThread = this
        }

        // 2. 编码线程：同时等 buffer index 和 PCM
        encodeThread?.interrupt()
        EncodeThread(
            codec,
            pcmQueue,
            freeInputBuffers
        ).apply {
            start()
            encodeThread = this
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(mediaProjection: MediaProjection): AudioRecord {
        return with(AudioRecord.Builder()) {
            setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            setBufferSizeInBytes(
                AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build()
                setAudioPlaybackCaptureConfig(config)
            } else {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            build()
        }
    }

    fun createMediacodec(callback: Callback): MediaCodec {
        // create media codec
        val format = MediaFormat.createAudioFormat(AUDIO_MIME, sampleRate, channelCount)
            .apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
            }
        return MediaCodec.createEncoderByType(AUDIO_MIME)
            .apply {
                setCallback(callback)
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
    }

    fun stop() {
        logWarn()
        try {
            audioCaptureThread?.interrupt()
            encodeThread?.interrupt()
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            logError("$e")
        }
        mediaCodec = null
    }
}