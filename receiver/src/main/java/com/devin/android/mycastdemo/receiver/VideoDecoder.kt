package com.devin.android.mycastdemo.receiver

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.view.Surface
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import com.devin.android.mycastdemo.receiver.FrameData.VideoConfigData
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class VideoDecoder private constructor() : Thread() {
    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_US = 10_000L

        fun create(
            surface: Surface,
            width: Int,
            height: Int,
            bitrate: Int,
            csd0: ByteArray,
            csd1: ByteArray,
        ): VideoDecoder? {
            return try {
                val codec = MediaCodec.createDecoderByType(MIME_TYPE)
                logInfo("MediaCodec: ${codec.name}")
                // 1. 创建格式
                val format = MediaFormat().apply {
                    setString(MediaFormat.KEY_MIME, MIME_TYPE)
                    setInteger(MediaFormat.KEY_WIDTH, width)
                    setInteger(MediaFormat.KEY_HEIGHT, height)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    if (csd0.isNotEmpty()) {
                        val csd0Buffer = ByteBuffer.allocateDirect(csd0.size).apply {
                            put(csd0)
                            flip() // 切换为读模式
                        }
                        setByteBuffer("csd-0", csd0Buffer)
                    }
                    if (csd1.isNotEmpty()) {
                        val csd1Buffer = ByteBuffer.allocateDirect(csd1.size).apply {
                            put(csd1)
                            flip() // 切换为读模式
                        }
                        setByteBuffer("csd-1", csd1Buffer)
                    }
                }
                // 2. 配置解码器
                codec.configure(format, surface, null, 0)
                VideoDecoder().apply {
                    this.mediaCodec = codec
                    codec.start()
                    logInfo("MediaCodec start success!")
                }
            } catch (e: Exception) {
                logInfo("createDecoderByType: $MIME_TYPE, failed $e")
                null
            }
        }
    }

    init {
        isDaemon = true
        name = "VideoDecoder"
    }

    private var mediaCodec: MediaCodec? = null
    private val inputQueue = ConcurrentLinkedQueue<FrameData>()

    fun feed(data: FrameData) {
        inputQueue.offer(data)
    }

    fun release() {
        inputQueue.clear()
        val codec = mediaCodec ?: return
        logWarn()
        try {
            codec.stop()
            codec.release()
            interrupted()
            mediaCodec = null
        } catch (_: Exception) {
        }
    }

    override fun run() {
        decodeLoop()
        logWarn("${this::class.simpleName} done...")
    }

    private fun decodeLoop() {
        // 局部变量锁住当前 codec 实例，防止中途被变动
        val codec = mediaCodec ?: return
        val info = BufferInfo()

        while (!isInterrupted) {
            // 双重检查锁定
            if (codec != mediaCodec) break

            var transformDataSuccess = false

            try {
                // 1. 【输入端喂料】：尝试从队列中取出一个视频帧
                val packet = inputQueue.poll()
                if (packet != null) {
                    if (packet.data.size > 4) {
                        // 打印每帧数据的前 5 个字节的十六进制
                        val hexString = packet.data.take(5).joinToString(" ") { String.format("%02X", it) }
                        logDebug("▶ input ==> size: ${packet.data.size}, 前5字节: $hexString, 是否关键帧: ${packet.isKeyFrame}", clazzName = "DecodeLoop")
                    }

                    // 如果你坚持要在循环中处理配置帧，请保留这段；如果已经在外层 configure 时传入了 CSD，可直接删掉此 if
                    if (packet is VideoConfigData) {
//                        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
//                        if (inputIndex >= 0) {
//                            val inputBuffer = codec.getInputBuffer(inputIndex)
//                            inputBuffer?.let {
//                                it.clear()
//                                it.put(packet.data, 0, packet.data.size)
//                                codec.queueInputBuffer(
//                                    inputIndex, 0, packet.data.size,
//                                    packet.timestampUs, MediaCodec.BUFFER_FLAG_CODEC_CONFIG
//                                )
//                            }
//                        }
                        // 配置帧处理完直接进入下一轮，但注意：这会跳过下面的输出检查
                        continue
                    }

                    // 2. 处理普通视频帧（包含关键帧和I/P/B帧）
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        inputBuffer?.let {
                            it.clear()
                            it.put(packet.data, 0, packet.data.size)

                            val flags = if (packet.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            codec.queueInputBuffer(inputIndex, 0, packet.data.size, info.presentationTimeUs, flags)
                            transformDataSuccess = true
                        }
                    } else {
                        // 解码器输入缓冲区满了，把 packet 放回队列头部，稍后重试
                        // 或者使用带有超时的阻塞，甚至这里不 poll 改用 peek
                        // 为了简单起见，这里可以考虑将 packet 重新 offer 进队列或在这里等待
                    }
                }
            } catch (e: IllegalStateException) {
                logInfo("Input dequeue IllegalStateException: ${e::class.simpleName}, ${e.message}")
                isInterrupted
                break
            } catch (e: Exception) {
                logInfo("Input loop error: $e")
                isInterrupted
                break
            }

            // 3. 【输出端渲染】：不论刚才有没有成功喂入新数据，都必须不断尝试榨干解码器的输出
            // 因为解码器吐出画面的节奏是独立的
            try {
                // 循环读取输出，直到解码器提示“暂无新画面” (INFO_TRY_AGAIN_LATER)
                while (true) {
                    when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // 解码器目前没有可以渲染的帧，退出输出小循环
                            break
                        }

                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // 收到真正的视频宽高发生改变的回调
                            val newFormat = codec.outputFormat
                            logWarn("Decoder output format changed: $newFormat")
                        }

                        else -> {
                            if (outputIndex >= 0) {
                                // 打印 BufferInfo 的元数据
                                logDebug("★ output ==> size: ${info.size}, pts: ${info.presentationTimeUs}, flags: ${info.flags}", clazzName = "DecodeLoop")
                                // true 表示直接让 MediaCodec 把这一帧的数据渲染到你绑定的 Surface/TextureView 上
                                codec.releaseOutputBuffer(outputIndex, true)

                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    interrupted()
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                logInfo("Output dequeue IllegalStateException: ${e::class.simpleName}, ${e.message}")
                isInterrupted
                break
            }

            // 4. 【防止CPU空转】：如果输入队列空了，且解码器也暂时没有输出，主动释放 CPU
            if (inputQueue.isEmpty() && !transformDataSuccess) {
                try {
                    sleep(10) // 10ms 既能保证不空转，又能换来约 100fps 的响应速度
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }
}

sealed class FrameData(val data: ByteArray, val timestampUs: Long, val isKeyFrame: Boolean) {
    class VideoConfigData(data: ByteArray, timestampUs: Long) : FrameData(data, timestampUs, false)
    class VideoFrameData(data: ByteArray, timestampUs: Long, isKeyFrame: Boolean) : FrameData(data, timestampUs, isKeyFrame)
}