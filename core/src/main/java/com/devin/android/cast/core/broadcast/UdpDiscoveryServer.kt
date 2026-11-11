package com.devin.android.cast.core.broadcast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

class UdpDiscoveryServer(
    private val wsPort: Int,
    private val port: Int = 8899,
) {
    private var socket: DatagramSocket? = null
    private var running = false
    private var lock: WifiManager.MulticastLock? = null

    fun start(context: Context) {
        logInfo()
        Thread {
            try {
                socket = DatagramSocket(port)
                logInfo("create DatagramSocket($port)")
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                lock = wifiManager.createMulticastLock("udp_discovery_lock")
                lock?.acquire()

                running = true
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)

                while (running) {
                    packet.length = buf.size
                    socket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    logDebug(
                        "receive packet:\n" +
                                "    ${packet.address}:${packet.port}\n" +
                                "    ${packet.data.copyOf(packet.length).joinToString(" ") { "%02x".format(it) }}\n" +
                                "    $msg\n"

                    )
                    if (msg.trim() == "DISCOVER_MIRROR") {
                        val respData = JSONObject().apply {
                            put("type", "found")
                            put("deviceName", Build.MODEL)
                            put("wsPort", wsPort)
                        }.toString()

                        val respPacket = DatagramPacket(
                            respData.toByteArray(Charsets.UTF_8),
                            respData.length,
                            packet.address,
                            packet.port
                        )
                        logDebug("send packet: $respData")
                        socket?.send(respPacket)
                    }
                }
            } catch (e: Exception) {
                logError("error= $e")
            } finally {
                stop()
            }
        }.start()
    }

    fun stop() {
        logInfo()
        running = false
        socket?.close()
        socket = null
        lock?.release()
    }
}