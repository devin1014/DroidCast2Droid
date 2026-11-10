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
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.devin.android.cast.core.FrameProtocol
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
    private val callback: OnCodecOutputCallback,
) {
    companion object {
        private const val AUDIO_SAMPLE_RATE = 160_000
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BIT_RATE = 64_000
        private const val KEY_CSD_0 = "csd-0"
    }

    private var mediaCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null

    private var audioCaptureThread: Thread? = null
    private var encodeThread: Thread? = null
    private var curOutputFormatData: ByteArray? = null

    private val pcmQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>(16)
    private val freeInputBuffers = LinkedBlockingQueue<Int>(8)

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
        val record = createAudioRecord(mediaProjection, sampleRate)

        record.format.also { format ->
            logInfo("AudioRecord format: sampleRate=${format.sampleRate}, channelCount=${format.channelCount}")
        }

        audioRecord = record

        val codec = createMediacodec(object : Callback() {
            override fun onError(codec: MediaCodec, e: CodecException) {
                logError("$e", "MediaCodec-Callback-A")
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
//                logDebug("index=$index", "MediaCodec-Callback-A")
                freeInputBuffers.put(index)
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: BufferInfo) {
                logDebug("info flag=${info.flags}, size=${info.size}, offset=${info.offset}, timeUs=${info.presentationTimeUs}", clazzName = "MediaCodec-Callback-A")
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val aac = ByteArray(info.size)
                    buffer.get(aac)
//                    logDebug("aac out size=${aac.size} first16=${aac.take(16).joinToString(" ") { "%02x".format(it) }}", "MediaCodec-Callback-A")
                    //TODO: check config|key_frame flag
                    callback.onOutputBufferUpdate(FrameProtocol.packAudioFrame(aac, info.presentationTimeUs, false))
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                logInfo("$format", "MediaCodec-Callback-A")
                val bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                val csd0 = format.getByteBuffer(KEY_CSD_0)
                val csd0Bytes = csd0?.let {
                    it.rewind()
                    ByteArray(it.remaining()).also { bytes -> it.get(bytes) }
                } ?: byteArrayOf()
                logInfo("csd0= ${csd0Bytes.joinToString("") { "%02x".format(it) }}", "MediaCodec-Callback-A")
                callback.onOutputBufferUpdate(FrameProtocol.packAudioFormat(bitrate, csd0Bytes).also {
                    curOutputFormatData = it
                })
            }
        })

        mediaCodec = codec

        // 1. 采集线程：只负责读 PCM
        audioCaptureThread?.start()
        AudioCaptureThread(
            record,
            pcmQueue
        ).apply {
            start()
            audioCaptureThread = this
        }

        // 2. 编码线程：同时等 buffer index 和 PCM
        encodeThread?.interrupt()
        EncodeThread(
            sampleRate,
            codec,
            pcmQueue,
            freeInputBuffers
        ).apply {
            start()
            encodeThread = this
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(
        mediaProjection: MediaProjection,
        sampleRate: Int,
    ): AudioRecord {
        return with(AudioRecord.Builder()) {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            setAudioFormat(format)
            setBufferSizeInBytes(
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                logInfo("setAudioPlaybackCaptureConfig: mediaProjection")
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                setAudioPlaybackCaptureConfig(config)
            } else {
                logInfo("setAudioSource: MIC")
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            logInfo(
                "encodeing=${AudioFormat.ENCODING_PCM_16BIT}, " +
                        "sampleRate=$sampleRate, " +
                        "channelMask=${AudioFormat.CHANNEL_IN_MONO}"
            )
            build()
        }
    }

    fun createMediacodec(callback: Callback): MediaCodec {
        // create media codec
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
        }
        logInfo(
            "mime=${MediaFormat.MIMETYPE_AUDIO_AAC}," +
                    " sampleRate=$sampleRate, " +
                    "channelCount=$channelCount, " +
                    "bitRate=$bitRate, " +
                    "aacProfile=${MediaCodecInfo.CodecProfileLevel.AACObjectLC}"
        )
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
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
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            logError("$e")
        }
        mediaCodec = null
    }

    fun notifyOutputFormat() {
        curOutputFormatData?.also { bytes ->
            callback.onOutputBufferUpdate(bytes)
        }
        //TODO: check audio need this?
        mediaCodec?.setParameters(
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
        )
    }
}