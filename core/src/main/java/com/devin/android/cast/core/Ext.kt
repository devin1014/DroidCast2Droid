package com.devin.android.cast.core

import androidx.core.text.isDigitsOnly
import timber.log.Timber
import java.util.logging.Level

inline fun logInfo(
    message: String = "",
    clazzName: String? = null,
) {
    val trace = Throwable().stackTrace[0]
    val className = clazzName ?: parentClassName(trace.className.substringAfterLast("."))
    val methodName = trace.methodName
    val text = "$methodName${message.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    Timber.tag("DL-$className").i(text)
    log(className, text)
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