package com.example.nativecppapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.core.audio.AudioPlaybackManager
import com.example.nativecppapp.core.history.ProjectHistoryManager
import com.example.nativecppapp.ui.dialog.AddElementDialog
import com.example.nativecppapp.ui.dialog.BlendModeDialog
import com.example.nativecppapp.ui.dialog.AudioMixerDialog
import com.example.nativecppapp.ui.dialog.ExportDialog
import com.example.nativecppapp.ui.dialog.KeyframeEditorDialog
import com.example.nativecppapp.ui.dialog.NewProjectDialog
import com.example.nativecppapp.ui.dialog.ProjectTemplatesDialog
import com.example.nativecppapp.ui.dialog.ShaderEffectsDialog
import com.example.nativecppapp.ui.dialog.ShapeStyleDialog
import com.example.nativecppapp.ui.dialog.TransformInspectorDialog
import com.example.nativecppapp.ui.dialog.TransitionDialog
import com.example.nativecppapp.ui.theme.MotionTheme
import com.example.nativecppapp.ui.view.CanvasViewportView
import com.example.nativecppapp.ui.view.GraphEditorView
import com.example.nativecppapp.ui.view.TimelineDeckView
import java.io.File

class MainActivity : Activity() {

    data class MediaAssetItem(
        val id: String,
        val name: String,
        val filePath: String,
        val isVideo: Boolean,
        val isAudio: Boolean,
        val resolutionOrDuration: String,
        val thumbnailBitmap: Bitmap? = null
    )

    private val REQUEST_CODE_PICK_MEDIA = 1001
    private val REQUEST_CODE_PICK_AUDIO = 1002

    private val mediaLibraryAssets = mutableListOf<MediaAssetItem>()
    private var activeMediaFilter = "SEMUA"
    private lateinit var mediaCardsContainer: LinearLayout
    private lateinit var mediaItemCountBadge: TextView
    private val navButtons = mutableListOf<Button>()
    private val filterButtons = mutableListOf<Button>()

    private var currentProjectPtr: Long = 0L
    private var projectTitle: String = "Proyek 1"
    private var projectWidth: Int = 1080
    private var projectHeight: Int = 1920
    private var projectFps: Double = 60.0
    private var projectDurationFrames: Long = 300 // 5 seconds default

    private var currentFrame: Long = 0L
    private var isPlaying = false
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var previewBitmap: Bitmap? = null
    private var compositorPtr: Long = 0L
    private var audioPlaybackManager: AudioPlaybackManager? = null
    private var currentAudioFilePath: String? = null

    // Visual Shader Effects
    private var effectBrightness = 0.0f
    private var effectContrast = 1.0f
    private var effectSaturation = 1.0f
    private var effectVignette = 0.0f
    private var effectFilterMode = 0

    // History & Undo/Redo Engine
    private val historyManager = ProjectHistoryManager()
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private var copiedTransformState: NativeEngineBridge.TransformState? = null

    // UI References
    private lateinit var viewportView: CanvasViewportView
    private lateinit var graphViewportView: CanvasViewportView
    private lateinit var timelineView: TimelineDeckView
    private lateinit var graphTimelineView: TimelineDeckView
    private lateinit var graphEditorView: GraphEditorView
    private lateinit var graphTimecodeText: TextView
    private lateinit var graphBtnPlayPause: Button
    private lateinit var graphSeekSlider: SeekBar
    private lateinit var graphKeyframeLabel: TextView
    private lateinit var graphTimeReadout: TextView
    private lateinit var graphTimeSeekBar: SeekBar
    private lateinit var graphPropertyValueReadout: TextView
    private lateinit var graphPropertyValueSeekBar: SeekBar
    private var activeGraphProperty = "posX"
    private lateinit var editorContainer: LinearLayout
    private lateinit var graphContainer: LinearLayout
    private lateinit var mediaContainer: LinearLayout
    private lateinit var templatesContainer: LinearLayout
    private var activeTemplateFilter = "SEMUA"
    private lateinit var templateCardsContainer: LinearLayout
    private val templateFilterButtons = mutableListOf<Button>()
    private lateinit var timecodeText: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var subtitleInfo: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Audio Subsystem
        audioPlaybackManager = AudioPlaybackManager(this)

        // Initialize Core C++ Project
        initCppProject()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setOnApplyWindowInsetsListener { _, insets ->
                setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }

        // 1. Top Header Bar
        val headerBar = createHeaderBar()
        rootLayout.addView(headerBar)
        updateUndoRedoButtonStates()

        // 2. Middle Content Deck (Houses Editor, Graph, and Media views)
        val middleContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        editorContainer = createEditorWorkspace()
        graphContainer = createGraphWorkspace()
        mediaContainer = createMediaLibraryWorkspace()
        templatesContainer = createTemplatesWorkspace()

        middleContainer.addView(editorContainer)
        middleContainer.addView(graphContainer)
        middleContainer.addView(mediaContainer)
        middleContainer.addView(templatesContainer)

        // Default to Editor view
        editorContainer.visibility = View.VISIBLE
        graphContainer.visibility = View.GONE
        mediaContainer.visibility = View.GONE
        templatesContainer.visibility = View.GONE

        rootLayout.addView(middleContainer)

        // 3. Bottom Studio Navigation Bar
        val bottomNav = createBottomNavigationBar()
        rootLayout.addView(bottomNav)

        setContentView(rootLayout)

        // Populate tracks now that all UI workspaces (Editor, Graph, Media) are initialized
        populateTimelineTracks()

