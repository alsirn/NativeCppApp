package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.NativeEngineBridge
import com.example.nativecppapp.ui.theme.MotionTheme

class AddElementDialog(
    context: Context,
    private val onAddShape: (shapeType: Int, name: String) -> Unit,
    private val onAddMedia: (filePath: String, isVideo: Boolean, name: String) -> Unit,
    private val onAddAudio: (filePath: String, name: String) -> Unit,
    private val onAddText: ((text: String, name: String) -> Unit)? = null,
    private val onPickMedia: ((isVideo: Boolean) -> Unit)? = null,
    private val onPickAudio: (() -> Unit)? = null,
    private val onAddVfxPreset: ((presetId: Int, name: String) -> Unit)? = null
) : Dialog(context) {

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val header = TextView(context).apply {
            text = "Tambah Elemen Baru (+)"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(header)

        // 1. Bentuk Vektor Section
        val shapeLabel = TextView(context).apply {
            text = "BENTUK VEKTOR"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(shapeLabel)

        val shapeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val shapes = listOf(
            Pair("Lingkaran", NativeEngineBridge.ShapeType.CIRCLE.value),
            Pair("Persegi", NativeEngineBridge.ShapeType.RECTANGLE.value),
            Pair("Bintang", NativeEngineBridge.ShapeType.STAR.value)
        )
        for (s in shapes) {
            val btn = Button(context).apply {
                text = s.first
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(6)
                }
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    onAddShape(s.second, s.first)
                    dismiss()
                }
            }
            shapeRow.addView(btn)
        }
        root.addView(shapeRow)

        // 2. Teks Dinamis Section
        val textLabel = TextView(context).apply {
            text = "LAYER TEKS DINAMIS"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(textLabel)

        val textRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val textInput = EditText(context).apply {
            hint = "Tulis teks animasi..."
            setHintTextColor(MotionTheme.TEXT_MUTED)
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(6)
            }
        }
        textRow.addView(textInput)

        val btnAddText = Button(context).apply {
            text = "+ Teks"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener {
                val txt = textInput.text.toString().ifBlank { "MotionLab" }
                onAddText?.invoke(txt, txt)
                dismiss()
            }
        }
        textRow.addView(btnAddText)
        root.addView(textRow)

        // 3. Media Section
        val mediaLabel = TextView(context).apply {
            text = "MEDIA PERANGKAT"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(mediaLabel)

        val mediaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val btnImage = Button(context).apply {
            text = "Foto / Gambar"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(6)
            }
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener {
                if (onPickMedia != null) {
                    onPickMedia.invoke(false)
                } else {
                    onAddMedia("/storage/emulated/0/Pictures/sample.png", false, "Foto Latar")
                }
                dismiss()
            }
        }
        mediaRow.addView(btnImage)

        val btnVideo = Button(context).apply {
            text = "Video Klip"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener {
                if (onPickMedia != null) {
                    onPickMedia.invoke(true)
                } else {
                    onAddMedia("/storage/emulated/0/DCIM/sample.mp4", true, "Video Klip 01")
                }
                dismiss()
            }
        }
        mediaRow.addView(btnVideo)
        root.addView(mediaRow)

        // 4. Audio Section
        val audioLabel = TextView(context).apply {
            text = "AUDIO & SOUNDTRACK"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(audioLabel)

        val btnAudio = Button(context).apply {
            text = "+ Tambah Audio Trek"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply {
                bottomMargin = dp(10)
            }
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener {
                if (onPickAudio != null) {
                    onPickAudio.invoke()
                } else {
                    onAddAudio("/storage/emulated/0/Music/audio.mp3", "Soundtrack 01")
                }
                dismiss()
            }
        }
        root.addView(btnAudio)

        // 5. Preset Grafis & Animasi VFX
        val vfxLabel = TextView(context).apply {
            text = "PRESET GRAFIS & ANIMASI VFX"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(vfxLabel)

        val vfxRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val vfxPresets = listOf(
            Pair("Lower Third", 0),
            Pair("Pulse Glow", 1),
            Pair("Intro Title", 2),
            Pair("Spin Badge", 3)
        )

        for (vfx in vfxPresets) {
            val btn = Button(context).apply {
                text = vfx.first
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(4), dp(4), dp(4), dp(4))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    marginEnd = dp(4)
                }
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4F46E5"))
                setOnClickListener {
                    onAddVfxPreset?.invoke(vfx.second, vfx.first)
                    dismiss()
                }
            }
            vfxRow.addView(btn)
        }
        root.addView(vfxRow)

        // Cancel
        val btnCancel = Button(context).apply {
            text = "Tutup"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36))
            setTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }
        root.addView(btnCancel)

        setContentView(root)
    }
}
