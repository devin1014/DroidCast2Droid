package com.devin.android.mycastdemo.receiver

import com.devin.android.cast.core.FrameProtocol
import com.devin.android.cast.core.FrameProtocol.TYPE_FORMAT
import com.devin.android.cast.core.FrameProtocol.TYPE_VIDEO_CONFIG
import com.devin.android.cast.core.FrameProtocol.TYPE_VIDEO_FRAME
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class OkHttpWebSocket(
    private val url: String,
    private val onConnected: () -> Unit,
    private val onVideoFormatReceived: (width: Int, height: Int, bitrate: Int, csd0: ByteArray, csd1: ByteArray) -> Unit,
    private val onVideoConfigReceived: (data: ByteArray, presentationTimeUs: Long) -> Unit,
    private val onVideoFrameReceived: (data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean) -> Unit,
    private val onDisconnected: (reason: String) -> Unit,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val readBuffer = ArrayDeque<Byte>()

    fun reconnect() {
        val socket = webSocket ?: return
        socket.send(FrameProtocol.COMMAND_GET_VIDEO_FORMAT)
    }

    fun connect() {
        webSocket = client.newWebSocket(
            request = Request.Builder().url(url).build(),
            listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 连接成功，等待发送端推流
                    logInfo(
                        "code=${response.code} msg=${response.message} \nheader=${response.headers}",
                        clazzName = "WebSocketListener"
                    )
                    onConnected()
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    parse(bytes)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    logWarn("$code=$code, reason=$reason", clazzName = "WebSocketListener")
                    onDisconnected(reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logError("error=$t", clazzName = "WebSocketListener")
                    onDisconnected(t.message ?: "unknown")
                }
            })
    }

    private fun parse(bytes: ByteString) {
        // 1. 转换为 ByteBuffer 提高解析效率，无需手动搬运 byte[]
        val byteBuffer = bytes.asByteBuffer()

        // 2. 检查基础报头长度 (1字节Type + 4字节PayloadLen = 5字节)
        if (byteBuffer.remaining() < 5) {
            logWarn("数据包过短，无法解析包头", clazzName = "WebSocketListener")
            return
        }

        // 3. 读取包头
        val type = byteBuffer.get() // 读取1字节类型
        val payloadLen = byteBuffer.getInt() // 读取4字节载荷长度

        // 4. 验证剩余字节是否和声明的载荷长度相符
        if (byteBuffer.remaining() < payloadLen) {
            logWarn("数据不完整，期望载荷: " + payloadLen + ", 实际剩余: " + byteBuffer.remaining(), clazzName = "WebSocketListener")
            return
        }

        // 5. 根据类型进行分流解析
        when (type) {
            TYPE_FORMAT -> {
                parseFormatPacket(byteBuffer)
            }

            TYPE_VIDEO_CONFIG -> {
                parseVideoFramePacket(true, byteBuffer)
            }

            TYPE_VIDEO_FRAME -> {
                parseVideoFramePacket(false, byteBuffer)
            }

            else -> {}
        }
    }

    /**
     * 解析格式配置包 (TYPE_FORMAT = 0x01)
     */
    private fun parseFormatPacket(buffer: ByteBuffer) {
        val width = buffer.getInt()
        val height = buffer.getInt()
        val bitrate = buffer.getInt()
        val csd0Len = buffer.getInt()
        val csd1Len = buffer.getInt()

        val csd0 = ByteArray(csd0Len)
        if (csd0Len > 0) {
            buffer.get(csd0)
        }

        val csd1 = ByteArray(csd1Len)
        if (csd1Len > 0) {
            buffer.get(csd1)
        }
        logInfo("width=$width, height=$height, bitrate=$bitrate, csd0=[$csd0Len], csd1=[$csd1Len]", clazzName = "WebSocketListener")
        onVideoFormatReceived(width, height, bitrate, csd0, csd1)
    }

    /**
     * 解析视频数据包 (TYPE_VIDEO_FRAME = 0x02)
     */
    private fun parseVideoFramePacket(isConfig: Boolean, buffer: ByteBuffer) {
        val presentationTimeUs = buffer.getLong()   // 提取 8 字节时间戳
        val frameType = buffer.getInt()             // 提取 4 字节帧类型
        val isKeyFrame = frameType == FrameProtocol.FRAME_TYPE_KEY

        val frameSize = buffer.remaining()
        val frameData = ByteArray(frameSize)
        buffer.get(frameData)
        if (isConfig) {
            logInfo("video config")
            onVideoConfigReceived(frameData, presentationTimeUs)
        } else {
            if (isKeyFrame) {
                logInfo("video key frame: $frameSize")
            } else {
                logDebug("video frame: $frameSize")
            }
            onVideoFrameReceived(frameData, presentationTimeUs, isKeyFrame)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
        readBuffer.clear()
    }
}