        // Initial Frame Render
        renderCurrentFrame()
    }

    private fun initCppProject(templateId: String = "DEFAULT") {
        if (currentProjectPtr != 0L) {
            NativeEngineBridge.nativeDestroyProject(currentProjectPtr)
        }
        if (compositorPtr != 0L) {
            NativeEngineBridge.nativeDestroyGpuCompositor(compositorPtr)
        }
        
        currentProjectPtr = NativeEngineBridge.nativeCreateProject(
            title = projectTitle,
            width = projectWidth,
            height = projectHeight,
            fps = projectFps,
            durationFrames = projectDurationFrames,
            bgArgb = 0xFF131315.toInt()
        )
        compositorPtr = NativeEngineBridge.nativeCreateGpuCompositor()

        when (templateId) {
            "TIKTOK_VIRAL" -> {
                // 1. Text Title Pop-In
                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_viral", "VIRAL REELS", "VIRAL REELS",
                    0, projectDurationFrames, Color.WHITE, 64.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleX", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleX", 25, 1.25, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleX", 40, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleY", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleY", 25, 1.25, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_viral", "scaleY", 40, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // 2. Cyber Bar Sweep
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_bar", "Cyber Bar", NativeEngineBridge.ShapeType.RECTANGLE.value,
                    0, projectDurationFrames, Color.parseColor("#06B6D4"), 720.0, 110.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_bar", "posX", 0, -1000.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.16, 1.0, 0.3, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_bar", "posX", 30, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // 3. Drop Bubble
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_bubble", "Drop Bubble", NativeEngineBridge.ShapeType.CIRCLE.value,
                    15, projectDurationFrames - 15, Color.parseColor("#F43F5E"), 180.0, 180.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_bubble", "posY", 15, 600.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_bubble", "posY", 45, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                currentAudioFilePath = "/storage/emulated/0/Music/beat.mp3"
                NativeEngineBridge.nativeAddAudioLayer(currentProjectPtr, "audio_1", "Synth Beat", currentAudioFilePath ?: "", 0, projectDurationFrames)
                effectFilterMode = 5 // Neon Glow
            }
            "YOUTUBE_LOWER_THIRD" -> {
                // Speaker Name
                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_speaker", "JOHN DOE", "JOHN DOE",
                    0, projectDurationFrames, Color.WHITE, 56.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_speaker", "posY", 0, 300.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.22, 1.0, 0.36, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_speaker", "posY", 35, 180.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // Subtitle Role
                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_role", "Creative Director", "Creative Director",
                    10, projectDurationFrames - 10, Color.parseColor("#F59E0B"), 36.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_role", "opacity", 10, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_role", "opacity", 40, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // Card Backdrop
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_card", "Latar Kartu", NativeEngineBridge.ShapeType.RECTANGLE.value,
                    0, projectDurationFrames, Color.parseColor("#18181B"), 900.0, 180.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_card", "posX", 0, -1200.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.16, 1.0, 0.3, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_card", "posX", 30, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // Accent Line
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_accent", "Aksen Garis", NativeEngineBridge.ShapeType.RECTANGLE.value,
                    10, projectDurationFrames - 10, Color.parseColor("#06B6D4"), 900.0, 10.0
                )
                effectFilterMode = 0
            }
            "LOGO_REVEAL" -> {
                // Diamond
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_diamond", "Logo Diamond", NativeEngineBridge.ShapeType.STAR.value,
                    0, projectDurationFrames, Color.parseColor("#818CF8"), 240.0, 240.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "scaleX", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "scaleX", 30, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "scaleY", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "scaleY", 30, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "rotation", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_diamond", "rotation", 60, 180.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // Ring
                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_ring", "Cincin Pendar", NativeEngineBridge.ShapeType.CIRCLE.value,
                    20, projectDurationFrames - 20, Color.parseColor("#38BDF8"), 260.0, 260.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_ring", "scaleX", 20, 0.5, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_ring", "scaleX", 60, 2.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_ring", "scaleY", 20, 0.5, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "shape_ring", "scaleY", 60, 2.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                // Text Brand
                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_brand", "MOTIONLAB", "MOTIONLAB",
                    35, projectDurationFrames - 35, Color.WHITE, 48.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_brand", "opacity", 35, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_brand", "opacity", 60, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                effectFilterMode = 0
            }
            "CYBERPUNK_GLITCH" -> {
                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_cyber1", "NIGHT CITY", "NIGHT CITY",
                    0, projectDurationFrames, Color.parseColor("#06B6D4"), 60.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_cyber1", "scaleX", 0, 0.2, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_cyber1", "scaleX", 25, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                NativeEngineBridge.nativeAddTextLayer(
                    currentProjectPtr, "text_cyber2", "PROJECT 2088", "PROJECT 2088",
                    15, projectDurationFrames - 15, Color.parseColor("#F43F5E"), 44.0
                )
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_cyber2", "posX", 15, -600.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.16, 1.0, 0.3, 1.0)
                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, "text_cyber2", "posX", 45, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                NativeEngineBridge.nativeAddShapeLayer(
                    currentProjectPtr, "shape_cyberbox", "Grid Cyber", NativeEngineBridge.ShapeType.RECTANGLE.value,
                    0, projectDurationFrames, Color.WHITE, 600.0, 300.0
                )

                effectFilterMode = 4 // RGB Split
                effectContrast = 1.35f
                effectSaturation = 1.6f
            }
            else -> {
                // Preload standard 3-layer stack from PRD & Stitch design
                NativeEngineBridge.nativeAddShapeLayer(
                    projectPtr = currentProjectPtr,
                    id = "shape_circle_1",
                    name = "Lingkaran 1",
                    shapeType = NativeEngineBridge.ShapeType.CIRCLE.value,
                    startFrame = 0,
                    durationFrames = projectDurationFrames,
                    fillColor = 0xFFFFFFFF.toInt(),
                    width = 300.0,
                    height = 300.0
                )

                NativeEngineBridge.nativeAddMediaLayer(
                    projectPtr = currentProjectPtr,
                    id = "media_video_1",
                    name = "Video Latar 01",
                    filePath = "/storage/emulated/0/DCIM/sample.mp4",
                    isVideo = true,
                    startFrame = 0,
                    durationFrames = projectDurationFrames
                )

                currentAudioFilePath = "/storage/emulated/0/Music/soundtrack.mp3"
                audioPlaybackManager?.loadAudio(currentAudioFilePath ?: "")

                NativeEngineBridge.nativeAddAudioLayer(
                    projectPtr = currentProjectPtr,
                    id = "audio_track_1",
                    name = "Audio Trek 01",
                    filePath = currentAudioFilePath ?: "",
                    startFrame = 0,
                    durationFrames = projectDurationFrames
                )

                NativeEngineBridge.addKeyframe(
                    currentProjectPtr, "shape_circle_1", "posX",
                    NativeEngineBridge.KeyframeData(0, -320.0, NativeEngineBridge.InterpolationMode.BEZIER, 0.42, 0.0, 0.58, 1.0)
                )
                NativeEngineBridge.addKeyframe(
                    currentProjectPtr, "shape_circle_1", "posX",
                    NativeEngineBridge.KeyframeData(projectDurationFrames / 2, 0.0, NativeEngineBridge.InterpolationMode.BEZIER, 0.42, 0.0, 0.58, 1.0)
                )
                NativeEngineBridge.addKeyframe(
                    currentProjectPtr, "shape_circle_1", "posX",
                    NativeEngineBridge.KeyframeData(projectDurationFrames, 320.0, NativeEngineBridge.InterpolationMode.BEZIER, 0.42, 0.0, 0.58, 1.0)
                )
            }
        }
        takeProjectSnapshot()
    }

    private fun createHeaderBar(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }

        // Row 1: Project Info and Brand
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = projectTitle
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        infoCol.addView(titleView)

        subtitleInfo = TextView(this).apply {
            text = "$projectWidth×$projectHeight • ${projectFps.toInt()}fps"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        infoCol.addView(subtitleInfo)
        titleRow.addView(infoCol)

        btnUndo = Button(this).apply {
            text = "↶"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(4) }
            setOnClickListener { performUndo() }
        }
        titleRow.addView(btnUndo)

        btnRedo = Button(this).apply {
            text = "↷"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(6) }
            setOnClickListener { performRedo() }
        }
        titleRow.addView(btnRedo)

        val brandBadge = TextView(this).apply {
            text = "MOTIONLAB"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
        }
        titleRow.addView(brandBadge)
        header.addView(titleRow)

        // Row 2: Action Buttons (+ Baru, Simpan, Buka, Ekspor)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        }

        val btnNew = createHeaderButton("+ Baru") {
            NewProjectDialog(this) { title, w, h, fps, bg ->
                projectTitle = title
                projectWidth = w
                projectHeight = h
                projectFps = fps
                titleView.text = title
                subtitleInfo.text = "$w×$h • ${fps.toInt()}fps"
                viewportView.updateProjectAspect(w, h)
                initCppProject()
                viewportView.activeSurface?.let { surf ->
                    if (compositorPtr != 0L) {
                        NativeEngineBridge.nativeInitializeGpuCompositor(compositorPtr, surf, projectWidth, projectHeight)
                        val r = viewportView.getCanvasRect()
                        NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, r.left, r.top, r.width(), r.height())
                    }
                }
                populateTimelineTracks()
                currentFrame = 0L
                renderCurrentFrame()
            }.show()
        }
        actionRow.addView(btnNew)

        val btnTemplate = createHeaderButton("Template") {
            ProjectTemplatesDialog(this) { tId, title, w, h, fps, dur ->
                projectTitle = title
                projectWidth = w
                projectHeight = h
                projectFps = fps
                projectDurationFrames = dur
                titleView.text = title
                subtitleInfo.text = "$w×$h • ${fps.toInt()}fps"
                viewportView.updateProjectAspect(w, h)
                initCppProject(tId)
                viewportView.activeSurface?.let { surf ->
                    if (compositorPtr != 0L) {
                        NativeEngineBridge.nativeInitializeGpuCompositor(compositorPtr, surf, projectWidth, projectHeight)
                        val r = viewportView.getCanvasRect()
                        NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, r.left, r.top, r.width(), r.height())
                        NativeEngineBridge.nativeSetGpuShaderEffects(compositorPtr, effectBrightness, effectContrast, effectSaturation, effectVignette, effectFilterMode)
                    }
                }
                populateTimelineTracks()
                currentFrame = 0L
                renderCurrentFrame()
                Toast.makeText(this, "Template dimuat: $title", Toast.LENGTH_SHORT).show()
            }.show()
        }
        actionRow.addView(btnTemplate)

        val btnSave = createHeaderButton("Simpan") {
            try {
                val json = NativeEngineBridge.nativeGetProjectJson(currentProjectPtr)
                val projectsDir = java.io.File(getExternalFilesDir(null), "projects").apply { mkdirs() }
                val safeName = projectTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                val projectFile = java.io.File(projectsDir, "${safeName}.motionlab")
                projectFile.writeText(json)
                Toast.makeText(this, "Proyek tersimpan:\n${projectFile.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(btnSave)

        val btnOpen = createHeaderButton("Buka") {
            val projectsDir = java.io.File(getExternalFilesDir(null), "projects").apply { mkdirs() }
            val files = projectsDir.listFiles { _, name -> name.endsWith(".motionlab") } ?: emptyArray()
            if (files.isEmpty()) {
                Toast.makeText(this, "Belum ada proyek tersimpan", Toast.LENGTH_SHORT).show()
                return@createHeaderButton
            }
            val names = files.map { it.nameWithoutExtension }.toTypedArray()
            android.app.AlertDialog.Builder(this)
                .setTitle("Buka Proyek Tersimpan")
                .setItems(names) { _, which ->
                    val chosenFile = files[which]
                    try {
                        val json = chosenFile.readText()
                        val loadedPtr = NativeEngineBridge.nativeLoadProjectJson(json)
                        if (loadedPtr != 0L) {
                            if (currentProjectPtr != 0L) {
                                NativeEngineBridge.nativeDestroyProject(currentProjectPtr)
                            }
                            currentProjectPtr = loadedPtr
                            projectTitle = chosenFile.nameWithoutExtension
                            titleView.text = projectTitle
                            populateTimelineTracks()
                            viewportView.updateProjectAspect(projectWidth, projectHeight)
                            viewportView.activeSurface?.let { surf ->
                                if (compositorPtr != 0L) {
                                    NativeEngineBridge.nativeInitializeGpuCompositor(compositorPtr, surf, projectWidth, projectHeight)
                                    val r = viewportView.getCanvasRect()
                                    NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, r.left, r.top, r.width(), r.height())
                                }
                            }
                            currentFrame = 0L
                            renderCurrentFrame()
                            historyManager.resetWithInitialState(json)
                            updateUndoRedoButtonStates()
                            Toast.makeText(this, "Proyek dimuat: $projectTitle", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Gagal mem-parsing file proyek", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        actionRow.addView(btnOpen)

        val btnExport = createHeaderButton("Ekspor") {
            updateProjectDurationFromLayers()
            ExportDialog(
                context = this,
                projectPtr = currentProjectPtr,
                projectWidth = projectWidth,
                projectHeight = projectHeight,
                projectFps = projectFps,
                totalFrames = projectDurationFrames,
                audioFilePath = currentAudioFilePath,
                brightness = effectBrightness,
                contrast = effectContrast,
                saturation = effectSaturation,
                vignette = effectVignette,
                filterMode = effectFilterMode,
                onExportProgressFrame = { f ->
                    currentFrame = f
                    renderCurrentFrame()
                },
                onExportCompleted = { path ->
                    Toast.makeText(this, "Tersimpan di Galeri & $path", Toast.LENGTH_LONG).show()
                }
            ).show()
        }
        actionRow.addView(btnExport)

        header.addView(actionRow)
        return header
    }

    private fun createHeaderButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { onClick() }
        }
    }

    private fun createEditorWorkspace(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            // 1. Viewport (Upper Half)
            viewportView = CanvasViewportView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1.1f
                )
                updateProjectAspect(projectWidth, projectHeight)
                onSurfaceCreated = { surface ->
                    if (compositorPtr != 0L) {
                        NativeEngineBridge.nativeInitializeGpuCompositor(compositorPtr, surface, projectWidth, projectHeight)
                        val r = getCanvasRect()
                        if (r.width() > 0 && r.height() > 0) {
                            NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, r.left, r.top, r.width(), r.height())
                        }
                        renderCurrentFrame()
                    }
                }
                onViewportCalculated = { left, top, w, h ->
                    if (compositorPtr != 0L) {
                        NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, left, top, w, h)
                        renderCurrentFrame()
                    }
                }
                onSurfaceDestroyed = {
                    // Handled inside nativeDestroyGpuCompositor usually or we can just pause rendering
                }
                onLayerPositionChanged = { newX, newY ->
                    // Interactive position change via dragging canvas
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "posX", currentFrame, newX.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "posY", currentFrame, newY.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                    }
                }
                onLayerScaleChanged = { sx, sy ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "scaleX", currentFrame, sx.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "scaleY", currentFrame, sy.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                    }
                }
                onLayerRotationChanged = { rot ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "rotation", currentFrame, rot.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                    }
                }
            }
            addView(viewportView)

            // 2. Transport Bar
            val transportBar = createTransportBar()
            addView(transportBar)

            // 3. Timeline Deck
            timelineView = TimelineDeckView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.9f
                )
                totalDurationFrames = projectDurationFrames
                currentFrame = 0L

                onFrameScrubbed = { f ->
                    currentFrame = f
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.currentFrame = f
                        graphTimelineView.invalidate()
                    }
                    audioPlaybackManager?.seekTo(f, projectFps)
                    renderCurrentFrame()
                }

                onTrackSelected = { idx, track ->
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.selectedTrackIndex = idx
                        graphTimelineView.invalidate()
                    }
                    if (::viewportView.isInitialized) {
                        viewportView.selectedLayerName = track.name
                    }
                    if (::graphViewportView.isInitialized) {
                        graphViewportView.selectedLayerName = track.name
                    }
                    if (::graphEditorView.isInitialized) {
                        val propLabel = when (activeGraphProperty) {
                            "posY" -> "Pos Y"
                            "posZ" -> "Pos Z"
                            "scaleX" -> "Skala"
                            "rotation" -> "Rotasi"
                            "opacity" -> "Opasitas"
                            else -> "Pos X"
                        }
                        graphEditorView.propertyName = "${track.name} ($propLabel)"
                    }
                    val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
                    if (state != null) {
                        if (::viewportView.isInitialized) {
                            viewportView.selectedLayerX = state.posX.toFloat()
                            viewportView.selectedLayerY = state.posY.toFloat()
                            viewportView.invalidate()
                        }
                        if (::graphViewportView.isInitialized) {
                            graphViewportView.selectedLayerX = state.posX.toFloat()
                            graphViewportView.selectedLayerY = state.posY.toFloat()
                            graphViewportView.invalidate()
                        }
                    }
                }

                onClipSpanChanged = { track ->
                    NativeEngineBridge.nativeSetLayerSpan(currentProjectPtr, track.id, track.startFrame, track.durationFrames)
                    updateProjectDurationFromLayers()
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.totalDurationFrames = projectDurationFrames
                        graphTimelineView.invalidate()
                    }
                    renderCurrentFrame()
                }

                onTrackReordered = { track, newIndex ->
                    NativeEngineBridge.nativeReorderLayer(currentProjectPtr, track.id, newIndex)
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.invalidate()
                    }
                    renderCurrentFrame()
                }

                onDragCompleted = {
                    updateProjectDurationFromLayers()
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.totalDurationFrames = projectDurationFrames
                        graphTimelineView.invalidate()
                    }
                    renderCurrentFrame()
                    takeProjectSnapshot()
                }

                onKeyframeClicked = { kfFrame ->
                    currentFrame = kfFrame
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.currentFrame = kfFrame
                        graphTimelineView.invalidate()
                    }
                    renderCurrentFrame()
                }

                onKeyframeMoved = { track, oldFrame, newFrame ->
                    NativeEngineBridge.nativeMoveKeyframe(currentProjectPtr, track.id, oldFrame, newFrame)
                    val kfList = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id)
                    track.keyframeFrames.clear()
                    for (f in kfList) track.keyframeFrames.add(f)
                    track.keyframeFrames.sort()
                    currentFrame = newFrame
                    if (::graphTimelineView.isInitialized) {
                        graphTimelineView.currentFrame = newFrame
                        graphTimelineView.invalidate()
                    }
                    renderCurrentFrame()
                    takeProjectSnapshot()
                }
            }
            addView(timelineView)

            // 4. Action Dock with Add Layer (+)
            val actionDock = createActionDock()
            addView(actionDock)
        }
    }

    private fun createTransportBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        // Left Controls: Split & Audio Mute
        val btnSplit = createCustomButton("✂ Bagi", dp(56), dp(34)) {
            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            if (track != null) {
                val ok = NativeEngineBridge.nativeSplitLayer(currentProjectPtr, track.id, currentFrame)
                if (ok) {
                    populateTimelineTracks()
                    updateProjectDurationFromLayers()
                    renderCurrentFrame()
                    takeProjectSnapshot()
                    Toast.makeText(this, "Layer '${track.name}' dipotong di frame $currentFrame", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Playhead (Frame $currentFrame) di luar rentang aktif layer", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Pilih layer di linimasa terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }
        bar.addView(btnSplit)

        lateinit var btnMute: Button
        btnMute = createCustomButton("🔊", dp(36), dp(34)) {
            val isMuted = audioPlaybackManager?.toggleMute() ?: false
            btnMute.text = if (isMuted) "🔇" else "🔊"
            Toast.makeText(this, if (isMuted) "Audio Dimatikan" else "Audio Aktif", Toast.LENGTH_SHORT).show()
        }
        bar.addView(btnMute)

        // Center Transport: Prev Frame, Play/Pause, Next Frame
        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnPrev = createCustomButton("|<", dp(38), dp(34)) {
            currentFrame = (currentFrame - 15).coerceAtLeast(0L)
            timelineView.currentFrame = currentFrame
            renderCurrentFrame()
        }
        centerBox.addView(btnPrev)

        btnPlayPause = Button(this).apply {
            text = "▶"
            textSize = 15f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(34)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            setOnClickListener { togglePlayback() }
        }
        centerBox.addView(btnPlayPause)

        val btnNext = createCustomButton(">|", dp(38), dp(34)) {
            currentFrame = (currentFrame + 15).coerceAtMost(projectDurationFrames)
            timelineView.currentFrame = currentFrame
            renderCurrentFrame()
        }
        centerBox.addView(btnNext)
        bar.addView(centerBox)

        // Timecode Readout
        timecodeText = TextView(this).apply {
            text = "00:00 / 05:00"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
        }
        bar.addView(timecodeText)

        return bar
    }

    private fun createActionDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }

        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tools = listOf("Keyframe", "Edit Bentuk", "Transform", "Transisi", "Blend", "Efek", "Audio Mixer", "Duplikat", "Hapus", "Naikkan", "Turunkan")
        for (t in tools) {
            val btn = Button(this).apply {
                text = t
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(12), dp(4), dp(12), dp(4))
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(34)
                ).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    when (t) {
                        "Keyframe" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                                ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                            if (track != null) {
                                selectWorkspaceTab(1)
                            } else {
                                Toast.makeText(this@MainActivity, "Tambahkan layer terlebih dahulu", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Transisi" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                val inTransData = NativeEngineBridge.nativeGetLayerTransition(currentProjectPtr, track.id, true)
                                val outTransData = NativeEngineBridge.nativeGetLayerTransition(currentProjectPtr, track.id, false)
                                val curInType = inTransData.getOrElse(0) { 0L }.toInt()
                                val curInDur = inTransData.getOrElse(1) { 0L }
                                val curOutType = outTransData.getOrElse(0) { 0L }.toInt()
                                val curOutDur = outTransData.getOrElse(1) { 0L }

                                TransitionDialog(
                                    context = this@MainActivity,
                                    layerName = track.name,
                                    inType = curInType,
                                    inDurationFrames = curInDur,
                                    outType = curOutType,
                                    outDurationFrames = curOutDur,
                                    onTransitionUpdated = { inType, inDur, outType, outDur ->
                                        NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, track.id, true, inType, inDur)
                                        NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, track.id, false, outType, outDur)
                                        track.inTransition = inType
                                        track.inTransitionDuration = inDur
                                        track.outTransition = outType
                                        track.outTransitionDuration = outDur
                                        timelineView.invalidate()
                                        renderCurrentFrame()
                                        takeProjectSnapshot()
                                    }
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Blend" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                val curBlend = NativeEngineBridge.nativeGetLayerBlendMode(currentProjectPtr, track.id)
                                BlendModeDialog(
                                    context = this@MainActivity,
                                    layerName = track.name,
                                    currentMode = curBlend,
                                    onBlendModeSelected = { mode ->
                                        NativeEngineBridge.nativeSetLayerBlendMode(currentProjectPtr, track.id, mode)
                                        track.blendMode = mode
                                        timelineView.invalidate()
                                        renderCurrentFrame()
                                        takeProjectSnapshot()
                                    }
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Transform" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
                                val curX = state?.posX ?: 0.0
                                val curY = state?.posY ?: 0.0
                                val curScaleX = state?.scaleX ?: 1.0
                                val curScaleY = state?.scaleY ?: 1.0
                                val curRot = state?.rotation ?: 0.0
                                val curOp = state?.opacity ?: 1.0

                                TransformInspectorDialog(
                                    context = this@MainActivity,
                                    layerName = track.name,
                                    currentFrame = currentFrame,
                                    initialPosX = curX,
                                    initialPosY = curY,
                                    initialScaleX = curScaleX,
                                    initialScaleY = curScaleY,
                                    initialRotation = curRot,
                                    initialOpacity = curOp,
                                    onTransformChanged = { px, py, sx, sy, r, op, recordKf ->
                                        if (recordKf) {
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posX", currentFrame, px, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posY", currentFrame, py, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleX", currentFrame, sx, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleY", currentFrame, sy, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "rotation", currentFrame, r, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "opacity", currentFrame, op, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                             if (!track.keyframeFrames.contains(currentFrame)) {
                                                 track.keyframeFrames.add(currentFrame)
                                                 track.keyframeFrames.sort()
                                                 timelineView.invalidate()
                                             }
                                        }
                                        viewportView.selectedLayerX = px.toFloat()
                                        viewportView.selectedLayerY = py.toFloat()
                                        viewportView.selectedLayerScaleX = sx.toFloat()
                                        viewportView.selectedLayerScaleY = sy.toFloat()
                                        viewportView.selectedLayerRotation = r.toFloat()
                                        renderCurrentFrame()
                                    }
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Edit Bentuk" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                ShapeStyleDialog(
                                    context = this@MainActivity,
                                    layerId = track.id,
                                    layerName = track.name,
                                    targetLayerType = track.type,
                                    initialColor = Color.parseColor("#6366F1"),
                                    initialWidthOrSize = if (track.type == "TEXT") 54.0 else 240.0,
                                    initialTextContent = track.name,
                                    onStyleUpdated = { color, size, text ->
                                        if (track.type == "SHAPE") {
                                            NativeEngineBridge.nativeUpdateShapeLayer(currentProjectPtr, track.id, color, size, size)
                                            viewportView.selectedLayerWidth = size.toFloat()
                                            viewportView.selectedLayerHeight = size.toFloat()
                                            if (::graphViewportView.isInitialized) {
                                                graphViewportView.selectedLayerWidth = size.toFloat()
                                                graphViewportView.selectedLayerHeight = size.toFloat()
                                            }
                                        } else if (track.type == "TEXT") {
                                            NativeEngineBridge.nativeUpdateTextLayer(currentProjectPtr, track.id, text, color, size)
                                            val tw = (text.length * size * 0.9).toFloat().coerceAtLeast(100f)
                                            val th = (size * 1.2).toFloat().coerceAtLeast(40f)
                                            viewportView.selectedLayerWidth = tw
                                            viewportView.selectedLayerHeight = th
                                            if (::graphViewportView.isInitialized) {
                                                graphViewportView.selectedLayerWidth = tw
                                                graphViewportView.selectedLayerHeight = th
                                            }
                                        }
                                        renderCurrentFrame()
                                        takeProjectSnapshot()
                                    },
                                    onApplyAnimationPreset = { presetName ->
                                        val sFrame = track.startFrame
                                        when (presetName) {
                                            "POP_ZOOM" -> {
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleX", sFrame, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleX", sFrame + 24, 1.2, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleX", sFrame + 36, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleY", sFrame, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleY", sFrame + 24, 1.2, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleY", sFrame + 36, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                                                listOf(sFrame, sFrame + 24, sFrame + 36).forEach { f ->
                                                    if (!track.keyframeFrames.contains(f)) track.keyframeFrames.add(f)
                                                }
                                            }
                                            "SLIDE_UP" -> {
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posY", sFrame, 400.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.22, 1.0, 0.36, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posY", sFrame + 30, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                                                listOf(sFrame, sFrame + 30).forEach { f ->
                                                    if (!track.keyframeFrames.contains(f)) track.keyframeFrames.add(f)
                                                }
                                            }
                                            "FADE_IN" -> {
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "opacity", sFrame, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "opacity", sFrame + 25, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                                                listOf(sFrame, sFrame + 25).forEach { f ->
                                                    if (!track.keyframeFrames.contains(f)) track.keyframeFrames.add(f)
                                                }
                                            }
                                        }
                                        track.keyframeFrames.sort()
                                        timelineView.invalidate()
                                        graphTimelineView?.invalidate()
                                        renderCurrentFrame()
                                        takeProjectSnapshot()
                                    }
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Efek" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                                ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                            val curMbEnabled = if (track != null) {
                                NativeEngineBridge.nativeGetLayerMotionBlurEnabled(currentProjectPtr, track.id)
                            } else false
                            val curMbStrength = if (track != null) {
                                NativeEngineBridge.nativeGetLayerMotionBlurStrength(currentProjectPtr, track.id).toFloat()
                            } else 1.0f

                            ShaderEffectsDialog(
                                context = this@MainActivity,
                                brightness = effectBrightness,
                                contrast = effectContrast,
                                saturation = effectSaturation,
                                vignette = effectVignette,
                                filterMode = effectFilterMode,
                                selectedLayerName = track?.name,
                                motionBlurEnabled = curMbEnabled,
                                motionBlurStrength = curMbStrength,
                                onMotionBlurChanged = { enabled, strength ->
                                    if (track != null) {
                                        NativeEngineBridge.nativeSetLayerMotionBlur(currentProjectPtr, track.id, enabled, strength.toDouble())
                                        track.motionBlurEnabled = enabled
                                        track.motionBlurStrength = strength
                                        timelineView.invalidate()
                                        if (::graphTimelineView.isInitialized) {
                                            graphTimelineView.invalidate()
                                        }
                                        renderCurrentFrame()
                                        takeProjectSnapshot()
                                    }
                                },
                                onEffectsChanged = { b, c, s, v, mode ->
                                    effectBrightness = b
                                    effectContrast = c
                                    effectSaturation = s
                                    effectVignette = v
                                    effectFilterMode = mode
                                    if (compositorPtr != 0L) {
                                        NativeEngineBridge.nativeSetGpuShaderEffects(compositorPtr, b, c, s, v, mode)
                                        renderCurrentFrame()
                                    }
                                }
                            ).show()
                        }
                        "Audio Mixer" -> {
                            val curVol = audioPlaybackManager?.getVolume() ?: 1.0f
                            val isMuted = audioPlaybackManager?.isMuted() ?: false
                            val track = timelineView.tracks.find { it.type == "AUDIO" } ?: timelineView.tracks.firstOrNull()
                            AudioMixerDialog(
                                context = this@MainActivity,
                                volume = curVol,
                                isMuted = isMuted,
                                audioTrackName = track?.name ?: "Master Audio",
                                onVolumeChanged = { vol -> audioPlaybackManager?.setVolume(vol) },
                                onMuteToggled = { muted -> audioPlaybackManager?.toggleMute() }
                            ).show()
                        }
                        "Duplikat" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                if (NativeEngineBridge.nativeDuplicateLayer(currentProjectPtr, track.id)) {
                                    populateTimelineTracks()
                                    renderCurrentFrame()
                                    takeProjectSnapshot()
                                    Toast.makeText(this@MainActivity, "Layer '${track.name}' diduplikat", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Hapus" -> {
                            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            if (track != null) {
                                if (NativeEngineBridge.nativeRemoveLayer(currentProjectPtr, track.id)) {
                                    populateTimelineTracks()
                                    renderCurrentFrame()
                                    takeProjectSnapshot()
                                    Toast.makeText(this@MainActivity, "Layer '${track.name}' dihapus", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Pilih layer di linimasa", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Naikkan" -> {
                            val selIdx = timelineView.selectedTrackIndex
                            if (selIdx > 0 && selIdx < timelineView.tracks.size) {
                                val track = timelineView.tracks[selIdx]
                                NativeEngineBridge.nativeReorderLayer(currentProjectPtr, track.id, selIdx - 1)
                                populateTimelineTracks()
                                timelineView.selectedTrackIndex = selIdx - 1
                                renderCurrentFrame()
                                takeProjectSnapshot()
                                Toast.makeText(this@MainActivity, "Layer dinaikkan", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Turunkan" -> {
                            val selIdx = timelineView.selectedTrackIndex
                            if (selIdx >= 0 && selIdx < timelineView.tracks.size - 1) {
                                val track = timelineView.tracks[selIdx]
                                NativeEngineBridge.nativeReorderLayer(currentProjectPtr, track.id, selIdx + 1)
                                populateTimelineTracks()
                                timelineView.selectedTrackIndex = selIdx + 1
                                renderCurrentFrame()
                                takeProjectSnapshot()
                                Toast.makeText(this@MainActivity, "Layer diturunkan", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Alat: $t", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            toolRow.addView(btn)
        }

        // Add Keyframe Diamond Button (+ Kunci)
        val btnAddKf = Button(this).apply {
            text = "+ Kunci"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(12), dp(4), dp(12), dp(4))
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                if (track != null) {
                    val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
                    val curVal = state?.posX ?: 0.0
                    NativeEngineBridge.addKeyframe(
                        currentProjectPtr, track.id, "posX",
                        NativeEngineBridge.KeyframeData(currentFrame, curVal, NativeEngineBridge.InterpolationMode.BEZIER, 0.42, 0.0, 0.58, 1.0)
                    )
                    if (!track.keyframeFrames.contains(currentFrame)) {
                        track.keyframeFrames.add(currentFrame)
                        track.keyframeFrames.sort()
                    }
                    timelineView.invalidate()
                    takeProjectSnapshot()
                    Toast.makeText(this@MainActivity, "Kunci ditambahkan di Frame $currentFrame", Toast.LENGTH_SHORT).show()
                }
            }
        }
        toolRow.addView(btnAddKf)
        scroll.addView(toolRow)
        dock.addView(scroll)

        // Floating Add Layer (+) Button
        val btnAddLayer = Button(this).apply {
            text = "+"
            textSize = 20f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(34)).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                AddElementDialog(
                    this@MainActivity,
                    onAddShape = { shapeType, name ->
                        val id = "shape_${System.currentTimeMillis() % 1000}"
                        NativeEngineBridge.nativeAddShapeLayer(
                            currentProjectPtr, id, name, shapeType, 0, projectDurationFrames, 0xFFFFFFFF.toInt(), 250.0, 250.0
                        )
                        populateTimelineTracks()
                        renderCurrentFrame()
                        takeProjectSnapshot()
                    },
                    onAddMedia = { path, isVid, name ->
                        val id = "media_${System.currentTimeMillis() % 1000}"
                        NativeEngineBridge.nativeAddMediaLayer(
                            currentProjectPtr, id, name, path, isVid, 0, projectDurationFrames
                        )
                        populateTimelineTracks()
                        renderCurrentFrame()
                        takeProjectSnapshot()
                    },
                    onAddAudio = { path, name ->
                        val id = "audio_${System.currentTimeMillis() % 1000}"
                        currentAudioFilePath = path
                        audioPlaybackManager?.loadAudio(path)
                        NativeEngineBridge.nativeAddAudioLayer(
                            currentProjectPtr, id, name, path, 0, projectDurationFrames
                        )
                        populateTimelineTracks()
                        takeProjectSnapshot()
                    },
                    onAddText = { txt, name ->
                        val id = "text_${System.currentTimeMillis() % 1000}"
                        NativeEngineBridge.nativeAddTextLayer(
                            currentProjectPtr, id, name, txt, 0, projectDurationFrames, 0xFFFFFFFF.toInt(), 54.0
                        )
                        populateTimelineTracks()
                        renderCurrentFrame()
                        takeProjectSnapshot()
                        Toast.makeText(this@MainActivity, "Layer Teks Ditambahkan", Toast.LENGTH_SHORT).show()
                    },
                    onPickMedia = { isVid ->
                        openMediaPicker(isVid)
                    },
                    onPickAudio = {
                        openAudioPicker()
                    },
                    onAddVfxPreset = { presetId, presetName ->
                        when (presetId) {
                            0 -> { // Lower Third
                                val bgId = "shape_${System.currentTimeMillis() % 1000}"
                                NativeEngineBridge.nativeAddShapeLayer(
                                    currentProjectPtr, bgId, "Lower Third BG",
                                    NativeEngineBridge.ShapeType.RECTANGLE.value,
                                    0, projectDurationFrames,
                                    Color.parseColor("#1E1B4B"), 550.0, 100.0
                                )
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, bgId, true, 2, 30) // SLIDE_LEFT
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, bgId, false, 2, 30)

                                val txtId = "text_${(System.currentTimeMillis() + 1) % 1000}"
                                NativeEngineBridge.nativeAddTextLayer(
                                    currentProjectPtr, txtId, "TITLE • SUBTITLE",
                                    "MOTION LAB • STUDIO VFX",
                                    0, projectDurationFrames,
                                    Color.parseColor("#818CF8"), 32.0
                                )
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, txtId, true, 1, 30) // FADE
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, txtId, false, 1, 30)
                            }
                            1 -> { // Pulse Glow
                                val glowId = "shape_${System.currentTimeMillis() % 1000}"
                                NativeEngineBridge.nativeAddShapeLayer(
                                    currentProjectPtr, glowId, "Pulsing Glow",
                                    NativeEngineBridge.ShapeType.CIRCLE.value,
                                    0, projectDurationFrames,
                                    Color.parseColor("#6366F1"), 200.0, 200.0
                                )
                                NativeEngineBridge.nativeSetLayerBlendMode(currentProjectPtr, glowId, 4) // ADD
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleX", 0, 0.75, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleY", 0, 0.75, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleX", 30, 1.35, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleY", 30, 1.35, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleX", 60, 0.75, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                                NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, glowId, "scaleY", 60, 0.75, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                            }
                            2 -> { // Intro Title
                                val titleId = "text_${System.currentTimeMillis() % 1000}"
                                NativeEngineBridge.nativeAddTextLayer(
                                    currentProjectPtr, titleId, "Intro Title",
                                    "CREATIVE MOTION",
                                    0, projectDurationFrames,
                                    Color.parseColor("#FACC15"), 54.0
                                )
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, titleId, true, 6, 36) // ZOOM
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, titleId, false, 1, 24) // FADE
                            }
                            3 -> { // Spin Badge
                                val badgeId = "shape_${System.currentTimeMillis() % 1000}"
                                NativeEngineBridge.nativeAddShapeLayer(
                                    currentProjectPtr, badgeId, "Spin Badge",
                                    NativeEngineBridge.ShapeType.STAR.value,
                                    0, projectDurationFrames,
                                    Color.parseColor("#EC4899"), 220.0, 220.0
                                )
                                NativeEngineBridge.nativeSetLayerBlendMode(currentProjectPtr, badgeId, 2) // SCREEN
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, badgeId, true, 7, 45) // SPIN
                                NativeEngineBridge.nativeSetLayerTransition(currentProjectPtr, badgeId, false, 6, 30) // ZOOM
                            }
                        }
                        populateTimelineTracks()
                        renderCurrentFrame()
                        takeProjectSnapshot()
                        Toast.makeText(this@MainActivity, "Preset VFX '$presetName' Diterapkan", Toast.LENGTH_SHORT).show()
                    }
                ).show()
            }
        }
        dock.addView(btnAddLayer)

        return dock
    }

    private fun createGraphWorkspace(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)

            // 1. Live Preview Canvas (Upper Section - mirrors Editor Workspace preview)
            graphViewportView = CanvasViewportView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1.15f
                )
                updateProjectAspect(projectWidth, projectHeight)
                onLayerPositionChanged = { newX, newY ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                        ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "posX", currentFrame, newX.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "posY", currentFrame, newY.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                        updateGraphWorkspaceControls()
                    }
                }
                onLayerScaleChanged = { sx, sy ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                        ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "scaleX", currentFrame, sx.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "scaleY", currentFrame, sy.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                        updateGraphWorkspaceControls()
                    }
                }
                onLayerRotationChanged = { rot ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                        ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                    if (track != null) {
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, "rotation", currentFrame, rot.toDouble(),
                            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
                        )
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                        updateGraphWorkspaceControls()
                    }
                }
            }
            addView(graphViewportView)

            // 2. Mini Transport Bar
            val transportRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            graphBtnPlayPause = Button(this@MainActivity).apply {
                text = "▶"
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(32)).apply { marginEnd = dp(6) }
                setOnClickListener {
                    togglePlayback()
                    text = if (isPlaying) "❚❚" else "▶"
                    btnPlayPause.text = text
                }
            }
            transportRow.addView(graphBtnPlayPause)

            graphTimecodeText = TextView(this@MainActivity).apply {
                text = "00:00 / 00:05"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(MotionTheme.TEXT_SECONDARY)
                setPadding(0, 0, dp(6), 0)
            }
            transportRow.addView(graphTimecodeText)

            graphSeekSlider = SeekBar(this@MainActivity).apply {
                max = projectDurationFrames.toInt().coerceAtLeast(1)
                progress = currentFrame.toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        if (fromUser) {
                            currentFrame = prog.toLong().coerceIn(0L, projectDurationFrames)
                            audioPlaybackManager?.seekTo(currentFrame, projectFps)
                            renderCurrentFrame()
                            updateGraphWorkspaceControls()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            transportRow.addView(graphSeekSlider)

            // Prev & Next Keyframe Jump Buttons
            val btnPrevKf = Button(this@MainActivity).apply {
                text = "|<"
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(4) }
                setOnClickListener { jumpToAdjacentKeyframe(false) }
            }
            transportRow.addView(btnPrevKf)

            val btnNextKf = Button(this@MainActivity).apply {
                text = ">|"
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(4) }
                setOnClickListener { jumpToAdjacentKeyframe(true) }
            }
            transportRow.addView(btnNextKf)
            addView(transportRow)

            // 3. Middle Section: Timeline Layers Deck (as in Image 2)
            graphTimelineView = TimelineDeckView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(130)
                )
                totalDurationFrames = projectDurationFrames
                currentFrame = this@MainActivity.currentFrame
                tracks.clear()
                tracks.addAll(timelineView.tracks)
                selectedTrackIndex = timelineView.selectedTrackIndex

                onFrameScrubbed = { f ->
                    currentFrame = f
                    timelineView.currentFrame = f
                    timelineView.invalidate()
                    audioPlaybackManager?.seekTo(f, projectFps)
                    renderCurrentFrame()
                }

                onTrackSelected = { idx, track ->
                    timelineView.selectedTrackIndex = idx
                    timelineView.invalidate()
                    if (::viewportView.isInitialized) {
                        viewportView.selectedLayerName = track.name
                    }
                    if (::graphViewportView.isInitialized) {
                        graphViewportView.selectedLayerName = track.name
                    }
                    if (::graphEditorView.isInitialized) {
                        val propLabel = when (activeGraphProperty) {
                            "posY" -> "Pos Y"
                            "posZ" -> "Pos Z"
                            "scaleX" -> "Skala"
                            "rotation" -> "Rotasi"
                            "opacity" -> "Opasitas"
                            else -> "Pos X"
                        }
                        graphEditorView.propertyName = "${track.name} ($propLabel)"
                    }
                    val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
                    if (state != null) {
                        if (::viewportView.isInitialized) {
                            viewportView.selectedLayerX = state.posX.toFloat()
                            viewportView.selectedLayerY = state.posY.toFloat()
                            viewportView.invalidate()
                        }
                        if (::graphViewportView.isInitialized) {
                            graphViewportView.selectedLayerX = state.posX.toFloat()
                            graphViewportView.selectedLayerY = state.posY.toFloat()
                            graphViewportView.invalidate()
                        }
                    }
                    renderCurrentFrame()
                }

                onKeyframeClicked = { kfFrame ->
                    currentFrame = kfFrame
                    timelineView.currentFrame = kfFrame
                    timelineView.invalidate()
                    renderCurrentFrame()
                }

                onKeyframeMoved = { track, oldFrame, newFrame ->
                    NativeEngineBridge.nativeMoveKeyframe(currentProjectPtr, track.id, oldFrame, newFrame)
                    val kfList = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id)
                    track.keyframeFrames.clear()
                    for (f in kfList) track.keyframeFrames.add(f)
                    track.keyframeFrames.sort()
                    currentFrame = newFrame
                    timelineView.currentFrame = newFrame
                    timelineView.invalidate()
                    renderCurrentFrame()
                    takeProjectSnapshot()
                }

                onClipSpanChanged = { track ->
                    NativeEngineBridge.nativeSetLayerSpan(currentProjectPtr, track.id, track.startFrame, track.durationFrames)
                    updateProjectDurationFromLayers()
                    timelineView.invalidate()
                    renderCurrentFrame()
                }

                onTrackReordered = { track, newIndex ->
                    NativeEngineBridge.nativeReorderLayer(currentProjectPtr, track.id, newIndex)
                    timelineView.invalidate()
                    renderCurrentFrame()
                }

                onDragCompleted = {
                    updateProjectDurationFromLayers()
                    timelineView.invalidate()
                    renderCurrentFrame()
                    takeProjectSnapshot()
                }
            }
            addView(graphTimelineView)

            // 4. Scrollable Controls Section
            val scrollControls = ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1.2f
                )
                isVerticalScrollBarEnabled = true
            }

            val controlsContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(16))
            }

            // 3a. Dedicated Full-Width Keyframe Status Banner Card
            val kfStatusCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }

            graphKeyframeLabel = TextView(this@MainActivity).apply {
                text = "◆ KEYFRAME AKTIF • Frame 0 (0.00s)"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#4ADE80"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            kfStatusCard.addView(graphKeyframeLabel)
            controlsContainer.addView(kfStatusCard)

            // 3b. Keyframe Actions Full-Width 4-Button Row
            val kfActionRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(34)
                ).apply { bottomMargin = dp(8) }
            }

            val btnAddKf = Button(this@MainActivity).apply {
                text = "+ Tambah"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(Color.parseColor("#2563EB"))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
                setOnClickListener { addKeyframeAtCurrentFrame() }
            }
            kfActionRow.addView(btnAddKf)

            val btnDelKf = Button(this@MainActivity).apply {
                text = "🗑 Hapus"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(Color.parseColor("#DC2626"))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
                setOnClickListener { deleteKeyframeAtCurrentFrame() }
            }
            kfActionRow.addView(btnDelKf)

            val btnCopyKf = Button(this@MainActivity).apply {
                text = "📋 Salin"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
                setOnClickListener { copyKeyframeAtCurrentFrame() }
            }
            kfActionRow.addView(btnCopyKf)

            val btnPasteKf = Button(this@MainActivity).apply {
                text = "📌 Tempel"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(Color.parseColor("#7C3AED"))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
                setOnClickListener { pasteKeyframeAtCurrentFrame() }
            }
            kfActionRow.addView(btnPasteKf)
            controlsContainer.addView(kfActionRow)

            // 3c. Keyframe Time Relocation Row (Slider & Nudge buttons: -1s, -0.1s, +0.1s, +1s)
            val kfTimeRelocateRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            val tvPindah = TextView(this@MainActivity).apply {
                text = "Geser KF:"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(MotionTheme.TEXT_MUTED)
                setPadding(0, 0, dp(4), 0)
            }
            kfTimeRelocateRow.addView(tvPindah)

            val btnNudgeM1 = createMiniButton("-1.0s") { shiftCurrentKeyframe(-60L) }
            val btnNudgeM01 = createMiniButton("-0.1s") { shiftCurrentKeyframe(-6L) }
            val btnNudgeP01 = createMiniButton("+0.1s") { shiftCurrentKeyframe(6L) }
            val btnNudgeP1 = createMiniButton("+1.0s") { shiftCurrentKeyframe(60L) }

            kfTimeRelocateRow.addView(btnNudgeM1)
            kfTimeRelocateRow.addView(btnNudgeM01)

            graphTimeSeekBar = SeekBar(this@MainActivity).apply {
                max = projectDurationFrames.toInt()
                progress = currentFrame.toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        if (fromUser) {
                            relocateCurrentKeyframeTo(prog.toLong())
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            kfTimeRelocateRow.addView(graphTimeSeekBar)

            kfTimeRelocateRow.addView(btnNudgeP01)
            kfTimeRelocateRow.addView(btnNudgeP1)
            controlsContainer.addView(kfTimeRelocateRow)

            // 3d. Property Selector Row
            val propRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }

            val propButtons = mutableListOf<Button>()
            val propList = listOf(
                Pair("Pos X", "posX"),
                Pair("Pos Y", "posY"),
                Pair("Pos Z", "posZ"),
                Pair("Skala", "scaleX"),
                Pair("Rotasi", "rotation"),
                Pair("Opasitas", "opacity")
            )
            for (p in propList) {
                val btn = Button(this@MainActivity).apply {
                    text = p.first
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(3) }
                    val isDef = (p.second == activeGraphProperty)
                    setTextColor(if (isDef) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                    setBackgroundColor(if (isDef) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                    setOnClickListener {
                        activeGraphProperty = p.second
                        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                        val trackName = track?.name ?: "Layer"
                        graphEditorView.propertyName = "$trackName (${p.first})"
                        for (other in propButtons) {
                            val sel = (other.text == p.first)
                            other.setTextColor(if (sel) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                            other.setBackgroundColor(if (sel) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                        }
                        updateGraphWorkspaceControls()
                    }
                }
                propButtons.add(btn)
                propRow.addView(btn)
            }
            controlsContainer.addView(propRow)

            // 3e. Property Value Readout & Value Adjustment Slider
            val propValueRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            graphPropertyValueReadout = TextView(this@MainActivity).apply {
                text = "Posisi X: 0.0 px"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(MotionTheme.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            propValueRow.addView(graphPropertyValueReadout)

            graphPropertyValueSeekBar = SeekBar(this@MainActivity).apply {
                max = 2000
                progress = 1000
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        if (fromUser) {
                            onGraphPropertyValueChanged(prog)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            propValueRow.addView(graphPropertyValueSeekBar)
            controlsContainer.addView(propValueRow)

            // 3f. Curve Editor Canvas View (1:1 Aspect Ratio)
            graphEditorView = GraphEditorView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(260)
                ).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(6)
                }
                onCurveChanged = { ox, oy, ix, iy ->
                    val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                        ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
                    if (track != null) {
                        val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
                        val curVal = when (activeGraphProperty) {
                            "posY" -> state?.posY ?: 0.0
                            "posZ" -> 0.0
                            "scaleX" -> state?.scaleX ?: 1.0
                            "rotation" -> state?.rotation ?: 0.0
                            "opacity" -> state?.opacity ?: 1.0
                            else -> state?.posX ?: 0.0
                        }
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(
                            currentProjectPtr, track.id, activeGraphProperty, currentFrame, curVal,
                            NativeEngineBridge.InterpolationMode.BEZIER.value, ox, oy, ix, iy
                        )
                        if (activeGraphProperty == "scaleX") {
                            NativeEngineBridge.nativeAddOrUpdateKeyframe(
                                currentProjectPtr, track.id, "scaleY", currentFrame, state?.scaleY ?: 1.0,
                                NativeEngineBridge.InterpolationMode.BEZIER.value, ox, oy, ix, iy
                            )
                        }
                        if (!track.keyframeFrames.contains(currentFrame)) {
                            track.keyframeFrames.add(currentFrame)
                            track.keyframeFrames.sort()
                        }
                        timelineView.invalidate()
                        renderCurrentFrame()
                    }
                }
            }
            controlsContainer.addView(graphEditorView)

            // 3g. Easing Presets Row
            val presetRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(4)
                }
            }

            val presets = listOf(
                Pair("LINEAR", "LIN"),
                Pair("EASE_IN", "IN"),
                Pair("EASE_OUT", "OUT"),
                Pair("EASE_IN_OUT", "IN-OUT"),
                Pair("BOUNCE", "POP"),
                Pair("FAST_SNAP", "SNAP"),
                Pair("HOLD", "HOLD")
            )
            for (pr in presets) {
                val btn = Button(this@MainActivity).apply {
                    text = pr.second
                    textSize = 10f
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(3) }
                    setTextColor(MotionTheme.TEXT_PRIMARY)
                    setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                    setOnClickListener {
                        graphEditorView.applyPreset(pr.first)
                    }
                }
                presetRow.addView(btn)
            }
            controlsContainer.addView(presetRow)

            scrollControls.addView(controlsContainer)
            addView(scrollControls)
        }
    }

    private fun createMiniButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 9f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(4), 0, dp(4), 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun jumpToAdjacentKeyframe(next: Boolean) {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
            ?: return
        val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id).sorted()
        if (frames.isEmpty()) {
            Toast.makeText(this, "Belum ada keyframe pada layer ini", Toast.LENGTH_SHORT).show()
            return
        }
        val target = if (next) {
            frames.firstOrNull { it > currentFrame } ?: frames.last()
        } else {
            frames.lastOrNull { it < currentFrame } ?: frames.first()
        }
        currentFrame = target
        renderCurrentFrame()
        updateGraphWorkspaceControls()
    }

    private fun shiftCurrentKeyframe(deltaFrames: Long) {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
            ?: return
        val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id).sorted()
        val exact = frames.find { it == currentFrame } ?: frames.minByOrNull { kotlin.math.abs(it - currentFrame) }
        if (exact != null && kotlin.math.abs(exact - currentFrame) <= 5L) {
            val targetFrame = (exact + deltaFrames).coerceIn(0L, projectDurationFrames)
            if (targetFrame != exact) {
                NativeEngineBridge.nativeMoveKeyframe(currentProjectPtr, track.id, exact, targetFrame)
                val updated = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id)
                track.keyframeFrames.clear()
                for (f in updated) track.keyframeFrames.add(f)
                track.keyframeFrames.sort()
                currentFrame = targetFrame
                timelineView.invalidate()
                renderCurrentFrame()
                updateGraphWorkspaceControls()
                takeProjectSnapshot()
                Toast.makeText(this, "Keyframe dipindah ke frame $targetFrame (${String.format("%.2fs", targetFrame / projectFps)})", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Tidak ada keyframe di posisi playhead saat ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun relocateCurrentKeyframeTo(targetFrame: Long) {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
            ?: return
        val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id).sorted()
        val exact = frames.find { it == currentFrame } ?: frames.minByOrNull { kotlin.math.abs(it - currentFrame) }
        if (exact != null && kotlin.math.abs(exact - currentFrame) <= 5L) {
            val boundedTarget = targetFrame.coerceIn(0L, projectDurationFrames)
            if (boundedTarget != exact) {
                NativeEngineBridge.nativeMoveKeyframe(currentProjectPtr, track.id, exact, boundedTarget)
                val updated = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id)
                track.keyframeFrames.clear()
                for (f in updated) track.keyframeFrames.add(f)
                track.keyframeFrames.sort()
                currentFrame = boundedTarget
                timelineView.invalidate()
                renderCurrentFrame()
                updateGraphWorkspaceControls()
                takeProjectSnapshot()
            }
        }
    }

    private fun addKeyframeAtCurrentFrame() {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        if (track == null) {
            Toast.makeText(this, "Pilih layer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
        val curVal = when (activeGraphProperty) {
            "posY" -> state?.posY ?: 0.0
            "posZ" -> 0.0
            "scaleX" -> state?.scaleX ?: 1.0
            "rotation" -> state?.rotation ?: 0.0
            "opacity" -> state?.opacity ?: 1.0
            else -> state?.posX ?: 0.0
        }
        NativeEngineBridge.nativeAddOrUpdateKeyframe(
            currentProjectPtr, track.id, activeGraphProperty, currentFrame, curVal,
            NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
        )
        if (activeGraphProperty == "scaleX") {
            NativeEngineBridge.nativeAddOrUpdateKeyframe(
                currentProjectPtr, track.id, "scaleY", currentFrame, state?.scaleY ?: 1.0,
                NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0
            )
        }
        if (!track.keyframeFrames.contains(currentFrame)) {
            track.keyframeFrames.add(currentFrame)
            track.keyframeFrames.sort()
        }
        timelineView.invalidate()
        renderCurrentFrame()
        updateGraphWorkspaceControls()
        takeProjectSnapshot()
        Toast.makeText(this, "+ Keyframe ditambahkan di frame $currentFrame", Toast.LENGTH_SHORT).show()
    }

    private fun deleteKeyframeAtCurrentFrame() {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        if (track == null) {
            Toast.makeText(this, "Pilih layer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id).sorted()
        val exact = frames.find { it == currentFrame } ?: frames.minByOrNull { kotlin.math.abs(it - currentFrame) }
        if (exact != null && kotlin.math.abs(exact - currentFrame) <= 5L) {
            NativeEngineBridge.nativeDeleteKeyframeAtFrame(currentProjectPtr, track.id, exact)
            track.keyframeFrames.remove(exact)
            timelineView.invalidate()
            renderCurrentFrame()
            updateGraphWorkspaceControls()
            Toast.makeText(this, "Keyframe di frame $exact dihapus", Toast.LENGTH_SHORT).show()
            takeProjectSnapshot()
        } else {
            Toast.makeText(this, "Tidak ada keyframe di frame ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyKeyframeAtCurrentFrame() {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        if (track == null) {
            Toast.makeText(this, "Pilih layer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        copiedTransformState = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
        Toast.makeText(this, "Keyframe di frame $currentFrame disalin", Toast.LENGTH_SHORT).show()
    }

    private fun pasteKeyframeAtCurrentFrame() {
        val state = copiedTransformState
        if (state == null) {
            Toast.makeText(this, "Salin keyframe terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        if (track == null) {
            Toast.makeText(this, "Pilih layer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posX", currentFrame, state.posX, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "posY", currentFrame, state.posY, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleX", currentFrame, state.scaleX, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "scaleY", currentFrame, state.scaleY, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "rotation", currentFrame, state.rotation, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, track.id, "opacity", currentFrame, state.opacity, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
        if (!track.keyframeFrames.contains(currentFrame)) {
            track.keyframeFrames.add(currentFrame)
            track.keyframeFrames.sort()
        }
        timelineView.invalidate()
        if (::graphTimelineView.isInitialized) {
            graphTimelineView.invalidate()
        }
        renderCurrentFrame()
        updateGraphWorkspaceControls()
        takeProjectSnapshot()
        Toast.makeText(this, "Keyframe ditempel di frame $currentFrame", Toast.LENGTH_SHORT).show()
    }

    private fun takeProjectSnapshot() {
        if (currentProjectPtr != 0L) {
            try {
                val json = NativeEngineBridge.nativeGetProjectJson(currentProjectPtr)
                historyManager.recordSnapshot(json)
                updateUndoRedoButtonStates()
            } catch (_: Exception) {}
        }
    }

    private fun performUndo() {
        val json = historyManager.undo()
        if (json != null) {
            restoreProjectFromJson(json)
            Toast.makeText(this, "↶ Undo Berhasil", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tidak ada aksi untuk di-undo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRedo() {
        val json = historyManager.redo()
        if (json != null) {
            restoreProjectFromJson(json)
            Toast.makeText(this, "↷ Redo Berhasil", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tidak ada aksi untuk di-redo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreProjectFromJson(json: String) {
        try {
            val loadedPtr = NativeEngineBridge.nativeLoadProjectJson(json)
            if (loadedPtr != 0L) {
                if (currentProjectPtr != 0L) {
                    NativeEngineBridge.nativeDestroyProject(currentProjectPtr)
                }
                currentProjectPtr = loadedPtr
                populateTimelineTracks()
                viewportView.updateProjectAspect(projectWidth, projectHeight)
                viewportView.activeSurface?.let { surf ->
                    if (compositorPtr != 0L) {
                        NativeEngineBridge.nativeInitializeGpuCompositor(compositorPtr, surf, projectWidth, projectHeight)
                        val r = viewportView.getCanvasRect()
                        NativeEngineBridge.nativeUpdateGpuViewport(compositorPtr, r.left, r.top, r.width(), r.height())
                        NativeEngineBridge.nativeSetGpuShaderEffects(compositorPtr, effectBrightness, effectContrast, effectSaturation, effectVignette, effectFilterMode)
                    }
                }
                if (::graphTimelineView.isInitialized) {
                    graphTimelineView.totalDurationFrames = projectDurationFrames
                    graphTimelineView.tracks.clear()
                    graphTimelineView.tracks.addAll(timelineView.tracks)
                    graphTimelineView.selectedTrackIndex = timelineView.selectedTrackIndex
                    graphTimelineView.invalidate()
                }
                renderCurrentFrame()
                updateUndoRedoButtonStates()
            }
        } catch (_: Exception) {}
    }

    private fun updateUndoRedoButtonStates() {
        if (::btnUndo.isInitialized && ::btnRedo.isInitialized) {
            btnUndo.isEnabled = historyManager.canUndo
            btnUndo.alpha = if (historyManager.canUndo) 1.0f else 0.35f
            btnRedo.isEnabled = historyManager.canRedo
            btnRedo.alpha = if (historyManager.canRedo) 1.0f else 0.35f
        }
    }

    private fun updateGraphWorkspaceControls() {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        val layerName = track?.name ?: "Tidak ada layer"
        val kfFrames = if (track != null) NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id).sorted() else emptyList()
        val hasKfAtCurrent = kfFrames.any { kotlin.math.abs(it - currentFrame) <= 3L }

        val curSec = currentFrame / projectFps
        val totalSec = projectDurationFrames / projectFps

        if (::graphKeyframeLabel.isInitialized) {
            val status = if (hasKfAtCurrent) "◆ KEYFRAME AKTIF" else "◇ BUKAN KEYFRAME"
            graphKeyframeLabel.text = "$status • Frame $currentFrame (${String.format("%.2fs", curSec)}) / ${String.format("%.1fs", totalSec)}"
            graphKeyframeLabel.setTextColor(if (hasKfAtCurrent) Color.parseColor("#4ADE80") else MotionTheme.TEXT_SECONDARY)
        }

        if (::graphTimeSeekBar.isInitialized && !graphTimeSeekBar.isPressed) {
            graphTimeSeekBar.max = projectDurationFrames.toInt()
            graphTimeSeekBar.progress = currentFrame.toInt()
        }

        if (track != null) {
            val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
            val curVal = when (activeGraphProperty) {
                "posY" -> state?.posY ?: 0.0
                "posZ" -> 0.0
                "scaleX" -> state?.scaleX ?: 1.0
                "rotation" -> state?.rotation ?: 0.0
                "opacity" -> state?.opacity ?: 1.0
                else -> state?.posX ?: 0.0
            }

            if (::graphPropertyValueReadout.isInitialized) {
                val unit = when (activeGraphProperty) {
                    "posX", "posY", "posZ" -> "px"
                    "scaleX" -> "x"
                    "rotation" -> "°"
                    "opacity" -> "%"
                    else -> ""
                }
                val formatted = when (activeGraphProperty) {
                    "scaleX" -> String.format("%.2f %s", curVal, unit)
                    "opacity" -> String.format("%d %s", (curVal * 100).toInt(), unit)
                    else -> String.format("%.1f %s", curVal, unit)
                }
                val propTitle = when (activeGraphProperty) {
                    "posX" -> "Posisi X"
                    "posY" -> "Posisi Y"
                    "posZ" -> "Posisi Z"
                    "scaleX" -> "Skala"
                    "rotation" -> "Rotasi"
                    "opacity" -> "Opasitas"
                    else -> activeGraphProperty
                }
                graphPropertyValueReadout.text = "$propTitle: $formatted"
            }

            if (::graphPropertyValueSeekBar.isInitialized && !graphPropertyValueSeekBar.isPressed) {
                val prog = when (activeGraphProperty) {
                    "posX", "posY" -> (curVal + 1000.0).toInt().coerceIn(0, 2000)
                    "posZ" -> (curVal + 500.0).toInt().coerceIn(0, 1000)
                    "scaleX" -> (curVal * 100.0).toInt().coerceIn(10, 500)
                    "rotation" -> (curVal + 360.0).toInt().coerceIn(0, 720)
                    "opacity" -> (curVal * 100.0).toInt().coerceIn(0, 100)
                    else -> 100
                }
                graphPropertyValueSeekBar.progress = prog
            }
        }
    }

    private fun onGraphPropertyValueChanged(progress: Int) {
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
            ?: return
        val newVal = when (activeGraphProperty) {
            "posX", "posY" -> (progress - 1000).toDouble()
            "posZ" -> (progress - 500).toDouble()
            "scaleX" -> (progress / 100.0).coerceAtLeast(0.05)
            "rotation" -> (progress - 360).toDouble()
            "opacity" -> (progress / 100.0).coerceIn(0.0, 1.0)
            else -> progress.toDouble()
        }
        val ox = if (::graphEditorView.isInitialized) graphEditorView.outHandleX else 0.42
        val oy = if (::graphEditorView.isInitialized) graphEditorView.outHandleY else 0.0
        val ix = if (::graphEditorView.isInitialized) graphEditorView.inHandleX else 0.58
        val iy = if (::graphEditorView.isInitialized) graphEditorView.inHandleY else 1.0

        NativeEngineBridge.nativeAddOrUpdateKeyframe(
            currentProjectPtr, track.id, activeGraphProperty, currentFrame, newVal,
            NativeEngineBridge.InterpolationMode.BEZIER.value, ox, oy, ix, iy
        )
        if (activeGraphProperty == "scaleX") {
            NativeEngineBridge.nativeAddOrUpdateKeyframe(
                currentProjectPtr, track.id, "scaleY", currentFrame, newVal,
                NativeEngineBridge.InterpolationMode.BEZIER.value, ox, oy, ix, iy
            )
        }
        if (!track.keyframeFrames.contains(currentFrame)) {
            track.keyframeFrames.add(currentFrame)
            track.keyframeFrames.sort()
        }
        timelineView.invalidate()
        renderCurrentFrame()
        updateGraphWorkspaceControls()
    }

    private fun initDefaultMediaAssets() {
        if (mediaLibraryAssets.isNotEmpty()) return
        mediaLibraryAssets.add(
            MediaAssetItem(
                id = "sample_media_1",
                name = "Video_Sample_Cinematic.mp4",
                filePath = "",
                isVideo = true,
                isAudio = false,
                resolutionOrDuration = "1920×1080 • 00:08",
                thumbnailBitmap = null
            )
        )
        mediaLibraryAssets.add(
            MediaAssetItem(
                id = "sample_media_2",
                name = "Cyber_Badge_Watermark.png",
                filePath = "",
                isVideo = false,
                isAudio = false,
                resolutionOrDuration = "1080×1080",
                thumbnailBitmap = null
            )
        )
        mediaLibraryAssets.add(
            MediaAssetItem(
                id = "sample_media_3",
                name = "Ambient_Synth_Beat.mp3",
                filePath = "",
                isVideo = false,
                isAudio = true,
                resolutionOrDuration = "Audio • 00:30",
                thumbnailBitmap = null
            )
        )
    }

    private fun openMediaPicker(isVideo: Boolean) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = if (isVideo) "video/*" else "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_MEDIA)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka berkas galeri: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_AUDIO)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka berkas audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            handleImportedMediaUri(uri, isAudioOnly = (requestCode == REQUEST_CODE_PICK_AUDIO))
        }
    }

    private fun handleImportedMediaUri(uri: Uri, isAudioOnly: Boolean) {
        try {
            var fileName = if (isAudioOnly) "Audio_${System.currentTimeMillis() % 1000}.mp3" else "Media_${System.currentTimeMillis() % 1000}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    val queried = cursor.getString(nameIdx)
                    if (!queried.isNullOrBlank()) fileName = queried
                }
            }

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val mediaDir = File(filesDir, "media_library")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val destFile = File(mediaDir, "${System.currentTimeMillis()}_$safeName")

            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val path = destFile.absolutePath
            val mime = contentResolver.getType(uri) ?: ""
            val lower = safeName.lowercase()
            val isAudio = isAudioOnly || mime.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg")
            val isVideo = !isAudio && (mime.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv"))

            var badgeInfo = "Aset Media"
            var thumb: Bitmap? = null

            if (isVideo) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(path)
                    val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1920"
                    val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "1080"
                    val sec = (durMs / 1000).toInt()
                    badgeInfo = "${w}×${h} • %02d:%02d".format(sec / 60, sec % 60)
                    thumb = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) {
                    badgeInfo = "Video Clip"
                } finally {
                    mmr.release()
                }
            } else if (isAudio) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(path)
                    val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val sec = (durMs / 1000).toInt()
                    badgeInfo = "Audio • %02d:%02d".format(sec / 60, sec % 60)
                } catch (_: Exception) {
                    badgeInfo = "Audio Track"
                } finally {
                    mmr.release()
                }
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                badgeInfo = "${opts.outWidth}×${opts.outHeight}"
                val sampleOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
                thumb = BitmapFactory.decodeFile(path, sampleOpts)
            }

            val item = MediaAssetItem(
                id = "asset_${System.currentTimeMillis() % 10000}",
                name = fileName,
                filePath = path,
                isVideo = isVideo,
                isAudio = isAudio,
                resolutionOrDuration = badgeInfo,
                thumbnailBitmap = thumb
            )
            mediaLibraryAssets.add(0, item)
            refreshMediaLibraryCards()
            Toast.makeText(this, "Media '$fileName' berhasil diimpor!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengimpor berkas: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createMediaLibraryWorkspace(): LinearLayout {
        initDefaultMediaAssets()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(16), dp(12), dp(16), dp(12))

            // Header Row: Title & Item Counter Badge
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }

            val titleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this@MainActivity).apply {
                text = "MEDIA LIBRARY"
                setTextColor(MotionTheme.TEXT_PRIMARY)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            titleCol.addView(title)

            val info = TextView(this@MainActivity).apply {
                text = "Aset video, grafis, dan audio lokal siap ditambahkan ke linimasa."
                setTextColor(MotionTheme.TEXT_MUTED)
                textSize = 11f
            }
            titleCol.addView(info)
            headerRow.addView(titleCol)

            mediaItemCountBadge = TextView(this@MainActivity).apply {
                text = "${mediaLibraryAssets.size} item"
                setTextColor(MotionTheme.TEXT_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            }
            headerRow.addView(mediaItemCountBadge)
            addView(headerRow)

            // Import Buttons Row
            val btnRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(36)
                ).apply { bottomMargin = dp(10) }
            }

            val btnImportVideoImage = Button(this@MainActivity).apply {
                text = "+ Impor Video / Foto"
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1.2f).apply { marginEnd = dp(6) }
                setTextColor(MotionTheme.CANVAS_BG)
                setBackgroundColor(MotionTheme.TEXT_PRIMARY)
                setOnClickListener {
                    openMediaPicker(false)
                }
            }
            btnRow.addView(btnImportVideoImage)

            val btnImportAudio = Button(this@MainActivity).apply {
                text = "+ Impor Musik"
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    openAudioPicker()
                }
            }
            btnRow.addView(btnImportAudio)
            addView(btnRow)

            // Filter Chips Row
            val filterRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(32)
                ).apply { bottomMargin = dp(10) }
            }

            filterButtons.clear()
            val filters = listOf("SEMUA", "VIDEO", "GAMBAR", "AUDIO")
            for (f in filters) {
                val btn = Button(this@MainActivity).apply {
                    text = f
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
                    val isDefault = (f == activeMediaFilter)
                    setTextColor(if (isDefault) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_MUTED)
                    setBackgroundColor(if (isDefault) MotionTheme.SURFACE_HIGH else MotionTheme.SURFACE_CONTAINER)

                    setOnClickListener {
                        activeMediaFilter = f
                        for (b in filterButtons) {
                            val active = (b == this)
                            b.setTextColor(if (active) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_MUTED)
                            b.setBackgroundColor(if (active) MotionTheme.SURFACE_HIGH else MotionTheme.SURFACE_CONTAINER)
                        }
                        refreshMediaLibraryCards()
                    }
                }
                filterButtons.add(btn)
                filterRow.addView(btn)
            }
            addView(filterRow)

            // Scrollable Cards List
            val scroll = ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                isVerticalScrollBarEnabled = true
            }

            mediaCardsContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            scroll.addView(mediaCardsContainer)
            addView(scroll)

            refreshMediaLibraryCards()
        }
    }

    private fun refreshMediaLibraryCards() {
        if (!::mediaCardsContainer.isInitialized) return
        mediaCardsContainer.removeAllViews()

        val filtered = mediaLibraryAssets.filter {
            when (activeMediaFilter) {
                "VIDEO" -> it.isVideo
                "GAMBAR" -> !it.isVideo && !it.isAudio
                "AUDIO" -> it.isAudio
                else -> true
            }
        }

        if (::mediaItemCountBadge.isInitialized) {
            mediaItemCountBadge.text = "${mediaLibraryAssets.size} item"
        }

        if (filtered.isEmpty()) {
            val emptyBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            }

            val icon = TextView(this).apply {
                text = "📁"
                textSize = 32f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            }
            emptyBox.addView(icon)

            val emptyTitle = TextView(this).apply {
                text = "Tidak Ada Aset Dalam Kategori Ini"
                setTextColor(MotionTheme.TEXT_PRIMARY)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            emptyBox.addView(emptyTitle)

            val emptySub = TextView(this).apply {
                text = "Gunakan tombol di atas untuk mengimpor berkas dari galeri perangkat Anda."
                setTextColor(MotionTheme.TEXT_MUTED)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), 0)
            }
            emptyBox.addView(emptySub)
            mediaCardsContainer.addView(emptyBox)
            return
        }

        for (asset in filtered) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            // Thumbnail / Icon box
            val thumbBox = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(46)).apply { marginEnd = dp(10) }
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
            }

            if (asset.thumbnailBitmap != null) {
                val iv = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(asset.thumbnailBitmap)
                }
                thumbBox.addView(iv)
            } else {
                val tvEmoji = TextView(this).apply {
                    text = when {
                        asset.isVideo -> "🎬"
                        asset.isAudio -> "🎵"
                        else -> "🖼"
                    }
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                thumbBox.addView(tvEmoji)
            }
            card.addView(thumbBox)

            // Info Column
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeTag = TextView(this).apply {
                text = when {
                    asset.isVideo -> "VIDEO"
                    asset.isAudio -> "AUDIO"
                    else -> "FOTO"
                }
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                val badgeBg = when {
                    asset.isVideo -> Color.parseColor("#4F46E5") // Indigo
                    asset.isAudio -> Color.parseColor("#D97706") // Amber
                    else -> Color.parseColor("#059669")         // Emerald
                }
                setBackgroundColor(badgeBg)
                setPadding(dp(5), dp(1), dp(5), dp(1))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(3) }
            }
            infoCol.addView(badgeTag)

            val nameView = TextView(this).apply {
                text = asset.name
                setTextColor(MotionTheme.TEXT_PRIMARY)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            infoCol.addView(nameView)

            val specView = TextView(this).apply {
                text = asset.resolutionOrDuration
                setTextColor(MotionTheme.TEXT_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
            }
            infoCol.addView(specView)
            card.addView(infoCol)

            // Action Buttons
            val btnAddTimeline = Button(this).apply {
                text = "+ Linimasa"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(8), 0, dp(8), 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(76), dp(32)).apply {
                    marginStart = dp(6)
                    marginEnd = dp(4)
                }
                setTextColor(MotionTheme.CANVAS_BG)
                setBackgroundColor(MotionTheme.TEXT_PRIMARY)
                setOnClickListener {
                    if (asset.isAudio) {
                        val id = "audio_${System.currentTimeMillis() % 1000}"
                        currentAudioFilePath = asset.filePath
                        if (asset.filePath.isNotBlank()) {
                            audioPlaybackManager?.loadAudio(asset.filePath)
                        }
                        NativeEngineBridge.nativeAddAudioLayer(
                            currentProjectPtr, id, asset.name, asset.filePath, currentFrame, 300
                        )
                    } else {
                        val id = "media_${System.currentTimeMillis() % 1000}"
                        NativeEngineBridge.nativeAddMediaLayer(
                            currentProjectPtr, id, asset.name, asset.filePath, asset.isVideo, currentFrame, 180
                        )
                    }
                    populateTimelineTracks()
                    renderCurrentFrame()
                    selectWorkspaceTab(0) // Switch immediately back to editor view!
                    Toast.makeText(this@MainActivity, "'${asset.name}' ditambahkan ke linimasa!", Toast.LENGTH_SHORT).show()
                }
            }
            card.addView(btnAddTimeline)

            val btnDelete = Button(this).apply {
                text = "✕"
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(32))
                setTextColor(MotionTheme.TEXT_MUTED)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    mediaLibraryAssets.remove(asset)
                    refreshMediaLibraryCards()
                    Toast.makeText(this@MainActivity, "Aset dihapus dari library", Toast.LENGTH_SHORT).show()
                }
            }
            card.addView(btnDelete)

            mediaCardsContainer.addView(card)
        }
    }

    data class MotionTemplateItem(
        val id: String,
        val title: String,
        val category: String,
        val durationFrames: Long,
        val durationText: String,
        val badgeText: String,
        val description: String,
        val previewColor: Int,
        val applyAction: () -> Unit
    )

    private val availableTemplates by lazy {
        listOf(
            MotionTemplateItem(
                id = "tpl_pop",
                title = "Kinetic Pop & Zoom",
                category = "POP",
                durationFrames = 120L,
                durationText = "00:02s",
                badgeText = "60 FPS • POP",
                description = "Animasi pop-in elastis dengan kurva Bézier dinamis dan aksen rotasi lembut.",
                previewColor = Color.parseColor("#6366F1"),
                applyAction = {
                    initCppProject()
                    val cId = "shape_pop_circ"
                    NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, cId, "Pop Lingkaran", 0, 0, 120, Color.parseColor("#6366F1"), 260.0, 260.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleX", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleX", 36, 1.2, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleX", 54, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleY", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleY", 36, 1.2, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.25, 1.0, 0.5, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "scaleY", 54, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "rotation", 0, -35.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, cId, "rotation", 48, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                    val bId = "shape_pop_box"
                    NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, bId, "Aksen Kotak", 1, 15, 105, Color.WHITE, 120.0, 120.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, bId, "scaleX", 15, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, bId, "scaleX", 50, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, bId, "scaleY", 15, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, bId, "scaleY", 50, 1.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                }
            ),
            MotionTemplateItem(
                id = "tpl_slide",
                title = "Cyber Slide & Glance",
                category = "KINETIC",
                durationFrames = 150L,
                durationText = "00:02.5s",
                badgeText = "60 FPS • KINETIC",
                description = "Banner horizontal dengan transisi posX cepat dan rotasi diamond futuristik.",
                previewColor = Color.parseColor("#06B6D4"),
                applyAction = {
                    initCppProject()
                    val barId = "shape_cyber_bar"
                    NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, barId, "Cyber Bar", 1, 0, 150, Color.parseColor("#06B6D4"), 720.0, 140.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, barId, "posX", 0, -1080.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.16, 1.0, 0.3, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, barId, "posX", 36, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)

                    val dId = "shape_cyber_diamond"
                    NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, dId, "Diamond", 1, 0, 150, Color.parseColor("#F43F5E"), 100.0, 100.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, dId, "posX", 0, 1080.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.16, 1.0, 0.3, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, dId, "posX", 36, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, dId, "rotation", 0, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.42, 0.0, 0.58, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, dId, "rotation", 60, 180.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                }
            ),
            MotionTemplateItem(
                id = "tpl_wipe",
                title = "Cinematic Screen Wipe",
                category = "TRANSISI",
                durationFrames = 108L,
                durationText = "00:01.8s",
                badgeText = "60 FPS • TRANSISI",
                description = "Transisi wipe mulus dari kiri ke kanan layar untuk transisi adegan profesional.",
                previewColor = Color.parseColor("#F59E0B"),
                applyAction = {
                    initCppProject()
                    val wId = "shape_wipe"
                    NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, wId, "Wipe Layar", 1, 0, 108, Color.parseColor("#F59E0B"), 1200.0, 2200.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, wId, "posX", 0, -1200.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.65, 0.0, 0.35, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, wId, "posX", 54, 0.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.65, 0.0, 0.35, 1.0)
                    NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, wId, "posX", 108, 1200.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                }
            ),
            MotionTemplateItem(
                id = "tpl_elastic",
                title = "Triple Elastic Bounce",
                category = "POP",
                durationFrames = 180L,
                durationText = "00:03s",
                badgeText = "60 FPS • ELASTIS",
                description = "Tiga elemen bertumpuk jatuh dari atas dengan efek pantulan elastis bertahap.",
                previewColor = Color.parseColor("#10B981"),
                applyAction = {
                    initCppProject()
                    val colors = listOf(Color.parseColor("#10B981"), Color.parseColor("#3B82F6"), Color.parseColor("#EC4899"))
                    val offsetsX = listOf(-160.0, 0.0, 160.0)
                    val delays = listOf(0L, 12L, 24L)
                    for (i in 0..2) {
                        val id = "shape_drop_$i"
                        NativeEngineBridge.nativeAddShapeLayer(currentProjectPtr, id, "Drop Circle ${i + 1}", 0, delays[i], 180 - delays[i], colors[i], 130.0, 130.0)
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, id, "posX", delays[i], offsetsX[i], NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, id, "posY", delays[i], 800.0, NativeEngineBridge.InterpolationMode.BEZIER.value, 0.34, 1.56, 0.64, 1.0)
                        NativeEngineBridge.nativeAddOrUpdateKeyframe(currentProjectPtr, id, "posY", delays[i] + 36, 0.0, NativeEngineBridge.InterpolationMode.LINEAR.value, 0.0, 0.0, 1.0, 1.0)
                    }
                }
            )
        )
    }

    private fun createTemplatesWorkspace(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )

            // Header Banner
            val headerBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(10))
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            }

            val badgeTag = TextView(this@MainActivity).apply {
                text = "MOTIONLAB STUDIO • TEMPLATE"
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#818CF8"))
                setPadding(0, 0, 0, dp(2))
            }
            headerBox.addView(badgeTag)

            val titleView = TextView(this@MainActivity).apply {
                text = "Template & Aset Animasi"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(MotionTheme.TEXT_PRIMARY)
            }
            headerBox.addView(titleView)

            val subView = TextView(this@MainActivity).apply {
                text = "Pilih preset animasi siap pakai dengan kurva Bézier halus untuk proyek Anda."
                textSize = 11f
                setTextColor(MotionTheme.TEXT_MUTED)
                setPadding(0, dp(2), 0, 0)
            }
            headerBox.addView(subView)
            addView(headerBox)

            // Category Filter Pills
            val filterScroll = HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            val filterRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val categories = listOf("SEMUA", "POP", "KINETIC", "TRANSISI")
            templateFilterButtons.clear()
            for (cat in categories) {
                val btn = Button(this@MainActivity).apply {
                    text = cat
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    gravity = Gravity.CENTER
                    val isSelected = (cat == activeTemplateFilter)
                    setTextColor(if (isSelected) MotionTheme.CANVAS_BG else MotionTheme.TEXT_MUTED)
                    setBackgroundColor(if (isSelected) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_CONTAINER)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(30)
                    ).apply { marginEnd = dp(6) }

                    setOnClickListener {
                        activeTemplateFilter = cat
                        for (other in templateFilterButtons) {
                            val active = (other.text == cat)
                            other.setTextColor(if (active) MotionTheme.CANVAS_BG else MotionTheme.TEXT_MUTED)
                            other.setBackgroundColor(if (active) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_CONTAINER)
                        }
                        refreshTemplateCards()
                    }
                }
                templateFilterButtons.add(btn)
                filterRow.addView(btn)
            }
            filterScroll.addView(filterRow)
            addView(filterScroll)

            // Scrollable Cards List
            val scrollView = ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            templateCardsContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(4), dp(16), dp(24))
            }
            scrollView.addView(templateCardsContainer)
            addView(scrollView)

            refreshTemplateCards()
        }
    }

    private fun refreshTemplateCards() {
        if (!::templateCardsContainer.isInitialized) return
        templateCardsContainer.removeAllViews()

        val filtered = availableTemplates.filter {
            if (activeTemplateFilter == "SEMUA") true else it.category == activeTemplateFilter
        }

        for (tpl in filtered) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            // Top Row: Preview Icon + Titles
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Visual Colored Box Icon
            val iconBox = FrameLayout(this).apply {
                setBackgroundColor(tpl.previewColor)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginEnd = dp(12)
                }
            }
            val iconText = TextView(this).apply {
                text = when (tpl.category) {
                    "POP" -> "⚡"
                    "KINETIC" -> "✦"
                    "TRANSISI" -> "⮞"
                    else -> "★"
                }
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            iconBox.addView(iconText)
            topRow.addView(iconBox)

            // Info Column
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeTag = TextView(this).apply {
                text = "${tpl.badgeText} • ${tpl.durationText}"
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#A1A1AA"))
            }
            infoCol.addView(badgeTag)

            val titleView = TextView(this).apply {
                text = tpl.title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(MotionTheme.TEXT_PRIMARY)
            }
            infoCol.addView(titleView)

            topRow.addView(infoCol)
            card.addView(topRow)

            // Description
            val descView = TextView(this).apply {
                text = tpl.description
                textSize = 11f
                setTextColor(MotionTheme.TEXT_MUTED)
                setPadding(0, dp(8), 0, dp(12))
            }
            card.addView(descView)

            // Action Buttons Row
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            val btnPreview = Button(this).apply {
                text = "▶ Pratinjau"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(12), 0, dp(12), 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(34)
                ).apply { marginEnd = dp(8) }
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    tpl.applyAction()
                    populateTimelineTracks()
                    updateProjectDurationFromLayers()
                    selectWorkspaceTab(0)
                    currentFrame = 0L
                    renderCurrentFrame()
                    if (!isPlaying) {
                        togglePlayback()
                    }
                    Toast.makeText(this@MainActivity, "Memutar pratinjau '${tpl.title}'", Toast.LENGTH_SHORT).show()
                }
            }
            btnRow.addView(btnPreview)

            val btnApply = Button(this).apply {
                text = "+ Terapkan ke Proyek"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(14), 0, dp(14), 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(34)
                )
                setTextColor(MotionTheme.CANVAS_BG)
                setBackgroundColor(MotionTheme.TEXT_PRIMARY)
                setOnClickListener {
                    tpl.applyAction()
                    populateTimelineTracks()
                    updateProjectDurationFromLayers()
                    selectWorkspaceTab(0)
                    currentFrame = 0L
                    renderCurrentFrame()
                    Toast.makeText(this@MainActivity, "Template '${tpl.title}' berhasil diterapkan ke proyek!", Toast.LENGTH_SHORT).show()
                }
            }
            btnRow.addView(btnApply)

            card.addView(btnRow)
            templateCardsContainer.addView(card)
        }
    }

    private fun selectWorkspaceTab(index: Int) {
        editorContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        graphContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        mediaContainer.visibility = if (index == 2) View.VISIBLE else View.GONE
        templatesContainer.visibility = if (index == 3) View.VISIBLE else View.GONE

        for (i in navButtons.indices) {
            val b = navButtons[i]
            val active = (i == index)
            b.setTextColor(if (active) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_MUTED)
            b.setBackgroundColor(if (active) MotionTheme.SURFACE_HIGH else Color.TRANSPARENT)
        }

        if (index == 0) {
            if (::timelineView.isInitialized) {
                timelineView.currentFrame = currentFrame
                timelineView.invalidate()
            }
            renderCurrentFrame()
        }

        if (index == 1) {
            if (::graphViewportView.isInitialized) {
                graphViewportView.updateProjectAspect(projectWidth, projectHeight)
            }
            if (::graphTimelineView.isInitialized) {
                graphTimelineView.tracks.clear()
                graphTimelineView.tracks.addAll(timelineView.tracks)
                graphTimelineView.selectedTrackIndex = timelineView.selectedTrackIndex
                graphTimelineView.totalDurationFrames = timelineView.totalDurationFrames
                graphTimelineView.currentFrame = currentFrame
                graphTimelineView.invalidate()
            }
            val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
                ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
            if (track != null && ::graphEditorView.isInitialized) {
                val propLabel = when (activeGraphProperty) {
                    "posY" -> "Pos Y"
                    "posZ" -> "Pos Z"
                    "scaleX" -> "Skala"
                    "rotation" -> "Rotasi"
                    "opacity" -> "Opasitas"
                    else -> "Pos X"
                }
                graphEditorView.propertyName = "${track.name} ($propLabel)"
            }
            renderCurrentFrame()
            updateGraphWorkspaceControls()
        }
    }

    private fun createBottomNavigationBar(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val tabs = listOf(
            Pair("EDITOR", editorContainer),
            Pair("GRAFIK & KF", graphContainer),
            Pair("MEDIA", mediaContainer),
            Pair("TEMPLATE", templatesContainer)
        )

        navButtons.clear()
        for (i in tabs.indices) {
            val tab = tabs[i]
            val btn = Button(this).apply {
                text = tab.first
                textSize = 12f
                typeface = Typeface.MONOSPACE
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
                val isDefault = (i == 0)
                setTextColor(if (isDefault) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_MUTED)
                setBackgroundColor(if (isDefault) MotionTheme.SURFACE_HIGH else Color.TRANSPARENT)

                setOnClickListener {
                    selectWorkspaceTab(i)
                }
            }
            navButtons.add(btn)
            nav.addView(btn)
        }

        return nav
    }

    private fun createCustomButton(label: String, widthDp: Int, heightDp: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            layoutParams = LinearLayout.LayoutParams(widthDp, heightDp).apply { marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun createIconButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(10), dp(4), dp(10), dp(4))
            gravity = Gravity.CENTER
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply { marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun populateTimelineTracks() {
        if (currentProjectPtr == 0L) return
        try {
            val jsonStr = NativeEngineBridge.nativeGetProjectJson(currentProjectPtr)
            if (jsonStr.isNotBlank()) {
                val json = org.json.JSONObject(jsonStr)

                // 1. Update project specs from JSON
                if (json.has("project")) {
                    val p = json.getJSONObject("project")
                    projectTitle = p.optString("title", projectTitle)
                    projectWidth = p.optInt("width", projectWidth)
                    projectHeight = p.optInt("height", projectHeight)
                    projectFps = p.optDouble("fps", projectFps)
                    projectDurationFrames = p.optLong("durationFrames", projectDurationFrames)

                    subtitleInfo.text = "$projectWidth×$projectHeight • ${projectFps.toInt()}fps"
                    timelineView.totalDurationFrames = projectDurationFrames
                }

                // 2. Populate layers and keyframes
                if (json.has("layers")) {
                    val layersArr = json.getJSONArray("layers")
                    timelineView.tracks.clear()

                    for (i in 0 until layersArr.length()) {
                        val lObj = layersArr.getJSONObject(i)
                        val id = lObj.optString("id", "layer_$i")
                        val name = lObj.optString("name", "Layer $i")
                        val type = lObj.optString("type", "SHAPE")
                        val start = lObj.optLong("startFrame", 0L)
                        val dur = lObj.optLong("durationFrames", projectDurationFrames)
                        val isVisible = lObj.optBoolean("isVisible", true)

                        val kfSet = sortedSetOf<Long>()
                        val tObj = lObj.optJSONObject("transform")
                        if (tObj != null) {
                            val propKeys = arrayOf("posX", "posY", "posZ", "scaleX", "scaleY", "rotation", "opacity")
                            for (pk in propKeys) {
                                val propObj = tObj.optJSONObject(pk)
                                val kfsArr = propObj?.optJSONArray("keyframes")
                                if (kfsArr != null) {
                                    for (k in 0 until kfsArr.length()) {
                                        val kfItem = kfsArr.optJSONObject(k)
                                        if (kfItem != null && kfItem.has("frame")) {
                                            kfSet.add(kfItem.getLong("frame"))
                                        }
                                    }
                                }
                            }
                        }

                        val blendModeStr = lObj.optString("blendMode", "NORMAL")
                        val blendModeId = when (blendModeStr) {
                            "MULTIPLY" -> 1
                            "SCREEN" -> 2
                            "OVERLAY" -> 3
                            "ADD" -> 4
                            "DIFFERENCE" -> 5
                            else -> 0
                        }
                        val inTransStr = lObj.optString("inTransition", "NONE")
                        val inTransId = when (inTransStr) {
                            "FADE" -> 1
                            "SLIDE_LEFT" -> 2
                            "SLIDE_RIGHT" -> 3
                            "SLIDE_UP" -> 4
                            "SLIDE_DOWN" -> 5
                            "ZOOM" -> 6
                            "SPIN" -> 7
                            else -> 0
                        }
                        val inTransDur = lObj.optLong("inTransitionDuration", 0L)
                        val outTransStr = lObj.optString("outTransition", "NONE")
                        val outTransId = when (outTransStr) {
                            "FADE" -> 1
                            "SLIDE_LEFT" -> 2
                            "SLIDE_RIGHT" -> 3
                            "SLIDE_UP" -> 4
                            "SLIDE_DOWN" -> 5
                            "ZOOM" -> 6
                            "SPIN" -> 7
                            else -> 0
                        }
                        val outTransDur = lObj.optLong("outTransitionDuration", 0L)
                        val mediaObj = lObj.optJSONObject("media")
                        val mWidth = mediaObj?.optInt("originalWidth", 0) ?: 0
                        val mHeight = mediaObj?.optInt("originalHeight", 0) ?: 0
                        val mbEnabled = lObj.optBoolean("motionBlurEnabled", false)
                        val mbStrength = lObj.optDouble("motionBlurStrength", 1.0).toFloat()

                        timelineView.tracks.add(
                            TimelineDeckView.TrackInfo(
                                id = id,
                                name = name,
                                type = type,
                                isVisible = isVisible,
                                startFrame = start,
                                durationFrames = dur,
                                keyframeFrames = kfSet.toMutableList(),
                                blendMode = blendModeId,
                                inTransition = inTransId,
                                inTransitionDuration = inTransDur,
                                outTransition = outTransId,
                                outTransitionDuration = outTransDur,
                                mediaWidth = mWidth,
                                mediaHeight = mHeight,
                                motionBlurEnabled = mbEnabled,
                                motionBlurStrength = mbStrength
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback default tracks if project has no layers
        if (timelineView.tracks.isEmpty()) {
            timelineView.tracks.add(
                TimelineDeckView.TrackInfo(
                    id = "shape_circle_1",
                    name = "Lingkaran 1",
                    type = "SHAPE",
                    startFrame = 0,
                    durationFrames = projectDurationFrames,
                    keyframeFrames = mutableListOf(0L, projectDurationFrames / 2, projectDurationFrames)
                )
            )
        }

        if (::timelineView.isInitialized && timelineView.selectedTrackIndex !in timelineView.tracks.indices) {
            timelineView.selectedTrackIndex = 0
        }
        val currentTrack = if (::timelineView.isInitialized) timelineView.tracks.getOrNull(timelineView.selectedTrackIndex) else null
        if (currentTrack != null) {
            if (::viewportView.isInitialized) {
                viewportView.selectedLayerName = currentTrack.name
            }
            if (::graphEditorView.isInitialized) {
                graphEditorView.propertyName = "${currentTrack.name} (posX)"
            }
        }
        if (::timelineView.isInitialized) {
            timelineView.invalidate()
        }
        if (::graphTimelineView.isInitialized) {
            graphTimelineView.tracks.clear()
            graphTimelineView.tracks.addAll(timelineView.tracks)
            graphTimelineView.selectedTrackIndex = timelineView.selectedTrackIndex
            graphTimelineView.totalDurationFrames = timelineView.totalDurationFrames
            graphTimelineView.currentFrame = currentFrame
            graphTimelineView.invalidate()
        }
        updateProjectDurationFromLayers()
    }

    private fun updateProjectDurationFromLayers() {
        if (!::timelineView.isInitialized || timelineView.tracks.isEmpty()) return

        val maxEnd = timelineView.tracks.maxOfOrNull { it.startFrame + it.durationFrames } ?: 30L
        val newDuration = maxOf(30L, maxEnd)

        projectDurationFrames = newDuration
        timelineView.totalDurationFrames = newDuration
        if (::graphTimelineView.isInitialized) {
            graphTimelineView.totalDurationFrames = newDuration
            graphTimelineView.invalidate()
        }

        if (currentProjectPtr != 0L) {
            NativeEngineBridge.nativeSetProjectSettings(
                currentProjectPtr,
                projectWidth,
                projectHeight,
                projectFps,
                projectDurationFrames,
                0xFF000000.toInt()
            )
        }

        if (currentFrame >= projectDurationFrames) {
            currentFrame = (projectDurationFrames - 1).coerceAtLeast(0L)
        }

        val curSec = currentFrame / projectFps
        val totalSec = projectDurationFrames / projectFps
        val curMin = (curSec / 60).toInt()
        val curRemSec = (curSec % 60).toInt()
        val totMin = (totalSec / 60).toInt()
        val totRemSec = (totalSec % 60).toInt()

        val tc = String.format("%02d:%02d / %02d:%02d", curMin, curRemSec, totMin, totRemSec)
        if (::timecodeText.isInitialized) {
            timecodeText.text = tc
        }
        if (::viewportView.isInitialized) {
            viewportView.currentTimecode = String.format("%02d:%02d", curMin, curRemSec)
        }
        timelineView.invalidate()
    }

    private fun renderCurrentFrame() {
        if (currentProjectPtr == 0L) return

        // 1. Evaluate transform on C++ engine
        val track = timelineView.tracks.getOrNull(timelineView.selectedTrackIndex)
            ?: timelineView.tracks.firstOrNull()?.also { timelineView.selectedTrackIndex = 0 }
        if (track != null) {
            val state = NativeEngineBridge.evaluateTransform(currentProjectPtr, track.id, currentFrame.toDouble())
            if (state != null) {
                val lw = if (track.type == "TEXT") (track.name.length * 48f * 0.85f).coerceAtLeast(140f)
                         else if (track.type == "MEDIA") {
                             val maxDim = (projectWidth * 0.6f).coerceAtLeast(400f)
                             if (track.mediaWidth > 0 && track.mediaHeight > 0) {
                                 val aspect = track.mediaWidth.toFloat() / track.mediaHeight.toFloat()
                                 if (aspect >= 1f) maxDim else maxDim * aspect
                             } else {
                                 maxDim
                             }
                         }
                         else if (track.name.contains("Bar")) 720f
                         else if (track.name.contains("Wipe")) 1080f
                         else 260f
                val lh = if (track.type == "TEXT") 64f
                         else if (track.type == "MEDIA") {
                             val maxDim = (projectWidth * 0.6f).coerceAtLeast(400f)
                             if (track.mediaWidth > 0 && track.mediaHeight > 0) {
                                 val aspect = track.mediaWidth.toFloat() / track.mediaHeight.toFloat()
                                 if (aspect >= 1f) maxDim / aspect else maxDim
                             } else {
                                 maxDim
                             }
                         }
                         else if (track.name.contains("Bar")) 140f
                         else if (track.name.contains("Wipe")) 1920f
                         else 260f

                if (::viewportView.isInitialized) {
                    viewportView.selectedLayerX = state.posX.toFloat()
                    viewportView.selectedLayerY = state.posY.toFloat()
                    viewportView.selectedLayerWidth = lw
                    viewportView.selectedLayerHeight = lh
                    viewportView.selectedLayerScaleX = state.scaleX.toFloat()
                    viewportView.selectedLayerScaleY = state.scaleY.toFloat()
                    viewportView.selectedLayerRotation = state.rotation.toFloat()
                    viewportView.selectedLayerName = track.name
                }
                if (::graphViewportView.isInitialized) {
                    graphViewportView.selectedLayerX = state.posX.toFloat()
                    graphViewportView.selectedLayerY = state.posY.toFloat()
                    graphViewportView.selectedLayerWidth = lw
                    graphViewportView.selectedLayerHeight = lh
                    graphViewportView.selectedLayerScaleX = state.scaleX.toFloat()
                    graphViewportView.selectedLayerScaleY = state.scaleY.toFloat()
                    graphViewportView.selectedLayerRotation = state.rotation.toFloat()
                    graphViewportView.selectedLayerName = track.name
                }
            }
        }

        // 2. Render frame to preview using GPU Compositor
        if (compositorPtr != 0L) {
            NativeEngineBridge.nativeRenderFrameGpu(compositorPtr, currentProjectPtr, currentFrame.toDouble())
        }

        // 2. Render frame to preview bitmap using native software renderer
        val targetW = 540
        val targetH = if (projectWidth > 0) ((targetW.toLong() * projectHeight) / projectWidth).toInt().coerceAtLeast(1) else 960
        if (previewBitmap == null || previewBitmap?.width != targetW || previewBitmap?.height != targetH) {
            previewBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        }
        previewBitmap?.let { bmp ->
            NativeEngineBridge.nativeRenderFrameToBitmap(currentProjectPtr, currentFrame, bmp)
            if (::viewportView.isInitialized) {
                viewportView.previewBitmap = bmp
            }
            if (::graphViewportView.isInitialized) {
                graphViewportView.previewBitmap = bmp
            }
        }

        // 3. Update Timecode Display
        val curSec = currentFrame / projectFps
        val totalSec = projectDurationFrames / projectFps
        val curMin = (curSec / 60).toInt()
        val curRemSec = (curSec % 60).toInt()
        val totMin = (totalSec / 60).toInt()
        val totRemSec = (totalSec % 60).toInt()

        val tc = String.format("%02d:%02d / %02d:%02d", curMin, curRemSec, totMin, totRemSec)
        if (::timecodeText.isInitialized) {
            timecodeText.text = tc
        }
        if (::viewportView.isInitialized) {
            viewportView.currentTimecode = String.format("%02d:%02d", curMin, curRemSec)
        }
        if (::graphTimecodeText.isInitialized) {
            graphTimecodeText.text = tc
        }
        if (::graphViewportView.isInitialized) {
            graphViewportView.currentTimecode = String.format("%02d:%02d", curMin, curRemSec)
        }
        if (::graphSeekSlider.isInitialized && !graphSeekSlider.isPressed) {
            graphSeekSlider.progress = currentFrame.toInt()
        }
        timelineView.currentFrame = currentFrame
        timelineView.invalidate()
        if (::graphTimelineView.isInitialized) {
            graphTimelineView.currentFrame = currentFrame
            graphTimelineView.invalidate()
        }

        if (::graphContainer.isInitialized && graphContainer.visibility == View.VISIBLE) {
            updateGraphWorkspaceControls()
        }
    }

    private fun togglePlayback() {
        isPlaying = !isPlaying
        val icon = if (isPlaying) "❚❚" else "▶"
        if (::btnPlayPause.isInitialized) {
            btnPlayPause.text = icon
        }
        if (::graphBtnPlayPause.isInitialized) {
            graphBtnPlayPause.text = icon
        }

        if (isPlaying) {
            audioPlaybackManager?.play(currentFrame, projectFps)
            playbackLoop()
        } else {
            audioPlaybackManager?.pause()
            playbackHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun playbackLoop() {
        if (!isPlaying) return

        currentFrame = (currentFrame + 1) % projectDurationFrames
        renderCurrentFrame()

        // 60 fps -> ~16ms per frame
        playbackHandler.postDelayed({ playbackLoop() }, 16)
    }

    private fun openKeyframeEditor(track: TimelineDeckView.TrackInfo, frame: Long) {
        KeyframeEditorDialog(
            context = this,
            projectPtr = currentProjectPtr,
            layerId = track.id,
            layerName = track.name,
            initialFrame = frame,
            totalDurationFrames = projectDurationFrames,
            projectFps = projectFps,
            onPreviewUpdate = { f, px, py, sx, sy, rot ->
                currentFrame = f
                viewportView.selectedLayerX = px.toFloat()
                viewportView.selectedLayerY = py.toFloat()
                viewportView.selectedLayerScaleX = sx.toFloat()
                viewportView.selectedLayerScaleY = sy.toFloat()
                viewportView.selectedLayerRotation = rot.toFloat()
                renderCurrentFrame()
            },
            onKeyframeListChanged = {
                val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(currentProjectPtr, track.id)
                track.keyframeFrames.clear()
                for (kf in frames) track.keyframeFrames.add(kf)
                track.keyframeFrames.sort()
                timelineView.invalidate()
            }
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        playbackHandler.removeCallbacksAndMessages(null)
        audioPlaybackManager?.stop()
        audioPlaybackManager = null
        if (currentProjectPtr != 0L) {
            NativeEngineBridge.nativeDestroyProject(currentProjectPtr)
            currentProjectPtr = 0L
        }
        if (compositorPtr != 0L) {
            NativeEngineBridge.nativeDestroyGpuCompositor(compositorPtr)
            compositorPtr = 0L
        }
    }
}