package com.devin.android.mycastdemo.sender.audio

import android.media.AudioRecord
import com.devin.android.cast.core.chunked
import com.devin.android.cast.core.logDebug
import com.devin.android.cast.core.logError
import com.devin.android.cast.core.logInfo
import java.util.concurrent.LinkedBlockingQueue

class AudioCaptureThread(
    private val audioRecord: AudioRecord,
    private val pcmQueue: LinkedBlockingQueue<Pair<ByteArray, Int>>,
) : Thread() {

    init {
        isDaemon = true
        name = "AudioCapture"
    }

    override fun run() {
        val result = runCatching {
            logInfo("[${Thread.currentThread().name}]start...", "thread")
            val buffer = ByteArray(4096)
            audioRecord.startRecording()

            while (!currentThread().isInterrupted) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val copy = buffer.copyOf(read)

//                logDebug(
//                    "audio record =>\n    ${
//                        copy.chunked(256).joinToString("\n    ") { chunk ->
//                            chunk.joinToString(" ") { "%02x".format(it) }
//                        }
//                    }"
//                )
                // 队列满时丢弃旧数据，避免延迟累积
                if (!pcmQueue.offer(copy to read)) {
                    pcmQueue.poll()
                    pcmQueue.offer(copy to read)
                }
            }
            logInfo("[${currentThread().name}]done...", "thread")
        }
        if (result.isFailure) {
            logError("error= ${result.exceptionOrNull()}")
        }
    }
}