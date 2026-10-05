package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class ShaderEffectsDialog(
    context: Context,
    private var brightness: Float,
    private var contrast: Float,
    private var saturation: Float,
    private var vignette: Float,
    private var filterMode: Int,
    private val selectedLayerName: String? = null,
    private var motionBlurEnabled: Boolean = false,
    private var motionBlurStrength: Float = 1.0f,
    private val onMotionBlurChanged: ((enabled: Boolean, strength: Float) -> Unit)? = null,
    private val onEffectsChanged: (b: Float, c: Float, s: Float, v: Float, mode: Int) -> Unit
) : Dialog(context) {

    // Overload for backward compatibility
    constructor(
        context: Context,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        vignette: Float,
        filterMode: Int,
        onEffectsChanged: (b: Float, c: Float, s: Float, v: Float, mode: Int) -> Unit
    ) : this(
        context = context,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        vignette = vignette,
        filterMode = filterMode,
        selectedLayerName = null,
        motionBlurEnabled = false,
        motionBlurStrength = 1.0f,
        onMotionBlurChanged = null,
        onEffectsChanged = onEffectsChanged
    )

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
            text = "STUDIO EFEK & VFX GRAFIKA"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val header = TextView(context).apply {
            text = "Efek Visual & Buram Gerak"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerBox.addView(header)
        root.addView(headerBox)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 0. Dynamic Motion Blur (VFX Shutter Angle)
        if (onMotionBlurChanged != null) {
            val mbContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(14))
                val bgDrawable = GradientDrawable().apply {
                    setColor(Color.parseColor("#171724"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#2E3048"))
                }
                background = bgDrawable
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(16)
                }
                layoutParams = lp
            }

            // Section Tag & Header
            val mbTag = TextView(context).apply {
                text = "VFX DINAMIS LAPISAN"
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#06B6D4")) // Cyan
            }
            mbContainer.addView(mbTag)

            // Row with Title + Switch
            val mbRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(8))
            }

            val mbTextCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val mbTitle = TextView(context).apply {
                text = "Buram Gerak (Motion Blur)"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(MotionTheme.TEXT_PRIMARY)
            }
            val targetName = selectedLayerName ?: "Lapisan Terpilih"
            val mbSubtitle = TextView(context).apply {
                text = "Target: $targetName • Multi-sample shutter"
                textSize = 10f
                setTextColor(MotionTheme.TEXT_MUTED)
            }
            mbTextCol.addView(mbTitle)
            mbTextCol.addView(mbSubtitle)
            mbRow.addView(mbTextCol)

            val mbSwitch = Switch(context).apply {
                isChecked = motionBlurEnabled
            }
            mbRow.addView(mbSwitch)
            mbContainer.addView(mbRow)

            // Shutter Angle & Strength controls (sub layout)
            val shutterBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                alpha = if (motionBlurEnabled) 1.0f else 0.4f
            }

            val shutterLabel = TextView(context).apply {
                val deg = (motionBlurStrength * 180f).toInt()
                text = "Shutter Angle: ${String.format("%.1fx", motionBlurStrength)} ($deg°)"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#06B6D4"))
                setPadding(0, dp(4), 0, dp(2))
            }
            shutterBox.addView(shutterLabel)

            val shutterSeek = SeekBar(context).apply {
                this.max = 100 // maps to 0.2x .. 2.0x
                val initialProg = (((motionBlurStrength - 0.2f) / 1.8f) * 100f).toInt().coerceIn(0, 100)
                progress = initialProg
                isEnabled = motionBlurEnabled
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val str = 0.2f + (progress / 100f) * 1.8f
                            motionBlurStrength = str
                            val deg = (str * 180f).toInt()
                            shutterLabel.text = "Shutter Angle: ${String.format("%.1fx", str)} ($deg°)"
                            onMotionBlurChanged.invoke(motionBlurEnabled, motionBlurStrength)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            shutterBox.addView(shutterSeek)

            // Presets for Motion Blur (Halus 90°, Alami 180°, Sinematik 360°)
            val mbPresetRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }
            data class MbPreset(val name: String, val strength: Float)
            val mbPresets = listOf(
                MbPreset("Halus (90°)", 0.5f),
                MbPreset("Alami (180°)", 1.0f),
                MbPreset("Sinematik (360°)", 2.0f)
            )
            mbPresets.forEachIndexed { idx, p ->
                val pBtn = Button(context).apply {
                    text = p.name
                    textSize = 9.5f
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                        if (idx < mbPresets.size - 1) marginEnd = dp(6)
                    }
                    setTextColor(MotionTheme.TEXT_PRIMARY)
                    setBackgroundColor(MotionTheme.SURFACE_HIGH)
                    isEnabled = motionBlurEnabled
                    setOnClickListener {
                        motionBlurStrength = p.strength
                        val deg = (p.strength * 180f).toInt()
                        shutterLabel.text = "Shutter Angle: ${String.format("%.1fx", p.strength)} ($deg°)"
                        shutterSeek.progress = (((p.strength - 0.2f) / 1.8f) * 100f).toInt().coerceIn(0, 100)
                        onMotionBlurChanged.invoke(motionBlurEnabled, motionBlurStrength)
                    }
                }
                mbPresetRow.addView(pBtn)
            }
            shutterBox.addView(mbPresetRow)
            mbContainer.addView(shutterBox)

            mbSwitch.setOnCheckedChangeListener { _, isChecked ->
                motionBlurEnabled = isChecked
                shutterBox.alpha = if (isChecked) 1.0f else 0.4f
                shutterSeek.isEnabled = isChecked
                for (ci in 0 until mbPresetRow.childCount) {
                    mbPresetRow.getChildAt(ci).isEnabled = isChecked
                }
                onMotionBlurChanged.invoke(motionBlurEnabled, motionBlurStrength)
            }

            content.addView(mbContainer)
        }

        // 1. One-Tap Cinematic Grading Presets
        val presetLabel = TextView(context).apply {
            text = "PRESET GAYA SINEMATIK"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        content.addView(presetLabel)

        data class GradePreset(
            val name: String,
            val b: Float,
            val c: Float,
            val s: Float,
            val v: Float,
            val mode: Int
        )

        val presets = listOf(
            GradePreset("Natural", 0.0f, 1.0f, 1.0f, 0.0f, 0),
            GradePreset("Cyberpunk", 0.04f, 1.35f, 1.6f, 0.35f, 5),
            GradePreset("Teal/Orange", -0.02f, 1.25f, 1.35f, 0.25f, 0),
            GradePreset("Film Noir", 0.0f, 1.6f, 0.0f, 0.55f, 2),
            GradePreset("Retro 90s", 0.02f, 1.15f, 1.1f, 0.4f, 3),
            GradePreset("RGB Glitch", 0.0f, 1.1f, 1.4f, 0.15f, 4)
        )

        val gradeGrid1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
            layoutParams = lp
        }
        val gradeGrid2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }

        val sliderUpdaters = mutableListOf<() -> Unit>()

        presets.forEachIndexed { i, p ->
            val btn = Button(context).apply {
                text = p.name
                textSize = 10f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (i % 3 != 2) marginEnd = dp(6)
                }
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    brightness = p.b
                    contrast = p.c
                    saturation = p.s
                    vignette = p.v
                    filterMode = p.mode
                    sliderUpdaters.forEach { it.invoke() }
                    onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                }
            }
            if (i < 3) gradeGrid1.addView(btn) else gradeGrid2.addView(btn)
        }
        content.addView(gradeGrid1)
        content.addView(gradeGrid2)

        // 2. Sliders
        fun createSlider(
            label: String,
            min: Float,
            max: Float,
            getVal: () -> Float,
            setVal: (Float) -> Unit
        ) {
            val labelView = TextView(context).apply {
                text = "$label: ${String.format("%.2f", getVal())}"
                setTextColor(MotionTheme.TEXT_SECONDARY)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(4), 0, 0)
            }
            content.addView(labelView)

            val seek = SeekBar(context).apply {
                this.max = 100
                progress = (((getVal() - min) / (max - min)) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = min + (progress / 100f) * (max - min)
                        labelView.text = "$label: ${String.format("%.2f", value)}"
                        setVal(value)
                        onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            sliderUpdaters.add {
                val cur = getVal()
                labelView.text = "$label: ${String.format("%.2f", cur)}"
                seek.progress = (((cur - min) / (max - min)) * 100).toInt()
            }
            content.addView(seek)
        }

        createSlider("Kecerahan (Brightness)", -0.5f, 0.5f, { brightness }) { brightness = it }
        createSlider("Kontras (Contrast)", 0.2f, 2.5f, { contrast }) { contrast = it }
        createSlider("Saturasi (Saturation)", 0.0f, 2.5f, { saturation }) { saturation = it }
        createSlider("Vignette (Sudut Gelap)", 0.0f, 1.0f, { vignette }) { vignette = it }

        scrollView.addView(content)
        root.addView(scrollView)

        val btnClose = Button(context).apply {
            text = "Tutup & Terapkan"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
                topMargin = dp(12)
            }
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4F46E5"))
            setOnClickListener { dismiss() }
        }
        root.addView(btnClose)

        setContentView(root)
    }
}
