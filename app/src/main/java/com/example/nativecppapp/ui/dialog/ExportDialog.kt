package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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

    // Export Options state
    private var exportW = projectWidth
    private var exportH = projectHeight
    private var exportFps = if (projectFps > 0) projectFps else 60.0
    private var exportBitrate = 8_000_000 // 8 Mbps default

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }

        // Header
        val headerBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val tagView = TextView(context).apply {
            text = "HARDWARE ENCODER (MEDIACODEC AVC)"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Ekspor Video Komposit"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerBox.addView(titleView)
        root.addView(headerBox)

        // Configuration View inside ScrollView to support smaller screens
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val configLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Dynamic Summary Card
        val summaryCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }

        val summaryText = TextView(context).apply {
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        summaryCard.addView(summaryText)

        val estimateText = TextView(context).apply {
            setTextColor(Color.parseColor("#34D399")) // Emerald
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        summaryCard.addView(estimateText)
        configLayout.addView(summaryCard)

        fun updateSummary() {
            val durSec = (totalFrames.toDouble() / exportFps).coerceAtLeast(0.1)
            val estMb = (exportBitrate.toDouble() / 8.0 * durSec / (1024.0 * 1024.0)).coerceAtLeast(0.2)
            summaryText.text = "Target: ${exportW}×${exportH} • ${exportFps.toInt()} FPS • ${exportBitrate / 1_000_000} Mbps"
            estimateText.text = "Durasi: ${String.format("%.1f", durSec)}s • Estimasi File: ~${String.format("%.1f", estMb)} MB (H.264 MP4)"
        }
        updateSummary()

        // 2. Preset Resolusi & Format Sosial Media
        val resTitle = TextView(context).apply {
            text = "PRESET FORMAT & RESOLUSI"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, 0, 0, dp(6))
        }
        configLayout.addView(resTitle)

        val presets = listOf(
            Triple("Proyek Asli", projectWidth, projectHeight),
            Triple("TikTok/Reels (9:16)", 1080, 1920),
            Triple("YouTube HD (16:9)", 1920, 1080),
            Triple("Instagram (1:1)", 1080, 1080),
            Triple("Portrait (4:5)", 1080, 1350),
            Triple("HD Ringan (720p)", if (projectWidth < projectHeight) 720 else 1280, if (projectWidth < projectHeight) 1280 else 720)
        )

        val resButtons = mutableListOf<Button>()
        val grid1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
            layoutParams = lp
        }
        val grid2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }

        fun updateResButtons(selectedW: Int, selectedH: Int) {
            presets.forEachIndexed { i, triple ->
                val btn = resButtons.getOrNull(i) ?: return@forEachIndexed
                val isSelected = triple.second == selectedW && triple.third == selectedH
                btn.setBackgroundColor(if (isSelected) Color.parseColor("#4F46E5") else MotionTheme.SURFACE_HIGH)
                btn.setTextColor(if (isSelected) Color.WHITE else MotionTheme.TEXT_PRIMARY)
            }
        }

        presets.forEachIndexed { i, p ->
            val btn = Button(context).apply {
                text = p.first
                textSize = 10f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (i % 3 != 2) marginEnd = dp(6)
                }
                setOnClickListener {
                    exportW = p.second
                    exportH = p.third
                    updateResButtons(exportW, exportH)
                    updateSummary()
                }
            }
            resButtons.add(btn)
            if (i < 3) grid1.addView(btn) else grid2.addView(btn)
        }
        configLayout.addView(grid1)
        configLayout.addView(grid2)
        updateResButtons(exportW, exportH)

        // 3. Frame Rate Selector
        val fpsTitle = TextView(context).apply {
            text = "FRAME RATE (KELANCARAN)"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, 0, 0, dp(6))
        }
        configLayout.addView(fpsTitle)

        val fpsOptions = listOf(
            Pair("60 FPS (Ultra Smooth)", 60.0),
            Pair("30 FPS (Standar)", 30.0),
            Pair("24 FPS (Sinema)", 24.0)
        )
        val fpsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }
        val fpsButtons = mutableListOf<Button>()

        fun updateFpsButtons(selectedFps: Double) {
            fpsOptions.forEachIndexed { i, pair ->
                val btn = fpsButtons.getOrNull(i) ?: return@forEachIndexed
                val isSelected = pair.second == selectedFps
                btn.setBackgroundColor(if (isSelected) Color.parseColor("#4F46E5") else MotionTheme.SURFACE_HIGH)
                btn.setTextColor(if (isSelected) Color.WHITE else MotionTheme.TEXT_PRIMARY)
            }
        }

        fpsOptions.forEachIndexed { i, pair ->
            val btn = Button(context).apply {
                text = pair.first
                textSize = 10f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (i != fpsOptions.size - 1) marginEnd = dp(6)
                }
                setOnClickListener {
                    exportFps = pair.second
                    updateFpsButtons(exportFps)
                    updateSummary()
                }
            }
            fpsButtons.add(btn)
            fpsRow.addView(btn)
        }
        configLayout.addView(fpsRow)
        updateFpsButtons(exportFps)

        // 4. Bitrate / Kualitas
        val bitrateTitle = TextView(context).apply {
            text = "KUALITAS BITRATE"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, 0, 0, dp(6))
        }
        configLayout.addView(bitrateTitle)

        val bitrateOptions = listOf(
            Pair("Tinggi (16M)", 16_000_000),
            Pair("Optimal (8M)", 8_000_000),
            Pair("Hemat (4M)", 4_000_000)
        )
        val bitrateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }
        val bitrateButtons = mutableListOf<Button>()

        fun updateBitrateButtons(selectedBps: Int) {
            bitrateOptions.forEachIndexed { i, pair ->
                val btn = bitrateButtons.getOrNull(i) ?: return@forEachIndexed
                val isSelected = pair.second == selectedBps
                btn.setBackgroundColor(if (isSelected) Color.parseColor("#4F46E5") else MotionTheme.SURFACE_HIGH)
                btn.setTextColor(if (isSelected) Color.WHITE else MotionTheme.TEXT_PRIMARY)
            }
        }

        bitrateOptions.forEachIndexed { i, pair ->
            val btn = Button(context).apply {
                text = pair.first
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (i != bitrateOptions.size - 1) marginEnd = dp(6)
                }
                setOnClickListener {
                    exportBitrate = pair.second
                    updateBitrateButtons(exportBitrate)
                    updateSummary()
                }
            }
            bitrateButtons.add(btn)
            bitrateRow.addView(btn)
        }
        configLayout.addView(bitrateRow)
        updateBitrateButtons(exportBitrate)

        scrollView.addView(configLayout)
        root.addView(scrollView)

        // Progress Section (hidden initially)
        val progressLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, dp(10))
        }

        val progressStatus = TextView(context).apply {
            text = "Mempersiapkan encoder..."
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(8))
        }
        progressLayout.addView(progressStatus)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = totalFrames.toInt()
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
            ).apply {
                bottomMargin = dp(14)
            }
        }
        progressLayout.addView(progressBar)
        root.addView(progressLayout)

        // Actions
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }

        val btnCancel = Button(context).apply {
            text = "Batal"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(16), 0, dp(16), 0)
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
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(20), 0, dp(20), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4F46E5"))
            setOnClickListener {
                scrollView.visibility = View.GONE
                progressLayout.visibility = View.VISIBLE
                btnCancel.text = "Hentikan"
                isEnabled = false

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                val outputFile = File(moviesDir, "MotionLab_${timeStamp}.mp4")

                exportManager = VideoExportManager(context)
                exportManager?.startExport(
                    projectPtr = projectPtr,
                    width = exportW,
                    height = exportH,
                    fps = exportFps,
                    totalFrames = totalFrames,
                    outputFile = outputFile,
                    audioFilePath = audioFilePath,
                    bitrateBps = exportBitrate,
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
