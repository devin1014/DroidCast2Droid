package com.devin.android.cast.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import timber.log.Timber
import java.util.logging.Level

inline fun Any.getLogText(
    message: String = "",
    clazzName: String? = null,
): Pair<String, String> {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: this::class.simpleName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = parseMethodName(trace.methodName)
    val symbolic = if (methodName.isNotEmpty() && message.isNotEmpty()) "=>" else ""
    val text = "${methodName}$symbolic${message}"
    return className to text
}

inline fun Any.logDebug(
    message: String = "",
    clazzName: String? = null,
) {
    val (className, text) = getLogText(message, clazzName)
    log(className, text, Level.CONFIG)
}

inline fun Any.logInfo(
    message: String = "",
    clazzName: String? = null,
) {
    val (className, text) = getLogText(message, clazzName)
    log(className, text, Level.INFO)
}

inline fun Any.logWarn(
    message: String = "",
    clazzName: String? = null,
) {
    val (className, text) = getLogText(message, clazzName)
    log(className, text, Level.WARNING)
}

inline fun Any.logError(
    message: String = "",
    clazzName: String? = null,
) {
    val (className, text) = getLogText(message, clazzName)
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

fun parseMethodName(name: String): String {
    return if (name.split("$").last().isDigitsOnly()) "" else name
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