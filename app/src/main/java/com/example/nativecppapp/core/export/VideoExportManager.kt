package com.example.nativecppapp.core.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.nativecppapp.NativeEngineBridge
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

class VideoExportManager(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cancel() {
        isCancelled.set(true)
    }

    fun startExport(
        projectPtr: Long,
        width: Int,
        height: Int,
        fps: Double,
        totalFrames: Long,
        outputFile: File,
        audioFilePath: String? = null,
        bitrateBps: Int? = null,
        brightness: Float = 0.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        vignette: Float = 0.0f,
        filterMode: Int = 0,
        onProgress: (currentFrame: Long, totalFrames: Long) -> Unit,
        onCompleted: (outputFilePath: String) -> Unit,
        onError: (errorMsg: String) -> Unit
    ) {
        isCancelled.set(false)

        Thread {
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var audioExtractor: MediaExtractor? = null
            var exportCompositorPtr = 0L
            var muxerStarted = false
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            try {
                // Ensure output directory exists
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // 1. Check if audio track can be extracted from source file (must be AAC for MPEG-4 container)
                val safeFps = if (fps > 0.0) fps else 60.0
                var syntheticSamples: List<EncodedSample>? = null
                if (!audioFilePath.isNullOrBlank()) {
                    val aFile = File(audioFilePath)
                    if (aFile.exists() && aFile.canRead()) {
                        try {
                            val ext = MediaExtractor()
                            ext.setDataSource(audioFilePath)
                            for (i in 0 until ext.trackCount) {
                                val fmt = ext.getTrackFormat(i)
                                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                                if (mime == MediaFormat.MIMETYPE_AUDIO_AAC || mime == "audio/mp4a-latm") {
                                    ext.selectTrack(i)
                                    audioFormat = fmt
                                    audioExtractor = ext
                                    Log.i(TAG, "Audio track found for export: $mime")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not open audio file for export: $audioFilePath", e)
                            audioExtractor = null
                        }
                    }
                }

                // If no audio file or not AAC, synthesize studio rhythm pulse AAC audio
                if (audioFormat == null) {
                    val totalSec = (totalFrames.toDouble() / safeFps).coerceAtLeast(1.0)
                    val synthResult = generateSyntheticAacTrack(totalSec)
                    if (synthResult != null) {
                        audioFormat = synthResult.first
                        syntheticSamples = synthResult.second
                        Log.i(TAG, "Synthesized ${syntheticSamples.size} AAC samples for ${totalSec}s project duration")
                    }
                }

                // Make dimensions even for H.264
                val encWidth = if (width % 2 == 0) width else width - 1
                val encHeight = if (height % 2 == 0) height else height - 1

                val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
                val format = MediaFormat.createVideoFormat(mimeType, encWidth, encHeight).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    val calculatedBitrate = ((encWidth * encHeight * 4.0 * fps) / 30.0).toInt().coerceIn(4_000_000, 20_000_000)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps ?: calculatedBitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                encoder = MediaCodec.createEncoderByType(mimeType)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = encoder.createInputSurface()
                encoder.start()

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                // Initialize GPU Compositor for offline rendering
                exportCompositorPtr = NativeEngineBridge.nativeCreateGpuCompositor()
                NativeEngineBridge.nativeInitializeGpuCompositor(
                    exportCompositorPtr,
                    inputSurface,
                    encWidth,
                    encHeight
                )
                NativeEngineBridge.nativeUpdateGpuViewport(
                    exportCompositorPtr,
                    0f,
                    0f,
                    encWidth.toFloat(),
                    encHeight.toFloat()
                )
                NativeEngineBridge.nativeSetGpuShaderEffects(
                    exportCompositorPtr,
                    brightness,
                    contrast,
                    saturation,
                    vignette,
                    filterMode
                )

                val bufferInfo = MediaCodec.BufferInfo()

                fun drainEncoder(codec: MediaCodec, mux: MediaMuxer, endOfStream: Boolean) {
                    val timeoutUs = if (endOfStream) 10_000L else 2_500L
                    var eosRetryCount = 0

                    while (true) {
                        val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            if (!endOfStream) {
                                break
                            } else {
                                eosRetryCount++
                                if (eosRetryCount > 30) {
                                    Log.w(TAG, "Draining encoder timed out on EOS")
                                    break
                                }
                            }
                        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) {
                                throw RuntimeException("Format changed after muxer started")
                            }
                            val newFormat = codec.outputFormat
                            videoTrackIndex = mux.addTrack(newFormat)
                            if (audioFormat != null) {
                                audioTrackIndex = mux.addTrack(audioFormat!!)
                            }
                            mux.start()
                            muxerStarted = true
                            Log.d(TAG, "MediaMuxer started with videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex")
                        } else if (encoderStatus >= 0) {
                            eosRetryCount = 0
                            val encodedData = codec.getOutputBuffer(encoderStatus)
                                ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                mux.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            }

                            codec.releaseOutputBuffer(encoderStatus, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }

                // Render each frame through GPU pipeline directly into hardware encoder
                for (frame in 0 until totalFrames) {
                    if (isCancelled.get()) {
                        Log.i(TAG, "Export cancelled by user at frame $frame")
                        break
                    }

                    val presentationTimeNs = ((frame * 1_000_000_000.0) / safeFps).toLong()
                    NativeEngineBridge.nativeSetGpuPresentationTime(exportCompositorPtr, presentationTimeNs)
                    NativeEngineBridge.nativeRenderFrameGpu(exportCompositorPtr, projectPtr, frame.toDouble())

                    drainEncoder(encoder, muxer, false)

                    mainHandler.post {
                        onProgress(frame + 1, totalFrames)
                    }
                }

                if (!isCancelled.get()) {
                    encoder.signalEndOfInputStream()
                    drainEncoder(encoder, muxer, true)
                }

                // Mux audio track if present (either synthetic AAC beat or source file)
                if (!isCancelled.get() && muxerStarted && audioTrackIndex >= 0) {
                    if (syntheticSamples != null && syntheticSamples.isNotEmpty()) {
                        val audioBuf = ByteBuffer.allocateDirect(1024 * 64)
                        val audioBufInfo = MediaCodec.BufferInfo()
                        for (sample in syntheticSamples) {
                            if (isCancelled.get()) break
                            audioBuf.clear()
                            audioBuf.put(sample.data, sample.offset, sample.size)
                            audioBufInfo.set(0, sample.size, sample.presentationTimeUs, sample.flags)
                            muxer.writeSampleData(audioTrackIndex, audioBuf, audioBufInfo)
                        }
                        Log.i(TAG, "Muxed ${syntheticSamples.size} synthetic audio samples to exported MP4")
                    } else if (audioExtractor != null) {
                        val maxAudioDurationUs = ((totalFrames * 1_000_000.0) / safeFps).toLong()
                        val audioBuf = ByteBuffer.allocateDirect(1024 * 64)
                        val audioBufInfo = MediaCodec.BufferInfo()
                        while (!isCancelled.get()) {
                            val sampleSize = audioExtractor.readSampleData(audioBuf, 0)
                            if (sampleSize < 0) break
                            val sampleTimeUs = audioExtractor.sampleTime
                            if (sampleTimeUs > maxAudioDurationUs) break

                            audioBufInfo.offset = 0
                            audioBufInfo.size = sampleSize
                            audioBufInfo.presentationTimeUs = sampleTimeUs
                            audioBufInfo.flags = audioExtractor.sampleFlags
                            muxer.writeSampleData(audioTrackIndex, audioBuf, audioBufInfo)
                            audioExtractor.advance()
                        }
                        Log.i(TAG, "Muxed source audio track to exported MP4")
                    }
                }

                // Clean shutdown
                if (exportCompositorPtr != 0L) {
                    NativeEngineBridge.nativeDestroyGpuCompositor(exportCompositorPtr)
                    exportCompositorPtr = 0L
                }

                try {
                    encoder.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Encoder stop exception", e)
                }
                encoder.release()
                encoder = null

                inputSurface.release()

                if (muxerStarted) {
                    try {
                        muxer.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Muxer stop exception", e)
                    }
                }
                muxer.release()
                muxer = null

                try {
                    audioExtractor?.release()
                    audioExtractor = null
                } catch (_: Exception) {}

                if (!isCancelled.get()) {
                    var publicPath = outputFile.absolutePath

                    try {
                        // 1. Android Q+ (API 29+): Insert into MediaStore.Video.Media for Gallery visibility
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, outputFile.name)
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MotionLab")
                                put(MediaStore.Video.Media.IS_PENDING, 1)
                            }
                            val resolver = context.contentResolver
                            val insertUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                            if (insertUri != null) {
                                resolver.openOutputStream(insertUri)?.use { out ->
                                    outputFile.inputStream().use { inp ->
                                        inp.copyTo(out)
                                    }
                                }
                                values.clear()
                                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                                resolver.update(insertUri, values, null, null)
                                Log.i(TAG, "Successfully published video to MediaStore: $insertUri")
                            }
                        }

                        // 2. Direct copy to public /storage/emulated/0/Movies/MotionLab folder
                        val publicMoviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MotionLab")
                        if (!publicMoviesDir.exists()) publicMoviesDir.mkdirs()
                        val publicFile = File(publicMoviesDir, outputFile.name)
                        outputFile.copyTo(publicFile, overwrite = true)
                        publicPath = publicFile.absolutePath

                        // 3. Scan files into Android Gallery database
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(publicFile.absolutePath, outputFile.absolutePath),
                            arrayOf("video/mp4")
                        ) { path, uri ->
                            Log.i(TAG, "MediaScanner indexed $path -> uri: $uri")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error publishing video to public gallery", e)
                    }

                    val finalResultPath = publicPath
                    mainHandler.post {
                        onCompleted(finalResultPath)
                    }
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Export failed with error", e)
                try {
                    audioExtractor?.release()
                } catch (_: Exception) {}
                if (exportCompositorPtr != 0L) {
                    try {
                        NativeEngineBridge.nativeDestroyGpuCompositor(exportCompositorPtr)
                    } catch (_: Exception) {}
                }
                try {
                    encoder?.stop()
                } catch (_: Exception) {}
                try {
                    encoder?.release()
                } catch (_: Exception) {}
                try {
                    if (muxerStarted) muxer?.stop()
                } catch (_: Exception) {}
                try {
                    muxer?.release()
                } catch (_: Exception) {}

                mainHandler.post {
                    onError(e.localizedMessage ?: "Unknown export error")
                }
            }
        }.start()
    }

    private data class EncodedSample(
        val data: ByteArray,
        val offset: Int,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private fun generateSyntheticAacTrack(
        durationSec: Double,
        sampleRate: Int = 44100,
        channels: Int = 2,
        volume: Float = 0.5f
    ): Pair<MediaFormat, List<EncodedSample>>? {
        var aEncoder: MediaCodec? = null
        try {
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            aEncoder = MediaCodec.createEncoderByType(mime)
            aEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aEncoder.start()

            val samples = ArrayList<EncodedSample>()
            var actualOutputFormat: MediaFormat? = null
            val bufferInfo = MediaCodec.BufferInfo()

            val totalPcmFrames = (durationSec * sampleRate).toInt().coerceAtLeast(sampleRate)
            val framesPerChunk = 1024
            var pcmFramesWritten = 0
            var inputDone = false
            var outputDone = false
            var waitCount = 0

            while (!outputDone && waitCount < 100) {
                // 1. Feed input PCM
                if (!inputDone) {
                    val inIdx = aEncoder.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inBuf = aEncoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val framesLeft = totalPcmFrames - pcmFramesWritten
                            val framesToWrite = framesLeft.coerceAtMost(framesPerChunk)
                            if (framesToWrite > 0) {
                                val shortBuf = inBuf.asShortBuffer()
                                for (f in 0 until framesToWrite) {
                                    val sIndex = pcmFramesWritten + f
                                    val timeSec = sIndex.toDouble() / sampleRate
                                    val beatTime = timeSec % 0.5
                                    val amp = if (beatTime < 0.08) {
                                        (1.0 - (beatTime / 0.08)) * 0.25 * volume
                                    } else 0.0
                                    val freq = if ((timeSec % 2.0) < 0.5) 523.25 else 440.0
                                    val sampleVal = (sin(2.0 * Math.PI * freq * timeSec) * amp * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                                    for (ch in 0 until channels) {
                                        shortBuf.put(sampleVal)
                                    }
                                }
                                val bytesWritten = framesToWrite * channels * 2
                                val ptsUs = (pcmFramesWritten.toLong() * 1_000_000L) / sampleRate
                                aEncoder.queueInputBuffer(inIdx, 0, bytesWritten, ptsUs, 0)
                                pcmFramesWritten += framesToWrite
                            } else {
                                val ptsUs = (pcmFramesWritten.toLong() * 1_000_000L) / sampleRate
                                aEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }
                }

                // 2. Drain output AAC
                val outIdx = aEncoder.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    actualOutputFormat = aEncoder.outputFormat
                    waitCount = 0
                } else if (outIdx >= 0) {
                    waitCount = 0
                    val outBuf = aEncoder.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(bytes)
                        samples.add(
                            EncodedSample(
                                data = bytes,
                                offset = 0,
                                size = bufferInfo.size,
                                presentationTimeUs = bufferInfo.presentationTimeUs,
                                flags = bufferInfo.flags
                            )
                        )
                    }
                    aEncoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone) {
                        waitCount++
                    }
                }
            }

            try {
                aEncoder.stop()
            } catch (_: Exception) {}
            aEncoder.release()
            aEncoder = null

            if (actualOutputFormat != null && samples.isNotEmpty()) {
                return Pair(actualOutputFormat, samples)
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateSyntheticAacTrack error", e)
            try { aEncoder?.release() } catch (_: Exception) {}
        }
        return null
    }

    companion object {
        private const val TAG = "VideoExportManager"
    }
}
