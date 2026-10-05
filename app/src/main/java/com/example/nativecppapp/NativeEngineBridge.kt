package com.example.nativecppapp

import android.graphics.Bitmap

object NativeEngineBridge {

    init {
        System.loadLibrary("native-lib")
    }

    enum class InterpolationMode(val value: Int) {
        LINEAR(0),
        BEZIER(1),
        HOLD(2),
        AUTO_BEZIER(3),
        CONTINUOUS_BEZIER(4)
    }

    enum class ShapeType(val value: Int) {
        RECTANGLE(0),
        CIRCLE(1),
        STAR(2),
        POLYGON(3),
        LINE(4)
    }

    data class TransformState(
        val posX: Double = 0.0,
        val posY: Double = 0.0,
        val posZ: Double = 0.0,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0,
        val rotation: Double = 0.0,
        val opacity: Double = 1.0
    )

    data class KeyframeData(
        val frame: Long,
        val value: Double,
        val interpolation: InterpolationMode = InterpolationMode.BEZIER,
        val outHandleX: Double = 0.42,
        val outHandleY: Double = 0.0,
        val inHandleX: Double = 0.58,
        val inHandleY: Double = 1.0
    )

    // JNI Native Declarations
    external fun nativeCreateProject(
        title: String,
        width: Int,
        height: Int,
        fps: Double,
        durationFrames: Long,
        bgArgb: Int
    ): Long

    external fun nativeDestroyProject(projectPtr: Long)

    external fun nativeSetProjectSettings(
        projectPtr: Long,
        width: Int,
        height: Int,
        fps: Double,
        durationFrames: Long,
        bgArgb: Int
    )

    external fun nativeGetProjectJson(projectPtr: Long): String

    external fun nativeLoadProjectJson(json: String): Long

    external fun nativeAddShapeLayer(
        projectPtr: Long,
        id: String,
        name: String,
        shapeType: Int,
        startFrame: Long,
        durationFrames: Long,
        fillColor: Int,
        width: Double,
        height: Double
    ): Boolean

    external fun nativeAddMediaLayer(
        projectPtr: Long,
        id: String,
        name: String,
        filePath: String,
        isVideo: Boolean,
        startFrame: Long,
        durationFrames: Long
    ): Boolean

    external fun nativeAddAudioLayer(
        projectPtr: Long,
        id: String,
        name: String,
        filePath: String,
        startFrame: Long,
        durationFrames: Long
    ): Boolean

    external fun nativeAddTextLayer(
        projectPtr: Long,
        id: String,
        name: String,
        text: String,
        startFrame: Long,
        durationFrames: Long,
        textColor: Int,
        fontSize: Double
    ): Boolean

    external fun nativeUpdateShapeLayer(
        projectPtr: Long,
        layerId: String,
        fillColor: Int,
        width: Double,
        height: Double
    ): Boolean

    external fun nativeUpdateTextLayer(
        projectPtr: Long,
        layerId: String,
        text: String,
        textColor: Int,
        fontSize: Double
    ): Boolean

    external fun nativeRemoveLayer(projectPtr: Long, layerId: String): Boolean

    external fun nativeSetLayerVisibility(projectPtr: Long, layerId: String, isVisible: Boolean): Boolean

    external fun nativeSetLayerSpan(projectPtr: Long, layerId: String, startFrame: Long, durationFrames: Long): Boolean

    external fun nativeSplitLayer(projectPtr: Long, layerId: String, splitFrame: Long): Boolean

    external fun nativeDuplicateLayer(projectPtr: Long, layerId: String): Boolean

    external fun nativeReorderLayer(projectPtr: Long, layerId: String, newIndex: Int): Boolean

    external fun nativeSetLayerBlendMode(projectPtr: Long, layerId: String, blendMode: Int): Boolean
    external fun nativeGetLayerBlendMode(projectPtr: Long, layerId: String): Int

    external fun nativeSetLayerTransition(
        projectPtr: Long,
        layerId: String,
        isInTransition: Boolean,
        transitionType: Int,
        durationFrames: Long
    ): Boolean

    external fun nativeGetLayerTransition(
        projectPtr: Long,
        layerId: String,
        isInTransition: Boolean
    ): LongArray

    external fun nativeAddOrUpdateKeyframe(
        projectPtr: Long,
        layerId: String,
        propName: String,
        frame: Long,
        value: Double,
        interpolation: Int,
        ox: Double,
        oy: Double,
        ix: Double,
        iy: Double
    ): Boolean

    external fun nativeRemoveKeyframe(
        projectPtr: Long,
        layerId: String,
        propName: String,
        frame: Long
    ): Boolean

    external fun nativeGetLayerKeyframeFrames(
        projectPtr: Long,
        layerId: String
    ): LongArray

    external fun nativeMoveKeyframe(
        projectPtr: Long,
        layerId: String,
        oldFrame: Long,
        newFrame: Long
    ): Boolean

    external fun nativeDeleteKeyframeAtFrame(
        projectPtr: Long,
        layerId: String,
        frame: Long
    ): Boolean

    external fun nativeSetLayerMotionBlur(
        projectPtr: Long,
        layerId: String,
        enabled: Boolean,
        strength: Double
    ): Boolean

    external fun nativeGetLayerMotionBlurEnabled(
        projectPtr: Long,
        layerId: String
    ): Boolean

    external fun nativeGetLayerMotionBlurStrength(
        projectPtr: Long,
        layerId: String
    ): Double

    external fun nativeGetKeyframeValues(
        projectPtr: Long,
        layerId: String,
        frame: Long
    ): DoubleArray?

    external fun nativeEvaluateLayerTransform(
        projectPtr: Long,
        layerId: String,
        frame: Double,
        outArray: DoubleArray
    ): Boolean

    external fun nativeRenderFrameToBitmap(
        projectPtr: Long,
        frame: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeSolveCubicBezier(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        x: Double
    ): Double

    // GPU Compositor
    external fun nativeCreateGpuCompositor(): Long
    external fun nativeInitializeGpuCompositor(compositorPtr: Long, surface: android.view.Surface, width: Int, height: Int)
    external fun nativeUpdateGpuViewport(compositorPtr: Long, left: Float, top: Float, width: Float, height: Float)
    external fun nativeSetGpuPresentationTime(compositorPtr: Long, nsecs: Long)
    external fun nativeSetGpuShaderEffects(
        compositorPtr: Long,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        vignette: Float,
        filterMode: Int
    )
    external fun nativeRenderFrameGpu(compositorPtr: Long, projectPtr: Long, frame: Double)
    external fun nativeDestroyGpuCompositor(compositorPtr: Long)


    // Kotlin Convenience Wrappers
    fun evaluateTransform(projectPtr: Long, layerId: String, frame: Double): TransformState? {
        val out = DoubleArray(7)
        val success = nativeEvaluateLayerTransform(projectPtr, layerId, frame, out)
        if (!success) return null
        return TransformState(
            posX = out[0],
            posY = out[1],
            posZ = out[2],
            scaleX = out[3],
            scaleY = out[4],
            rotation = out[5],
            opacity = out[6]
        )
    }

    fun addKeyframe(
        projectPtr: Long,
        layerId: String,
        propName: String,
        kf: KeyframeData
    ): Boolean {
        return nativeAddOrUpdateKeyframe(
            projectPtr,
            layerId,
            propName,
            kf.frame,
            kf.value,
            kf.interpolation.value,
            kf.outHandleX,
            kf.outHandleY,
            kf.inHandleX,
            kf.inHandleY
        )
    }
}
