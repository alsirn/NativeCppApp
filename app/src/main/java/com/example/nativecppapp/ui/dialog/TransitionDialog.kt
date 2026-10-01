package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class TransitionDialog(
    context: Context,
    private val layerName: String,
    private var inType: Int,
    private var inDurationFrames: Long,
    private var outType: Int,
    private var outDurationFrames: Long,
    private val onTransitionUpdated: (
        inType: Int,
        inDurationFrames: Long,
        outType: Int,
        outDurationFrames: Long
    ) -> Unit
) : Dialog(context) {

    data class TransOption(val id: Int, val label: String)

    private val inOptions = listOf(
        TransOption(0, "Tidak Ada"),
        TransOption(1, "Fade In"),
        TransOption(2, "Geser Kiri"),
        TransOption(3, "Geser Kanan"),
        TransOption(4, "Geser Atas"),
        TransOption(5, "Geser Bawah"),
        TransOption(6, "Zoom Masuk"),
        TransOption(7, "Spin Masuk")
    )

    private val outOptions = listOf(
        TransOption(0, "Tidak Ada"),
        TransOption(1, "Fade Out"),
        TransOption(2, "Geser Kiri"),
        TransOption(3, "Geser Kanan"),
        TransOption(4, "Geser Atas"),
        TransOption(5, "Geser Bawah"),
        TransOption(6, "Zoom Keluar"),
        TransOption(7, "Spin Keluar")
    )

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }

        // Header
        val tagView = TextView(context).apply {
            text = "EFEK TRANSISI & ANIMASI OTOMATIS"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Transisi: $layerName"
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

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ================= SECTION 1: IN TRANSITION =================
        val inHeader = TextView(context).apply {
            text = "TRANSISI MASUK (IN-TRANSITION)"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(inHeader)

        val inButtonsLayout = createGridButtons(inOptions, inType) { selectedId ->
            inType = selectedId
            if (inDurationFrames == 0L && selectedId != 0) {
                inDurationFrames = 30L // Default 0.5s
            }
            dispatch()
        }
        content.addView(inButtonsLayout)

        val inDurLabel = TextView(context).apply {
            text = String.format("Durasi Masuk: %.2fs (%d frame)", inDurationFrames / 60.0, inDurationFrames)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, dp(6), 0, dp(2))
        }
        content.addView(inDurLabel)

        val inSeek = SeekBar(context).apply {
            max = 120 // 0 to 2 seconds (120 frames at 60fps)
            progress = inDurationFrames.toInt().coerceIn(0, 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        inDurationFrames = prog.toLong()
                        inDurLabel.text = String.format("Durasi Masuk: %.2fs (%d frame)", prog / 60.0, prog)
                        dispatch()
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        content.addView(inSeek)

        // Divider
        val divider = View(context).apply {
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            }
        }
        content.addView(divider)

        // ================= SECTION 2: OUT TRANSITION =================
        val outHeader = TextView(context).apply {
            text = "TRANSISI KELUAR (OUT-TRANSITION)"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(outHeader)

        val outButtonsLayout = createGridButtons(outOptions, outType) { selectedId ->
            outType = selectedId
            if (outDurationFrames == 0L && selectedId != 0) {
                outDurationFrames = 30L // Default 0.5s
            }
            dispatch()
        }
        content.addView(outButtonsLayout)

        val outDurLabel = TextView(context).apply {
            text = String.format("Durasi Keluar: %.2fs (%d frame)", outDurationFrames / 60.0, outDurationFrames)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, dp(6), 0, dp(2))
        }
        content.addView(outDurLabel)

        val outSeek = SeekBar(context).apply {
            max = 120
            progress = outDurationFrames.toInt().coerceIn(0, 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        outDurationFrames = prog.toLong()
                        outDurLabel.text = String.format("Durasi Keluar: %.2fs (%d frame)", prog / 60.0, prog)
                        dispatch()
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        content.addView(outSeek)

        scroll.addView(content)
        root.addView(scroll)

        // Selesai Button
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
            ).apply { topMargin = dp(14) }
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener { dismiss() }
        }
        root.addView(btnDone)

        setContentView(root)
    }

    private fun dispatch() {
        onTransitionUpdated(inType, inDurationFrames, outType, outDurationFrames)
    }

    private fun createGridButtons(
        options: List<TransOption>,
        activeId: Int,
        onSelected: (Int) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val buttonList = mutableListOf<Button>()
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        }

        for (i in options.indices) {
            val opt = options[i]
            val btn = Button(context).apply {
                text = opt.label
                textSize = 9f
                typeface = Typeface.MONOSPACE
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(4), dp(8), dp(4), dp(8))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    marginEnd = dp(3)
                }

                fun updateVisual(selected: Boolean) {
                    if (selected) {
                        setBackgroundColor(Color.parseColor("#818CF8"))
                        setTextColor(Color.WHITE)
                    } else {
                        setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                        setTextColor(MotionTheme.TEXT_PRIMARY)
                    }
                }
                updateVisual(opt.id == activeId)

                setOnClickListener {
                    for (b in buttonList) {
                        b.setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                        b.setTextColor(MotionTheme.TEXT_PRIMARY)
                    }
                    setBackgroundColor(Color.parseColor("#818CF8"))
                    setTextColor(Color.WHITE)
                    onSelected(opt.id)
                }
            }
            buttonList.add(btn)
            if (i < 4) row1.addView(btn) else row2.addView(btn)
        }

        container.addView(row1)
        container.addView(row2)
        return container
    }
}
