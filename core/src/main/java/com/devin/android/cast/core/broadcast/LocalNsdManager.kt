package com.devin.android.cast.core.broadcast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn

object LocalNsdManager {
    // ------------------------------------
    // server
    // ------------------------------------
    fun registerService(context: Context, listener: RegistrationListener) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "MyCast-${Build.MODEL}"
            serviceType = "_mycast._tcp"
            port = 8080
            setAttribute("deviceName", Build.MODEL)
            setAttribute("protocol", "websocket")
            setAttribute("codec", "h264")
        }
        (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
            .registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
    }

    fun unregisterService(context: Context, listener: RegistrationListener) {
        (context.getSystemService(Context.NSD_SERVICE) as NsdManager)
            .unregisterService(listener)
    }

    // ------------------------------------
    // client
    // ------------------------------------
    fun searchService(context: Context, onServiceFound: (NsdServiceInfo) -> Unit) {
        with(context.getSystemService(Context.NSD_SERVICE) as NsdManager) {
            discoverServices(
                "_mycast._tcp.",
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        logInfo("serviceType=$serviceType")
                    }

                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        logError("serviceType=$serviceType, errorCode=$errorCode")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        logInfo("$serviceInfo")
                        // 发现一个候选设备，继续解析
                        resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                // 解析失败，可能是同名冲突或网络问题
                                logError("errorCode=$errorCode")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                logInfo("$serviceInfo")
                                onServiceFound(serviceInfo)
                            }
                        })
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        // 设备离线，从列表移除
                        logWarn()
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        logInfo()
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        logWarn()
                    }
                }
            )
        }
    }
}