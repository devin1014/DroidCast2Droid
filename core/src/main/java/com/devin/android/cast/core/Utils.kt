package com.devin.android.cast.core

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.hardware.display.DisplayManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowMetrics
import java.net.Inet4Address
import java.net.NetworkInterface

object Utils {
    fun getScreenSize(context: Context): Pair<Int, Int> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: throw IllegalStateException("Default display not found")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    fun getActivitySize(activity: Activity): Pair<Int, Int> {
        return if (VERSION.SDK_INT >= VERSION_CODES.R) {
            // Android 11 (API 30) 及以上推荐方法
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            // Android 10 及以下兼容旧写法
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    fun getDisplaySizeInfo(activity: Activity): String {
        val (windowWidth, windowHeight) = getActivitySize(activity)
        return with(activity.resources.displayMetrics) {
            "DisplayMetrics size:\n" +
                    "    ${this.widthPixels}x${this.heightPixels}\n\n" +
                    "CurrentWindowMetrics size:\n" +
                    "    ${windowWidth}x${windowHeight}"
        }
    }

    fun getColors(context: Context, array: Int): IntArray {
        var typedArray: TypedArray? = null
        return try {
            typedArray = context.resources.obtainTypedArray(array)
            IntArray(typedArray.length()) { i ->
                typedArray.getColor(i, 0)
            }
        } finally {
            typedArray?.recycle()
        }
    }

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
                        "[${networkInterface.index}]${networkInterface.displayName ?: networkInterface.name} " +
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
}