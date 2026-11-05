package com.devin.android.mycastdemo.sender.websocket

import com.devin.android.cast.core.FrameProtocol
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class WebSocketServer(
    port: Int,
    private val callback: WebSocketServerCallback,
) : org.java_websocket.server.WebSocketServer(InetSocketAddress(port)) {

    interface WebSocketServerCallback {
        fun onClientConnected(conn: WebSocket) {}
        fun onClientDisconnected(conn: WebSocket) {}
        fun onError(t: Throwable) {}
        fun onSendError(t: Throwable) {}
    }

    init {
        logInfo("init SenderWebSocketServer")
        isReuseAddr = true
    }

    var client: WebSocket? = null
        private set

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        if (client != null) {
            logInfo("已有客户端连接，拒绝新连接")
            conn.close()
            return
        }
        client = conn
        logInfo("客户端已连接: ${conn.remoteSocketAddress}")
        callback.onClientConnected(conn)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        logWarn("客户端断开: code=$code reason=$reason remote=$remote")
        if (client == conn) {
            client = null
            callback.onClientDisconnected(conn)
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // 发送端目前只推流，不处理文本消息
        logInfo(message)
        if (client == conn && message == FrameProtocol.COMMAND_GET_VIDEO_FORMAT) {
            callback.onClientConnected(conn)
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        // 目前不需要接收端回传二进制数据
        logDebug("message=${message}")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logError("ex=$ex")
        callback.onError(ex)
    }

    override fun onStart() {
        logInfo("WebSocket 服务已启动: $address")
    }

    fun sendBytes(data: ByteArray) {
        val conn = client ?: return
        if (!conn.isOpen) return
        try {
            logDebug("...")
            conn.send(data)
        } catch (e: Exception) {
            logInfo("error=$e")
            callback.onSendError(e)
        }
    }

    fun stopServer() {
        logInfo()
        disconnectAll()
        try {
            stop()
        } catch (_: Exception) {
        }
    }

    private fun disconnectAll() {
        try {
            val conn = client
            if (conn != null && conn.isOpen) {
                conn.close()
            }
        } catch (_: Exception) {
        }
        client = null
    }
}