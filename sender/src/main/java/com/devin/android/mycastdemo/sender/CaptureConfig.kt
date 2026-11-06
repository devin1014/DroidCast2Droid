package com.devin.android.mycastdemo.sender

data class CaptureConfig(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val targetResolution: Int = 720, //720, 1080
    val bitrate: Int = 2_000_000,
    val frameRate: Int = 30,
    val keyFrameInterval: Int = 2,
    val useImageReader: Boolean = false,
)