package com.example.nativecppapp.ui.theme

import android.graphics.Color

object MotionTheme {
    // Surface Tiers from Stitch Design System ("Monochrome Motion Mobile")
    val CANVAS_BG = Color.parseColor("#09090B")         // Deep Obsidian
    val SURFACE = Color.parseColor("#131315")           // Base Surface
    val SURFACE_LOW = Color.parseColor("#1C1B1D")       // Track container
    val SURFACE_CONTAINER = Color.parseColor("#201F22") // Modules & items
    val SURFACE_HIGH = Color.parseColor("#2A2A2C")      // Raised modules
    val SURFACE_HIGHEST = Color.parseColor("#353437")   // Highlights

    // Borders & Hairlines
    val BORDER_HAIRLINE = Color.parseColor("#27272A")
    val BORDER_SUBTLE = Color.parseColor("#3F3F46")

    // Typography & Accents
    val TEXT_PRIMARY = Color.parseColor("#F4F4F5")       // Studio White
    val TEXT_SECONDARY = Color.parseColor("#A1A1AA")     // Metallic Silver
    val TEXT_MUTED = Color.parseColor("#71717A")         // Muted Slate

    // Keyframes & Signals
    val PLAYHEAD_COLOR = Color.parseColor("#FFFFFF")
    val KEYFRAME_ACTIVE = Color.parseColor("#FAFAFA")
    val KEYFRAME_INACTIVE = Color.parseColor("#71717A")

    // Curve Graph Tracks
    val CURVE_X = Color.parseColor("#FF453A") // Red for PosX
    val CURVE_Y = Color.parseColor("#30D158") // Green for PosY
    val CURVE_Z = Color.parseColor("#0A84FF") // Blue for PosZ
    val CURVE_HANDLE = Color.parseColor("#A1A1AA")
}
