package com.devin.android.cast.core.broadcast

import android.content.Context
import android.net.wifi.WifiManager
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn
import com.devin.android.cast.core.network.NetworkUtils.getBroadcastAddress
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class UdpDiscoveryClient(
    private val port: Int = 8899,
    private val timeoutMs: Long = 3000,
) {
    data class DiscoveredDevice(
        val ip: String,
        val wsPort: Int,
        val deviceName: String,
    )

    private var thread: Thread? = null
    private var isRunning = false

    fun discoverByUdp(
        context: Context,
        onDeviceFounded: (List<DiscoveredDevice>) -> Unit,
    ) {
        if (isRunning) {
            logWarn("is searching...")
            return
        }
        logInfo()
        isRunning = true
        thread?.interrupt()
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val lock = wifiManager.createMulticastLock("udp_discover_lock")
        lock.acquire()

        thread = thread(
            start = true,
            name = "discoverUdp",
            isDaemon = true
        ) {
            logInfo(Thread.currentThread().name + " run")

            val devices = mutableSetOf<DiscoveredDevice>()

            val socket = DatagramSocket(InetSocketAddress(0)).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeoutMs.toInt()
            }

            try {
                val msg = "DISCOVER_MIRROR"
                val data = msg.toByteArray(Charsets.UTF_8)

                // 受限广播
                val limitedBroadcast = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(data, data.size, limitedBroadcast, port)
                logDebug(
                    "send packet:\n" +
                            "    ${packet.address}:${packet.port}\n" +
                            "    ${data.joinToString(" ")}\n" +
                            "    $msg"
                )
                socket.send(packet)

                // 2. 子网定向广播（兜底）
                val subnetBroadcast = getBroadcastAddress(context)
                if (subnetBroadcast != null && subnetBroadcast != limitedBroadcast) {
                    val packet = DatagramPacket(data, data.size, subnetBroadcast, port)
                    logDebug(
                        "send packet:\n" +
                                "    ${packet.address}:${packet.port}\n" +
                                "    ${data.joinToString(" ")}\n" +
                                "    $msg"
                    )
                    socket.send(packet)
                }

                val buf = ByteArray(1024)
                val responsePacket = DatagramPacket(buf, buf.size)

                while (!Thread.currentThread().isInterrupted) {
                    try {
                        socket.receive(responsePacket)
                        val json = JSONObject(
                            String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                        )
                        logDebug(
                            "receive packet:\n" +
                                    "    ${responsePacket.address}" +
                                    "    ${responsePacket.data.copyOf(responsePacket.length).joinToString(" ") { "%02x".format(it) }}\n" +
                                    "    $json"
                        )
                        if (json.optString("type") == "found") {
                            devices.add(
                                DiscoveredDevice(
                                    ip = responsePacket.address.hostAddress ?: continue,
                                    wsPort = json.getInt("wsPort"),
                                    deviceName = json.optString("deviceName", "Unknown")
                                )
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        logError("error= $e")
                        break
                    }
                }
            } finally {
                onDeviceFounded(devices.toList())
                socket.close()
                lock.release()
                logInfo(Thread.currentThread().name + " done...")
                isRunning = false
            }
        }
    }
}