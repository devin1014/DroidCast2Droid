package com.devin.android.mycastdemo.receiver

import com.devin.android.cast.core.logInfo
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class DataReceiver(
    private val url: String,
) {
    private val client: OkHttpClient = Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val readBuffer = ArrayDeque<Byte>()

    fun connect() {
        webSocket = client.newWebSocket(
            request = Request.Builder().url(url).build(),
            listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 连接成功，等待发送端推流
                    logInfo()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    logInfo("bytes=${text}")
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    logInfo("ByteString=${bytes.string(Charset.defaultCharset())}")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    logInfo("$code=$code, reason=$reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logInfo("error=$t")
                }
            })
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
        readBuffer.clear()
    }
}
