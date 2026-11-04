package com.devin.android.java_client

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

internal class MyWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {
    /**
     * 连接成功打开时触发
     */
    override fun onOpen(handshakedata: ServerHandshake?) {
        println("Connected successfully!")
        // System.out.println("HTTP Headers received: " + handshakedata.getHttpHeaders());
    }

    /**
     * 接收到服务端消息时触发
     */
    override fun onMessage(message: String?) {
        println("Received Message: $message")
    }

    /**
     * 接收到二进制消息时触发 (可选重写)
     */
    override fun onMessage(bytes: ByteBuffer) {
        println("Received Binary Message of length: ${bytes.array().size}")
    }

    /**
     * 连接关闭时触发
     */
    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println(
            "Connection closed by " + (if (remote) "server" else "us") +
                    ". Code: " + code + ", Reason: " + reason
        )
    }

    /**
     * 发生错误时触发
     */
    override fun onError(ex: Exception) {
        System.err.println("Error occurred: " + ex.message)
        ex.printStackTrace()
    }
}
