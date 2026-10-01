package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class ShapeStyleDialog(
    context: Context,
    private val layerId: String,
    private val layerName: String,
    private val layerType: String, // SHAPE or TEXT
    private val initialColor: Int,
    private val initialWidthOrSize: Double,
    private val initialTextContent: String = "",
    private val onStyleUpdated: (color: Int, size: Double, text: String) -> Unit
) : Dialog(context) {

    private var selectedColor = initialColor
    private var currentSize = initialWidthOrSize
    private var textContent = initialTextContent
    private val colorViews = mutableListOf<View>()

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
            text = "GAYA & TAMPILAN ELEMEN"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Edit: $layerName"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerBox.addView(titleView)
        root.addView(headerBox)

        // 1. Text Content Input (if TEXT layer)
        if (layerType == "TEXT") {
            val txtLabel = createSectionTitle("KONTEN TEKS")
            root.addView(txtLabel)

            val input = EditText(context).apply {
                setText(textContent)
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setHintTextColor(MotionTheme.TEXT_MUTED)
                hint = "Masukkan teks animasi..."
                textSize = 13f
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(40)
                ).apply { bottomMargin = dp(14) }
            }
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    textContent = p0?.toString() ?: ""
                    onStyleUpdated(selectedColor, currentSize, textContent)
                }
                override fun afterTextChanged(p0: android.text.Editable?) {}
            })
            root.addView(input)
        }

        // 2. Color Palette Section
        val colorTitle = createSectionTitle("PALET WARNA")
        root.addView(colorTitle)

        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val palette = listOf(
            Pair("Putih", Color.parseColor("#FFFFFF")),
            Pair("Indigo", Color.parseColor("#6366F1")),
            Pair("Cyan", Color.parseColor("#06B6D4")),
            Pair("Emerald", Color.parseColor("#10B981")),
            Pair("Amber", Color.parseColor("#F59E0B")),
            Pair("Rose", Color.parseColor("#F43F5E")),
            Pair("Ungu", Color.parseColor("#A855F7")),
            Pair("Gelap", Color.parseColor("#27272A"))
        )

        for (item in palette) {
            val colorBox = FrameLayout(context).apply {
                setBackgroundColor(item.second)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    selectedColor = item.second
                    updateColorSelectionUI(colorRow)
                    onStyleUpdated(selectedColor, currentSize, textContent)
                }
            }
            colorRow.addView(colorBox)
            colorViews.add(colorBox)
        }
        root.addView(colorRow)

        // 3. Size / Dimension Section
        val sizeLabelText = if (layerType == "TEXT") "UKURAN FONT" else "DIMENSI UKURAN"
        val sizeTitle = createSectionTitle(sizeLabelText)
        root.addView(sizeTitle)

        val sizeReadout = TextView(context).apply {
            text = String.format("%.0f px", currentSize)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(sizeReadout)

        val minSize = if (layerType == "TEXT") 18 else 40
        val maxSize = if (layerType == "TEXT") 160 else 800

        val sizeSeek = SeekBar(context).apply {
            max = maxSize - minSize
            progress = (currentSize.toInt() - minSize).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentSize = (prog + minSize).toDouble()
                        sizeReadout.text = String.format("%.0f px", currentSize)
                        onStyleUpdated(selectedColor, currentSize, textContent)
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        root.addView(sizeSeek)

        // Done Button
        val btnDone = Button(context).apply {
            text = "Selesai"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { topMargin = dp(16) }
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener { dismiss() }
        }
        root.addView(btnDone)

        setContentView(root)
    }

    private fun updateColorSelectionUI(colorRow: LinearLayout) {
        for (i in 0 until colorRow.childCount) {
            val child = colorRow.getChildAt(i)
            // Add a subtle border or scale
            child.alpha = 1.0f
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, dp(6), 0, dp(4))
        }
    }
}
