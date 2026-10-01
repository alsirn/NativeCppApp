package com.example.nativecppapp.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import kotlin.math.sin

class AudioPlaybackManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var isMuted: Boolean = false
    private var volume: Float = 1.0f

    // Synthesized beat generator for preview when no audio file is imported yet
    private var synthTrack: AudioTrack? = null
    private var isSynthPlaying = false
    private var synthThread: Thread? = null

    fun loadAudio(filePath: String) {
        currentFilePath = filePath
        val file = File(filePath)
        if (file.exists() && file.canRead()) {
            stop()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    setVolume(if (isMuted) 0f else volume, if (isMuted) 0f else volume)
                }
                Log.i(TAG, "Audio loaded successfully from $filePath")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load audio file: $filePath", e)
                mediaPlayer = null
            }
        } else {
            mediaPlayer = null
        }
    }

    fun play(frame: Long, fps: Double) {
        val safeFps = if (fps > 0.0) fps else 60.0
        val targetMs = ((frame * 1000.0) / safeFps).toInt()

        if (mediaPlayer != null) {
            try {
                mediaPlayer?.seekTo(targetMs)
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer play error", e)
            }
        } else {
            // Synthesize subtle rhythm beep when no file is present
            startSyntheticAudio(targetMs)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
        stopSyntheticAudio()
    }

    fun seekTo(frame: Long, fps: Double) {
        val safeFps = if (fps > 0.0) fps else 60.0
        val targetMs = ((frame * 1000.0) / safeFps).toInt()
        try {
            mediaPlayer?.seekTo(targetMs)
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        stopSyntheticAudio()
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        val curVol = if (isMuted) 0f else volume
        try {
            mediaPlayer?.setVolume(curVol, curVol)
        } catch (_: Exception) {}
        return isMuted
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        val curVol = if (isMuted) 0f else volume
        try {
            mediaPlayer?.setVolume(curVol, curVol)
        } catch (_: Exception) {}
    }

    private fun startSyntheticAudio(startMs: Int) {
        if (isMuted || isSynthPlaying) return
        isSynthPlaying = true

        synthThread = Thread {
            val sampleRate = 44100
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            synthTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synthTrack?.play()

            val buffer = ShortArray(1024)
            var currentSample = (startMs * 44.1).toLong()

            while (isSynthPlaying) {
                for (i in buffer.indices) {
                    val timeSec = (currentSample + i) / 44100.0
                    // Create gentle periodic 440Hz / 880Hz studio pulse every 0.5 second
                    val beatTime = timeSec % 0.5
                    val amp = if (beatTime < 0.08) {
                        (1.0 - (beatTime / 0.08)) * 0.15 * volume
                    } else 0.0

                    val freq = if ((timeSec % 2.0) < 0.5) 523.25 else 440.0 // C5 or A4
                    buffer[i] = (sin(2.0 * Math.PI * freq * timeSec) * amp * Short.MAX_VALUE).toInt().toShort()
                }
                currentSample += buffer.size
                synthTrack?.write(buffer, 0, buffer.size)
            }

            try {
                synthTrack?.stop()
                synthTrack?.release()
            } catch (_: Exception) {}
            synthTrack = null
        }.apply { start() }
    }

    private fun stopSyntheticAudio() {
        isSynthPlaying = false
        synthThread?.interrupt()
        synthThread = null
    }

    companion object {
        private const val TAG = "AudioPlaybackManager"
    }
}
