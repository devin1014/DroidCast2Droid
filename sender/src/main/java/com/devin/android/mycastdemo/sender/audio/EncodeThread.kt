package com.devin.android.mycastdemo.sender.audio

import android.media.MediaCodec
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class EncodeThread(
    private val codec: MediaCodec,
    private val pcmQueue: LinkedBlockingQueue<Pair<ByteArray, Int>>,
    private val freeInputBuffers: LinkedBlockingQueue<Int>,
) : Thread() {

    init {
        isDaemon = true
        name = "AudioEncode"
    }

    override fun run() {
        logInfo("[${Thread.currentThread().name}]start...", "thread")
        var pts = 0L
        val sampleRate = 44100
        val bytesPerSample = 2 // 16bit mono

        val result = runCatching {
            while (!currentThread().isInterrupted) {
                val index = freeInputBuffers.take() // 阻塞等有可用 input buffer
                val item = pcmQueue.poll(50, TimeUnit.MILLISECONDS) ?: run {
                    freeInputBuffers.put(index) // 没有 PCM，把 index 还回去
                    continue
                }

                val (pcm, size) = item
                val inputBuffer = codec.getInputBuffer(index) ?: continue
                val capacity = inputBuffer.remaining()
                val toWrite = minOf(size, capacity)

                inputBuffer.put(pcm, 0, toWrite)
                codec.queueInputBuffer(index, 0, toWrite, pts, 0)
                pts += toWrite.toLong() * 1_000_000L / (sampleRate * bytesPerSample)
                logInfo("[${Thread.currentThread().name}]queueInputBuffer=${toWrite}", "thread")
            }
            logInfo("[${Thread.currentThread().name}]done...", "thread")
        }
        if (result.isFailure) {
            logError("error= ${result.exceptionOrNull()}")
        }
    }
}
