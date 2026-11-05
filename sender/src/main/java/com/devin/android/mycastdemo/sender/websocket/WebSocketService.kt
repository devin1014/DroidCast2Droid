package com.devin.android.mycastdemo.sender.websocket

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import com.devin.android.cast.core.NotificationHelper
import com.devin.android.cast.core.NotificationHelper.startForegroundWithNotification
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.service.BaseService
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.websocket.WebSocketServer.WebSocketServerCallback
import org.java_websocket.WebSocket

class WebSocketService : BaseService() {

    companion object {
        private const val CHANNEL_ID = "websocket_server_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private var wsServer: WebSocketServer? = null
    var webSocketServerCallback: WebSocketServerCallback? = null

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.ensureNotificationChannel(
            applicationContext,
            id = CHANNEL_ID,
            name = "Websocket服务",
            description = "运行中",
            importance = NotificationManager.IMPORTANCE_LOW
        )

        @SuppressLint("InlinedApi")
        startForegroundWithNotification(
            channelId = CHANNEL_ID,
            id = NOTIFICATION_ID,
            title = "Websocket服务",
            description = "运行中",
            icon = R.drawable.ic_server,
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        startWebsocketServer()
    }

    private fun startWebsocketServer() {
        logInfo()
        if (wsServer == null) {
            WebSocketServer(
                port = 8080,
                callback = object : WebSocketServerCallback {
                    override fun onClientConnected(conn: WebSocket) {
                        webSocketServerCallback?.onClientConnected(conn)
                    }

                    override fun onClientDisconnected(conn: WebSocket) {
                        webSocketServerCallback?.onClientDisconnected(conn)
                    }

                    override fun onError(t: Throwable) {
                        webSocketServerCallback?.onError(t)
                    }
                }
            ).also { server ->
                server.start()
                wsServer = server
            }
        }
    }

    override fun onDestroy() {
        stopWebsocketServer()
        super.onDestroy()
    }

    private fun stopWebsocketServer() {
        logInfo()
        wsServer?.stopServer()
    }

    fun sendData(data: ByteArray) {
        wsServer?.sendBytes(data)
    }

}