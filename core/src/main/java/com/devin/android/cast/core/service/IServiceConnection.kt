package com.devin.android.cast.core.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

abstract class IServiceConnection<T : BaseService> : ServiceConnection {

    private var service: T? = null

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = ((binder as BaseService.LocalBinder).getService()) as? T
        onServiceConnected(service)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        onServiceDisconnected(service)
    }

    abstract fun onServiceConnected(service: T?)

    abstract fun onServiceDisconnected(service: T?)
}