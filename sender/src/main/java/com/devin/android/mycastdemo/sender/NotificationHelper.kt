package com.devin.android.mycastdemo.sender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    fun ensureNotificationChannel(
        context: Context,
        id: String,
        name: String,
        description: String,
        importance: Int = NotificationManager.IMPORTANCE_LOW,
    ) {
        // Android 8.0 not need notification channel
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Channel
        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
        }
        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(channel)
        }
    }

    fun Service.startForegroundWithNotification(
        id: Int,
        channelId: String,
        title: String,
        description: String,
        icon: Int,
        serviceType: Int,
    ) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(icon)
            .setOngoing(true)
            .build()

        // Android 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, serviceType)
        } else {
            startForeground(id, notification)
        }
    }

    fun Service.stopForegroundNotification(id: Int) {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}