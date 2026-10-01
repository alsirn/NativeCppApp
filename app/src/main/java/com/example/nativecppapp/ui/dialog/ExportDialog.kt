package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.core.export.VideoExportManager
import com.example.nativecppapp.ui.theme.MotionTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportDialog(
    context: Context,
    private val projectPtr: Long,
    private val projectWidth: Int,
    private val projectHeight: Int,
    private val projectFps: Double,
    private val totalFrames: Long,
    private val audioFilePath: String? = null,
    private val brightness: Float = 0.0f,
    private val contrast: Float = 1.0f,
    private val saturation: Float = 1.0f,
    private val vignette: Float = 0.0f,
    private val filterMode: Int = 0,
    private val onExportProgressFrame: (frame: Long) -> Unit,
    private val onExportCompleted: (outputFilePath: String) -> Unit
) : Dialog(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var exportManager: VideoExportManager? = null

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(36, 36, 36, 36)
        }

        val header = TextView(context).apply {
            text = "Ekspor Video — MotionLab"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        root.addView(header)

        // Configuration View
        val configLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val formatInfo = TextView(context).apply {
            text = "Format: MP4 (H.264 / Hardware AVC) • ${projectWidth}×${projectHeight} • ${projectFps.toInt()} FPS"
            setTextColor(MotionTheme.TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        configLayout.addView(formatInfo)

        val durationSec = if (projectFps > 0) totalFrames / projectFps else 5.0
        val estMb = (projectWidth * projectHeight * 4.0 * projectFps / 30.0 / 8.0 / 1024.0 / 1024.0 * durationSec).toInt().coerceAtLeast(1)

        val bitrateInfo = TextView(context).apply {
            text = "Akselerasi: GPU Offline Render • Estimasi: ~${estMb} MB (${String.format("%.1f", durationSec)}s)"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }
        configLayout.addView(bitrateInfo)

        // Progress Section (hidden initially)
        val progressLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val progressStatus = TextView(context).apply {
            text = "Sedang Mengekspor: Frame 0 / $totalFrames"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        progressLayout.addView(progressStatus)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = totalFrames.toInt()
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                16
            ).apply {
                bottomMargin = 24
            }
        }
        progressLayout.addView(progressBar)

        root.addView(configLayout)
        root.addView(progressLayout)

        // Actions
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val btnCancel = Button(context).apply {
            text = "Batal"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginEnd = dp(8)
            }
            setTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                exportManager?.cancel()
                dismiss()
            }
        }
        actionRow.addView(btnCancel)

        val btnStart = Button(context).apply {
            text = "Mulai Ekspor"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener {
                configLayout.visibility = View.GONE
                progressLayout.visibility = View.VISIBLE
                btnCancel.text = "Hentikan"

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                val outputFile = File(moviesDir, "MotionLab_${timeStamp}.mp4")

                exportManager = VideoExportManager(context)
                exportManager?.startExport(
                    projectPtr = projectPtr,
                    width = projectWidth,
                    height = projectHeight,
                    fps = projectFps,
                    totalFrames = totalFrames,
                    outputFile = outputFile,
                    audioFilePath = audioFilePath,
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    vignette = vignette,
                    filterMode = filterMode,
                    onProgress = { currentFrame, total ->
                        progressBar.progress = currentFrame.toInt()
                        val pct = if (total > 0) (currentFrame * 100 / total) else 0
                        progressStatus.text = "Mengekspor GPU: Frame $currentFrame / $total ($pct%)"
                        onExportProgressFrame(currentFrame)
                    },
                    onCompleted = { filePath ->
                        progressBar.progress = totalFrames.toInt()
                        progressStatus.text = "Ekspor Selesai!\nTersimpan di Galeri & Movies/MotionLab"
                        onExportCompleted(filePath)
                        handler.postDelayed({ dismiss() }, 1800)
                    },
                    onError = { errorMsg ->
                        progressStatus.text = "Gagal Ekspor: $errorMsg"
                        progressStatus.setTextColor(Color.RED)
                        btnCancel.text = "Tutup"
                    }
                )
            }
        }
        actionRow.addView(btnStart)
        root.addView(actionRow)

        setContentView(root)
    }
}
