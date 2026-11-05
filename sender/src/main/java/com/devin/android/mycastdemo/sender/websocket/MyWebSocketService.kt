package com.devin.android.mycastdemo.sender.websocket

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.service.BaseService
import com.devin.android.mycastdemo.R
import com.devin.android.mycastdemo.sender.NotificationHelper
import com.devin.android.mycastdemo.sender.NotificationHelper.startForegroundWithNotification
import com.devin.android.mycastdemo.sender.websocket.MyWebSocketServer.SenderWebSocketServerCallback
import org.java_websocket.WebSocket

class MyWebSocketService : BaseService() {

    companion object {
        private const val CHANNEL_ID = "websocket_server_channel"
        private const val NOTIFICATION_ID = 2001

        fun startForegroundService(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MyWebSocketService::class.java)
            )
        }

        fun bindService(context: Context, conn: ServiceConnection) {
            context.bindService(
                Intent(context, MyWebSocketService::class.java),
                conn,
                BIND_AUTO_CREATE
            )
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MyWebSocketService = this@MyWebSocketService
    }

    private val binder = LocalBinder()
    private var wsServer: MyWebSocketServer? = null
    var webSocketServerCallback: SenderWebSocketServerCallback? = null

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

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun startWebsocketServer() {
        logInfo()
        if (wsServer == null) {
            MyWebSocketServer(
                port = 8080,
                callback = object : SenderWebSocketServerCallback {
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