package com.devin.android.cast.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import timber.log.Timber
import java.util.logging.Level

inline fun logDebug(
    message: String = "",
    clazzName: String? = null,
) {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = trace.methodName
    val text = "$methodName${message.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    log(className, text, Level.CONFIG)
}

inline fun logInfo(
    message: String = "",
    clazzName: String? = null,
) {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = trace.methodName
    val text = "$methodName${message.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    log(className, text, Level.INFO)
}

inline fun logWarn(
    message: String = "",
    clazzName: String? = null,
) {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = trace.methodName
    val text = "$methodName${message.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    log(className, text, Level.WARNING)
}

inline fun logError(
    message: String = "",
    clazzName: String? = null,
) {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = trace.methodName
    val text = "$methodName${message.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    log(className, text, Level.SEVERE)
}

fun log(
    tag: String,
    message: String,
    level: Level = Level.INFO,
) {
    Timber.tag("DL-$tag").apply {
        when (level) {
            Level.CONFIG -> d(message)
            Level.INFO -> i(message)
            Level.WARNING -> w(message)
            Level.SEVERE -> e(message)
        }
    }
}

fun parentClassName(name: String): String {
    if (!name.contains("$")) return name
    return "$${name.split("$").last { !it.isDigitsOnly() && it != "lambda" }}"
}

// ------------------------------------------------------------------
// ---- Service
// ------------------------------------------------------------------
inline fun <reified T : Service> startForegroundServiceCompat(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, T::class.java)
    )
}

inline fun <reified T : Service> stopServiceCompat(context: Context) {
    context.stopService(
        Intent(context, T::class.java)
    )
}


inline fun <reified T : Service> bindServiceCompat(
    context: Context,
    conn: ServiceConnection,
    flags: Int = Context.BIND_AUTO_CREATE,
) {
    context.bindService(
        Intent(context, T::class.java),
        conn,
        flags
    )
}

fun ByteArray.chunked(size: Int): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    var index = 0
    while (index < this.size) {
        val end = minOf(index + size, this.size)
        result.add(copyOfRange(index, end))
        index = end
    }
    return result
}