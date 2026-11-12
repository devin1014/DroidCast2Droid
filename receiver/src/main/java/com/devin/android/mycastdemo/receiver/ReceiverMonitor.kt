package com.devin.android.mycastdemo.receiver

class ReceiverMonitor {
    private var bytes = 0L
    private var latencySum = 0L
    private var lastStatTime = System.currentTimeMillis()
    private val fpsArray = FloatArray(250)
    private var count = 0
    private val lock = ByteArray(0)
    private var frameCount = 0
    private var lastSampleTime = 0L

    fun clear() {
        bytes = 0L
        frameCount = 0
    }

    fun onBytesReceived(size: Int) {
        bytes += size
    }

    // 在 MediaCodec 的 onOutputBufferAvailable / 渲染循环里
    fun onFrameRendered() {
        frameCount++
        val now = System.currentTimeMillis()
        if (lastSampleTime == 0L) {
            lastSampleTime = now
            return
        }
        val elapsed = now - lastSampleTime
        if (elapsed >= 1000) {
            synchronized(lock) {
                val fps = frameCount * 1000f / elapsed
                fpsArray[count++] = fps
                frameCount = 0
                lastSampleTime = now
            }
        }
    }

    fun recordLatency(ms: Long) {
        latencySum += ms
    }

    fun getBitrate(): Double {
        val now = System.currentTimeMillis()
        val dt = now - lastStatTime
        if (dt <= 0) return 0.0

        val result = bytes * 8.0 / dt
        bytes = 0
        lastStatTime = now
        return result
    }

    fun getFps(): Double {
        var result = 0.0
        synchronized(lock) {
            if (count > 0) {
                for (index in 0 until count) {
                    result += fpsArray[index]
                }
                result /= count
                count = 0
            }
        }
        return result
    }
}