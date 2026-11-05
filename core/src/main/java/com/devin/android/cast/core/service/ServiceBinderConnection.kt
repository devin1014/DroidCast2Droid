package com.devin.android.cast.core.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.devin.android.cast.core.logInfo
import com.devin.android.cast.core.logWarn

abstract class ServiceBinderConnection<T : BaseService> : ServiceConnection {

    private var service: T? = null
    private var isBindService = false

    final override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val binderService = ((binder as BaseService.LocalBinder).getService()) as T
        logInfo("${binderService::class.simpleName}")
        service = binderService
        onServiceConnected(binderService)
        isBindService = true
    }

    final override fun onServiceDisconnected(name: ComponentName) {
        service?.also {
            logWarn("${it::class.simpleName}")
            onServiceDisconnected(it)
        }
        isBindService = false
    }

    abstract fun onServiceConnected(service: T)

    abstract fun onServiceDisconnected(service: T)

    fun unbindService(context: Context) {
        if (isBindService) {
            context.unbindService(this)
        }
        isBindService = false
    }
}