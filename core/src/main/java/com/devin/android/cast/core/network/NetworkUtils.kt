package com.devin.android.cast.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            var ipAddress: String? = ""
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // 跳过未启用或回环接口
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    logInfo(
                        "[${networkInterface.index}]  ${networkInterface.displayName ?: networkInterface.name} " +
                                "${address.hostAddress}"
                    )
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        // 过滤掉 169.254.x.x 链路本地地址
                        if (!address.isLinkLocalAddress) {
                            // 这里可以直接返回，不用遍历全部，这里是为了打印所有ip地址。
                            ipAddress = address.hostAddress
                        }
                    }
                }
            }
            return ipAddress
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getBroadcastAddress(context: Context): InetAddress? {
        // 1. 优先从 NetworkInterface 直接拿广播地址
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.interfaceAddresses.isEmpty()) continue

                for (ia in ni.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address) {
                        ia.broadcast?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            logError("error: $e")
        }

        // 2. 从 LinkProperties 推导广播地址
        try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = cm.activeNetwork ?: return null
            val lp = cm.getLinkProperties(network) ?: return null

            for (linkAddr in lp.linkAddresses) {
                val addr = linkAddr.address
                if (addr is java.net.Inet4Address) {
                    val prefix = linkAddr.prefixLength
                    if (prefix in 1..31) {
                        val ipInt = addrToInt(addr)
                        val hostBits = 32 - prefix
                        val mask = (0xFFFFFFFF.toInt() shl hostBits)
                        val networkInt = ipInt and mask
                        val broadcastInt = networkInt or (0xFFFFFFFF.toInt() ushr (32 - hostBits))
                        return intToInetAddress(broadcastInt)
                    }
                }
            }
        } catch (e: Exception) {
            logError("error: $e")
        }

        // 3. 兜底：DhcpInfo
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.netmask != 0 && dhcp.ipAddress != 0) {
                val ipInt = dhcp.ipAddress
                val maskInt = dhcp.netmask
                val broadcastInt = ipInt or (maskInt.inv())
                return intToInetAddress(broadcastInt)
            }
        } catch (e: Exception) {
            logError("error: $e")
        }

        return null
    }

    private fun addrToInt(addr: java.net.Inet4Address): Int {
        val bytes = addr.address
        return (bytes[0].toInt() and 0xFF shl 24) or
                (bytes[1].toInt() and 0xFF shl 16) or
                (bytes[2].toInt() and 0xFF shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun intToInetAddress(value: Int): InetAddress {
        return InetAddress.getByAddress(
            byteArrayOf(
                (value shr 24 and 0xFF).toByte(),
                (value shr 16 and 0xFF).toByte(),
                (value shr 8 and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        )
    }
}