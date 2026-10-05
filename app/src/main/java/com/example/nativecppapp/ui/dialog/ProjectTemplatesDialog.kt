package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class ProjectTemplatesDialog(
    context: Context,
    private val onTemplateSelected: (
        templateId: String,
        title: String,
        width: Int,
        height: Int,
        fps: Double,
        durationFrames: Long
    ) -> Unit
) : Dialog(context) {

    data class TemplateInfo(
        val id: String,
        val title: String,
        val ratioTag: String,
        val desc: String,
        val width: Int,
        val height: Int,
        val fps: Double,
        val durationFrames: Long,
        val accentColor: String
    )

    private val templates = listOf(
        TemplateInfo(
            id = "TIKTOK_VIRAL",
            title = "TikTok / Reels Pop Intro",
            ratioTag = "9:16 • 1080×1920",
            desc = "Animasi vertikal kekinian dengan teks pop-in zoom, cyber bar sweep, dan shape pantul dinamis 60fps.",
            width = 1080,
            height = 1920,
            fps = 60.0,
            durationFrames = 150,
            accentColor = "#06B6D4"
        ),
        TemplateInfo(
            id = "YOUTUBE_LOWER_THIRD",
            title = "YouTube Pro Lower-Third",
            ratioTag = "16:9 • 1920×1080",
            desc = "Format standar video lanskap untuk baris nama pembicara, sub-judul, dan kartu latar elegan dengan transisi geser halus.",
            width = 1920,
            height = 1080,
            fps = 60.0,
            durationFrames = 180,
            accentColor = "#F59E0B"
        ),
        TemplateInfo(
            id = "LOGO_REVEAL",
            title = "Cinematic Logo Stinger",
            ratioTag = "1:1 • 1080×1080",
            desc = "Reveal logo diamond berputar dengan cincin pendar aditif dan tipografi brand minimalis modern.",
            width = 1080,
            height = 1080,
            fps = 60.0,
            durationFrames = 120,
            accentColor = "#818CF8"
        ),
        TemplateInfo(
            id = "CYBERPUNK_GLITCH",
            title = "Cyberpunk Glitch Story",
            ratioTag = "9:16 • 1080×1920",
            desc = "Teks glitch futuristik dua baris dengan shader RGB Channel Split, kontras tinggi, dan nuansa sci-fi.",
            width = 1080,
            height = 1920,
            fps = 60.0,
            durationFrames = 180,
            accentColor = "#F43F5E"
        )
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
            text = "GALERI TEMPLATE MOTIONLAB"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Pilih Template Siap Pakai"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerBox.addView(titleView)
        root.addView(headerBox)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (item in templates) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                layoutParams = lp

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tTitle = TextView(context).apply {
                    text = item.title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(item.accentColor))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                headerRow.addView(tTitle)

                val badge = TextView(context).apply {
                    text = item.ratioTag
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(MotionTheme.TEXT_MUTED)
                }
                headerRow.addView(badge)
                addView(headerRow)

                val descView = TextView(context).apply {
                    text = item.desc
                    textSize = 11f
                    setTextColor(MotionTheme.TEXT_SECONDARY)
                    setPadding(0, dp(6), 0, dp(10))
                }
                addView(descView)

                val btnApply = Button(context).apply {
                    text = "Gunakan Template Ini"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    minHeight = 0
                    minimumHeight = 0
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor(item.accentColor))
                    setOnClickListener {
                        onTemplateSelected(
                            item.id,
                            item.title,
                            item.width,
                            item.height,
                            item.fps,
                            item.durationFrames
                        )
                        dismiss()
                    }
                }
                addView(btnApply)
            }
            listLayout.addView(card)
        }

        scroll.addView(listLayout)
        root.addView(scroll)

        val btnCancel = Button(context).apply {
            text = "Tutup"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            ).apply { topMargin = dp(10) }
            setTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }
        root.addView(btnCancel)

        setContentView(root)
    }
}
