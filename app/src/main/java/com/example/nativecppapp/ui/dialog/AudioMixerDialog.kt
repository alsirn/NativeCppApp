package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class AudioMixerDialog(
    context: Context,
    private var volume: Float,
    private var isMuted: Boolean,
    private val audioTrackName: String,
    private val onVolumeChanged: (volume: Float) -> Unit,
    private val onMuteToggled: (isMuted: Boolean) -> Unit
) : Dialog(context) {

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
            text = "AUDIO STUDIO MIXER & MASTERING"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#F59E0B")) // Studio Amber
        }
        headerBox.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Mixer: $audioTrackName"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerBox.addView(titleView)
        root.addView(headerBox)

        // 1. Status & Mute Card
        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }

        val statusInfo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusTitle = TextView(context).apply {
            text = "Status Kanal Audio"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        statusInfo.addView(statusTitle)

        val statusBadge = TextView(context).apply {
            text = if (isMuted) "STATUS: SENYAP (MUTED)" else "STATUS: AKTIF (ONLINE)"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(if (isMuted) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
        }
        statusInfo.addView(statusBadge)
        statusCard.addView(statusInfo)

        val btnMuteToggle = Button(context).apply {
            text = if (isMuted) "Unmute" else "Mute"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(12), 0, dp(12), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            setTextColor(Color.WHITE)
            setBackgroundColor(if (isMuted) Color.parseColor("#EF4444") else Color.parseColor("#374151"))
            setOnClickListener {
                isMuted = !isMuted
                text = if (isMuted) "Unmute" else "Mute"
                setBackgroundColor(if (isMuted) Color.parseColor("#EF4444") else Color.parseColor("#374151"))
                statusBadge.text = if (isMuted) "STATUS: SENYAP (MUTED)" else "STATUS: AKTIF (ONLINE)"
                statusBadge.setTextColor(if (isMuted) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                onMuteToggled(isMuted)
            }
        }
        statusCard.addView(btnMuteToggle)
        root.addView(statusCard)

        // 2. Volume Fader Section
        val volTitle = TextView(context).apply {
            text = "FADER VOLUME MASTER"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(volTitle)

        val volReadout = TextView(context).apply {
            val pct = (volume * 100).toInt()
            text = "$pct% ${if (pct > 100) "(Gain Boost)" else ""}"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(volReadout)

        val volSeek = SeekBar(context).apply {
            max = 150 // 0% to 150%
            progress = (volume * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        volume = prog / 100f
                        volReadout.text = "$prog% ${if (prog > 100) "(Gain Boost)" else ""}"
                        onVolumeChanged(volume)
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        root.addView(volSeek)

        // 3. Preset Gain Buttons
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(14)
            }
            layoutParams = lp
        }

        val presets = listOf(
            Pair("0%", 0f),
            Pair("50%", 0.5f),
            Pair("100%", 1.0f),
            Pair("150%", 1.5f)
        )
        presets.forEachIndexed { i, p ->
            val btn = Button(context).apply {
                text = p.first
                textSize = 10f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    if (i != presets.size - 1) marginEnd = dp(6)
                }
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    volume = p.second
                    volSeek.progress = (volume * 100).toInt()
                    volReadout.text = "${(volume * 100).toInt()}% ${if (volume > 1f) "(Gain Boost)" else ""}"
                    onVolumeChanged(volume)
                }
            }
            presetRow.addView(btn)
        }
        root.addView(presetRow)

        // 4. Simulated VU Meter
        val vuLabel = TextView(context).apply {
            text = "INDIKATOR PEAK SINYAL (VU METER)"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(vuLabel)

        val vuMeterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#09090B"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)).apply { bottomMargin = dp(16) }
            layoutParams = lp
        }
        val dotColors = listOf(
            "#10B981", "#10B981", "#10B981", "#10B981", "#10B981",
            "#F59E0B", "#F59E0B", "#F59E0B",
            "#EF4444", "#EF4444"
        )
        dotColors.forEachIndexed { i, c ->
            val dot = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor(c))
                alpha = if (i < (volume * 6).toInt().coerceIn(1, 10)) 1.0f else 0.2f
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i != dotColors.size - 1) marginEnd = dp(3)
                }
                layoutParams = lp
            }
            vuMeterRow.addView(dot)
        }
        root.addView(vuMeterRow)

        // Done Button
        val btnDone = Button(context).apply {
            text = "Tutup & Terapkan"
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
            )
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener { dismiss() }
        }
        root.addView(btnDone)

        setContentView(root)
    }
}
