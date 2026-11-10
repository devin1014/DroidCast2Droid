package com.devin.android.mycastdemo.receiver

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import java.lang.Thread.currentThread
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class AudioDecoder private constructor(private val sampleRate: Int) {

    companion object {
        private const val KEY_CSD_0 = "csd-0"
        fun create(
            bitrate: Int,
            csd0: ByteArray,
            sampleRate: Int,
            channelCount: Int = 1,
        ): AudioDecoder {
            val codec = createCodec(bitrate, csd0, sampleRate, channelCount)
            val track = createAudioTrack(sampleRate, channelCount)
            return AudioDecoder(sampleRate).apply {
                audioDecoder = codec
                audioTrack = track
            }
        }

        private fun createCodec(
            bitrate: Int,
            csd0: ByteArray,
            sampleRate: Int,
            channelCount: Int = 1,
        ): MediaCodec {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            }
            logInfo(
                "create decoder:\n" +
                        "    bitrate=$bitrate, sampleRate=$sampleRate, channelCount=$channelCount\n" +
                        "    csd0=${csd0.joinToString() { "%02x".format(it) }}\n" +
                        "    $format",
                "${AudioDecoder::class.simpleName}"
            )
            return MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                .apply {
                    logInfo("configure: $format", "${AudioDecoder::class.simpleName}")
                    configure(format, null, null, 0)
                }
        }

        private fun createAudioTrack(
            sampleRate: Int,
            channelCount: Int,
        ): AudioTrack {
            val channelMask = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )

            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also {
                    logInfo("")
                }
        }
    }

    init {
        logInfo()
    }

    private var audioDecoder: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var inputThread: Thread? = null

    private val freeInputBuffers = LinkedBlockingQueue<Int>(8)
    private val inputQueue = ConcurrentLinkedQueue<FrameData>()

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
//            logDebug("index=$index", clazzName = "${AudioDecoder::class.simpleName}")
            freeInputBuffers.put(index)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo,
        ) {
            logDebug("index=$index, size=${info.size}, flag=${info.flags}", clazzName = "${AudioDecoder::class.simpleName}")
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                logInfo("audio config flag continue")
                codec.releaseOutputBuffer(index, false)
                return
            }
            val out = codec.getOutputBuffer(index) ?: return
            if (info.size > 0) {
                val bytes = ByteArray(info.size)
                out.get(bytes)
//                logDebug("first bytes=${bytes.take(16).joinToString()}", clazzName = "${AudioDecoder::class.simpleName}")
                val writeSize = audioTrack?.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
//                logDebug("write: $writeSize", clazzName = "${AudioDecoder::class.simpleName}")
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            logError("error: $e", clazzName = "${AudioDecoder::class.simpleName}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            logInfo("$format", clazzName = "${AudioDecoder::class.simpleName}")
            val csd0 = format.getByteBuffer(KEY_CSD_0)
            val csd0Bytes = csd0?.let {
                it.rewind()
                ByteArray(it.remaining()).also { bytes -> it.get(bytes) }
            } ?: byteArrayOf()
            logInfo("csd0= ${csd0Bytes.joinToString("") { "%02x".format(it) }}", "${AudioDecoder::class.simpleName}")
        }
    }

    fun feed(data: FrameData) {
        inputQueue.offer(data)
    }

    fun start() {
        val decoder = audioDecoder ?: return
        decoder.setCallback(callback)
        decoder.start()
        val track = audioTrack ?: return
        track.play()

        logInfo()

        inputThread?.interrupt()
        inputThread = thread(
            start = true,
            name = "AudioInputThread"
        ) {
            logInfo("[${currentThread().name}]start...", "${currentThread().name}")
            var pts = 0L
            val bytesPerSample = 2 // 16bit mono

            val result = runCatching {
                while (!currentThread().isInterrupted) {
                    val index = freeInputBuffers.take() // 阻塞等有可用 input buffer
                    val item = inputQueue.poll() ?: run {
                        freeInputBuffers.put(index) // 没有 PCM，把 index 还回去
                        continue
                    }
                    val buffer = decoder.getInputBuffer(index) ?: continue
                    val size: Int = minOf(item.data.size, buffer.remaining())
                    if (size < item.data.size) {
                        logWarn("not queue all data!!!")
                    }
                    buffer.put(item.data, 0, size)
                    decoder.queueInputBuffer(index, 0, size, pts, 0)
                    pts += size.toLong() * 1_000_000L / (sampleRate * bytesPerSample)
//                    logDebug("audio queueInputBuffer, size=$size first16=${item.data.take(16).joinToString("") { "%02x".format(it) }}", "${currentThread().name}")
                }
                logInfo("[${currentThread().name}]done...", "${currentThread().name}")
            }
            if (result.isFailure) {
                logError("error= ${result.exceptionOrNull()}")
            }
        }
    }

    fun release() {
        logInfo()
        audioDecoder?.stop()
        audioDecoder?.release()
        audioDecoder = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        inputQueue.clear()
    }
}