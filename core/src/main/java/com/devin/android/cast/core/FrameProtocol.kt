package com.devin.android.cast.core

import java.nio.ByteBuffer

object FrameProtocol {
    private const val MAGIC: Short = 0x5153

    const val TYPE_FORMAT = 0x01.toByte()
    const val TYPE_VIDEO_CONFIG = 0x02.toByte()
    const val TYPE_VIDEO_FRAME = 0x04.toByte()

    // 视频帧类型常量（供接收端参考或日志记录）
    const val FRAME_TYPE_KEY = 1   // 关键帧
    const val FRAME_TYPE_OTHER = 2 // 普通帧

    fun packFormat(
        width: Int,
        height: Int,
        bitrate: Int,
        csd0Bytes: ByteArray,
        csd1Bytes: ByteArray,
    ): ByteArray {
        // 5个int + csd0 + csd1
        val payloadSize = 20 + csd0Bytes.size + csd1Bytes.size

        // type + payload
        val buffer = ByteBuffer.allocate(1 + 4 + payloadSize)
        buffer.put(TYPE_FORMAT)
        buffer.putInt(payloadSize)
        buffer.putInt(width)
        buffer.putInt(height)
        buffer.putInt(bitrate)
        buffer.putInt(csd0Bytes.size)
        buffer.putInt(csd1Bytes.size)
        buffer.put(csd0Bytes)
        buffer.put(csd1Bytes)
        return buffer.array()
    }

    fun packConfig(
        data: ByteArray,
        presentationTimeUs: Long,
    ): ByteArray {
        // 载荷长度 = 8字节时间戳 + 4字节帧类型 + 视频数据长度
        val payloadSize = 8 + 4 + data.size
        val buffer = ByteBuffer.allocate(1 + 4 + payloadSize)
        buffer.put(TYPE_VIDEO_CONFIG)          // 1字节：类型
        buffer.putInt(payloadSize)          // 4字节：载荷长度
        buffer.putLong(presentationTimeUs)  // 8字节：时间戳（解码和渲染必须）
        buffer.putInt(FRAME_TYPE_OTHER)     // 4字节：帧类型
        buffer.put(data)                      // 剩余：视频帧数据
        return buffer.array()
    }

    fun packVideo(
        data: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    ): ByteArray {
        val frameType = if (isKeyFrame) FRAME_TYPE_KEY else FRAME_TYPE_OTHER
        // 载荷长度 = 8字节时间戳 + 4字节帧类型 + 视频数据长度
        val payloadSize = 8 + 4 + data.size
        val buffer = ByteBuffer.allocate(1 + 4 + payloadSize)
        buffer.put(TYPE_VIDEO_FRAME)           // 1字节：类型
        buffer.putInt(payloadSize)          // 4字节：载荷长度
        buffer.putLong(presentationTimeUs)  // 8字节：时间戳（解码和渲染必须）
        buffer.putInt(frameType)            // 4字节：帧类型
        buffer.put(data)                      // 剩余：视频帧数据
        return buffer.array()
    }
}
