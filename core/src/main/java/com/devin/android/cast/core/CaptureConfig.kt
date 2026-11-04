package com.devin.android.cast.core

data class CaptureConfig(
    val width: Int,
    val targetWidth: Int,
    val height: Int,
    val dpi: Int,
    val bitrate: Int = 2_000_000,
    val frameRate: Int = 30,
    val keyFrameInterval: Int = 2,
)