package com.devin.android.cast.core

import android.app.Activity
import android.app.Application
import android.os.Bundle

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivityDestroyed(activity: Activity) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivityPaused(activity: Activity) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivityResumed(activity: Activity) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivityStarted(activity: Activity) {
                logInfo("", clazzName = activity::class.simpleName)
            }

            override fun onActivityStopped(activity: Activity) {
                logInfo("", clazzName = activity::class.simpleName)
            }
        })
    }
}