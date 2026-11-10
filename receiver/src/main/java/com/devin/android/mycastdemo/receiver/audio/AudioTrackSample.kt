package com.devin.android.mycastdemo.receiver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

object AudioTrackSample {

    private const val SAMPLE_RATE = 44100

    private fun createAudioRecord(sampleRate: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun play() {
        thread(true) {
            val sampleRate = SAMPLE_RATE
            val audioTrack = createAudioRecord(sampleRate)
            audioTrack.play()

            // 写一段正弦波测试音
            val buffer = getTestAudio()
            audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)

            audioTrack.stop()
            audioTrack.release()
        }
    }

    fun play(data: ByteArray) {
        thread(true) {
            val sampleRate = SAMPLE_RATE
            val audioTrack = createAudioRecord(sampleRate)
            audioTrack.play()

            audioTrack.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)

            audioTrack.stop()
            audioTrack.release()
        }
    }

    private fun getTestAudio(): ShortArray {
        val durationSec = 2
        val totalSamples = SAMPLE_RATE * durationSec
        val buffer = ShortArray(totalSamples)
        for (i in buffer.indices) {
            buffer[i] = (Short.MAX_VALUE * 0.3f *
                    sin(2 * PI * 440.0 * i / SAMPLE_RATE)).toInt().toShort()
        }
        return buffer
    }
}