package com.devin.android.cast.core

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import timber.log.Timber

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        logInfo("pid=${android.os.Process.myPid()}, process=${getAppProcessName()}")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                logInfo("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivityDestroyed(activity: Activity) {
                logWarn("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivityPaused(activity: Activity) {
                logInfo("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivityResumed(activity: Activity) {
                logInfo("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                logInfo("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivityStarted(activity: Activity) {
                logInfo("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }

            override fun onActivityStopped(activity: Activity) {
                logWarn("0x${activity.hashCode().toHexString()}", clazzName = activity::class.simpleName)
            }
        })
    }

    private fun getAppProcessName(): String {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (info in manager.runningAppProcesses) {
            if (info.pid == android.os.Process.myPid()) {
                return info.processName
            }
        }
        return "unknown"
    }
}