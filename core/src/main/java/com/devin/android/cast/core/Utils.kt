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
import androidx.core.content.edit

object Utils {
    fun getScreenSize(context: Context): Triple<Int, Int, Int> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: throw IllegalStateException("Default display not found")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(dm)
        return Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
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
            "DisplayMetrics: ${this.widthPixels}x${this.heightPixels}" +
                    "\n" +
                    "WindowMetrics: ${windowWidth}x${windowHeight}"
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

    fun putUrl2SharePreference(context: Context, url: String) {
        context.getSharedPreferences("serverUrl", Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString("url", url)
            }
    }

    fun getSharePreferenceUrl(context: Context): String {
        return context.getSharedPreferences("serverUrl", Context.MODE_PRIVATE)
            .getString("url", Constants.DEFAULT_SERVER) ?: Constants.DEFAULT_SERVER
    }
}