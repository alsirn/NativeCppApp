package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class BlendModeDialog(
    context: Context,
    private val layerName: String,
    private var currentMode: Int,
    private val onBlendModeSelected: (mode: Int) -> Unit
) : Dialog(context) {

    data class BlendModeItem(val id: Int, val name: String, val subtitle: String, val desc: String)

    private val blendModes = listOf(
        BlendModeItem(0, "Normal", "Standar Over", "Warna normal tanpa percampuran kanal."),
        BlendModeItem(1, "Multiply", "Gelap / Perbanyak", "Mengalikan RGB untuk efek bayangan & siluet."),
        BlendModeItem(2, "Screen", "Terang / Layar", "Menerangkan latar, ideal untuk cahaya & flare."),
        BlendModeItem(3, "Overlay", "Kontras Campuran", "Kombinasi kontras tinggi antara gelap & terang."),
        BlendModeItem(4, "Add / Glow", "Kilau Pendar", "Menambahkan energi warna aditif untuk neon/laser."),
        BlendModeItem(5, "Difference", "Inversi Artistik", "Mengurangkan warna piksel untuk efek sci-fi.")
    )

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }

        val tagView = TextView(context).apply {
            text = "KOMPOSISI TINGKAT LANJUT"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Mode Blending: $layerName"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(titleView)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (item in blendModes) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val isSelected = (item.id == currentMode)
                setBackgroundColor(if (isSelected) Color.parseColor("#1E1E2E") else MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val title = TextView(context).apply {
                    text = item.name
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isSelected) Color.parseColor("#818CF8") else MotionTheme.TEXT_PRIMARY)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                headerRow.addView(title)

                if (isSelected) {
                    val badge = TextView(context).apply {
                        text = "AKTIF"
                        textSize = 9f
                        typeface = Typeface.MONOSPACE
                        setTextColor(Color.parseColor("#10B981"))
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        setBackgroundColor(Color.parseColor("#132A20"))
                    }
                    headerRow.addView(badge)
                }

                addView(headerRow)

                val sub = TextView(context).apply {
                    text = item.subtitle
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#818CF8"))
                    setPadding(0, dp(2), 0, dp(2))
                }
                addView(sub)

                val desc = TextView(context).apply {
                    text = item.desc
                    textSize = 11f
                    setTextColor(MotionTheme.TEXT_MUTED)
                }
                addView(desc)

                setOnClickListener {
                    currentMode = item.id
                    onBlendModeSelected(item.id)
                    dismiss()
                }
            }
            listContainer.addView(card)
        }

        scroll.addView(listContainer)
        root.addView(scroll)

        val btnClose = Button(context).apply {
            text = "Tutup"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { topMargin = dp(12) }
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener { dismiss() }
        }
        root.addView(btnClose)

        setContentView(root)
    }
}
