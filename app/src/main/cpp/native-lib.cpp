#include <jni.h>
#include <string>
#include <vector>
#include <set>
#include <memory>
#include <android/log.h>
#include <android/bitmap.h>

#include "core/animation/CubicBezier.h"
#include "core/animation/AnimatedProperty.h"
#include "core/model/Project.h"
#include "core/model/Layer.h"
#include "core/io/ProjectSerializer.h"
#include "core/render/SoftwarePreviewRenderer.h"
#include "core/render/GpuCompositor.h"
#include <android/native_window_jni.h>
#define TAG "MotionLabCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace motion;

static Project* getProject(jlong ptr) {
    return reinterpret_cast<Project*>(ptr);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeCreateProject(
        JNIEnv* env, jobject /* this */,
        jstring jTitle, jint width, jint height, jdouble fps, jlong durationFrames, jint bgArgb) {
    const char* titleC = env->GetStringUTFChars(jTitle, nullptr);
    std::string titleStr = titleC ? titleC : "Proyek Baru";
    if (titleC) env->ReleaseStringUTFChars(jTitle, titleC);

    auto* project = new Project(titleStr, width, height, fps, durationFrames, static_cast<uint32_t>(bgArgb));
    LOGI("Project created: %s (%dx%d, %.1f fps, %lld frames)", titleStr.c_str(), width, height, fps, (long long)durationFrames);
    return reinterpret_cast<jlong>(project);
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeDestroyProject(
        JNIEnv* /* env */, jobject /* this */, jlong projectPtr) {
    auto* project = getProject(projectPtr);
    if (project) {
        LOGI("Project destroyed");
        delete project;
    }
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetProjectSettings(
        JNIEnv* /* env */, jobject /* this */,
        jlong projectPtr, jint width, jint height, jdouble fps, jlong durationFrames, jint bgArgb) {
    auto* project = getProject(projectPtr);
    if (project) {
        project->setSettings(width, height, fps, durationFrames, static_cast<uint32_t>(bgArgb));
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetProjectJson(
        JNIEnv* env, jobject /* this */, jlong projectPtr) {
    auto* project = getProject(projectPtr);
    if (!project) return env->NewStringUTF("{}");
    std::string json = ProjectSerializer::serializeToJson(*project);
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeLoadProjectJson(
        JNIEnv* env, jobject /* this */, jstring jJson) {
    const char* jsonC = env->GetStringUTFChars(jJson, nullptr);
    if (!jsonC) return 0;
    std::string jsonStr = jsonC;
    env->ReleaseStringUTFChars(jJson, jsonC);

    auto* project = new Project();
    if (ProjectSerializer::deserializeFromJson(jsonStr, *project)) {
        LOGI("Project loaded from JSON: %s, layers count: %zu", project->title.c_str(), project->layers.size());
        return reinterpret_cast<jlong>(project);
    }
    delete project;
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeAddShapeLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jstring jName, jint shapeType,
        jlong startFrame, jlong durationFrames, jint fillColor, jdouble width, jdouble height) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    const char* nameC = env->GetStringUTFChars(jName, nullptr);
    std::string idStr = idC ? idC : "shape_layer";
    std::string nameStr = nameC ? nameC : "Bentuk";
    if (idC) env->ReleaseStringUTFChars(jId, idC);
    if (nameC) env->ReleaseStringUTFChars(jName, nameC);

    auto layer = std::make_shared<Layer>(idStr, nameStr, LayerType::SHAPE, startFrame, durationFrames);
    layer->shapeData.type = static_cast<ShapeType>(shapeType);
    layer->shapeData.width = width;
    layer->shapeData.height = height;
    layer->shapeData.radius = width * 0.5;
    layer->shapeData.fillColor = static_cast<uint32_t>(fillColor);

    project->addLayer(layer);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeAddMediaLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jstring jName, jstring jFilePath,
        jboolean isVideo, jlong startFrame, jlong durationFrames) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    const char* nameC = env->GetStringUTFChars(jName, nullptr);
    const char* pathC = env->GetStringUTFChars(jFilePath, nullptr);
    std::string idStr = idC ? idC : "media_layer";
    std::string nameStr = nameC ? nameC : "Media";
    std::string pathStr = pathC ? pathC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);
    if (nameC) env->ReleaseStringUTFChars(jName, nameC);
    if (pathC) env->ReleaseStringUTFChars(jFilePath, pathC);

    auto layer = std::make_shared<Layer>(idStr, nameStr, LayerType::MEDIA, startFrame, durationFrames);
    layer->mediaData.filePath = pathStr;
    layer->mediaData.type = isVideo ? MediaType::VIDEO : MediaType::IMAGE;

    if (!isVideo && !pathStr.empty()) {
        int w = 0, h = 0;
        if (SoftwarePreviewRenderer::getImageDimensions(pathStr, w, h)) {
            layer->mediaData.originalWidth = w;
            layer->mediaData.originalHeight = h;
        }
    }

    project->addLayer(layer);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeAddAudioLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jstring jName, jstring jFilePath,
        jlong startFrame, jlong durationFrames) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    const char* nameC = env->GetStringUTFChars(jName, nullptr);
    const char* pathC = env->GetStringUTFChars(jFilePath, nullptr);
    std::string idStr = idC ? idC : "audio_layer";
    std::string nameStr = nameC ? nameC : "Audio";
    std::string pathStr = pathC ? pathC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);
    if (nameC) env->ReleaseStringUTFChars(jName, nameC);
    if (pathC) env->ReleaseStringUTFChars(jFilePath, pathC);

    auto layer = std::make_shared<Layer>(idStr, nameStr, LayerType::AUDIO, startFrame, durationFrames);
    layer->audioData.filePath = pathStr;

    project->addLayer(layer);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeRemoveLayer(
        JNIEnv* env, jobject /* this */, jlong projectPtr, jstring jLayerId) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    return project->removeLayer(idStr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetLayerVisibility(
        JNIEnv* env, jobject /* this */, jlong projectPtr, jstring jLayerId, jboolean isVisible) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;
    layer->isVisible = isVisible;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetLayerSpan(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jlong startFrame, jlong durationFrames) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    return project->setLayerSpan(idStr, startFrame, durationFrames) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSplitLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jlong splitFrame) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    return project->splitLayer(idStr, splitFrame) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeDuplicateLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto dup = project->duplicateLayer(idStr);
    return dup ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeReorderLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jint newIndex) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    project->reorderLayer(idStr, newIndex);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetLayerMotionBlur(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jboolean enabled, jdouble strength) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    layer->motionBlurEnabled = (enabled == JNI_TRUE);
    layer->motionBlurStrength = strength;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetLayerMotionBlurEnabled(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    return (layer && layer->motionBlurEnabled) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetLayerMotionBlurStrength(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId) {
    auto* project = getProject(projectPtr);
    if (!project) return 1.0;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    return layer ? layer->motionBlurStrength : 1.0;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeAddOrUpdateKeyframe(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jstring jPropName,
        jlong frame, jdouble value, jint interpolation,
        jdouble ox, jdouble oy, jdouble ix, jdouble iy) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    const char* propC = env->GetStringUTFChars(jPropName, nullptr);
    std::string idStr = idC ? idC : "";
    std::string propStr = propC ? propC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);
    if (propC) env->ReleaseStringUTFChars(jPropName, propC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    auto* prop = layer->transform.getProperty(propStr);
    if (!prop) return JNI_FALSE;

    prop->addOrUpdateKeyframe(frame, value, static_cast<InterpolationType>(interpolation), ox, oy, ix, iy);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeRemoveKeyframe(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jstring jPropName, jlong frame) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    const char* propC = env->GetStringUTFChars(jPropName, nullptr);
    std::string idStr = idC ? idC : "";
    std::string propStr = propC ? propC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);
    if (propC) env->ReleaseStringUTFChars(jPropName, propC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    auto* prop = layer->transform.getProperty(propStr);
    if (!prop) return JNI_FALSE;

    return prop->removeKeyframe(frame) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetLayerKeyframeFrames(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId) {
    auto* project = getProject(projectPtr);
    if (!project) return env->NewLongArray(0);

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return env->NewLongArray(0);

    std::set<int64_t> frameSet;
    const char* props[] = {"posX", "posY", "posZ", "scaleX", "scaleY", "rotation", "opacity"};
    for (const char* p : props) {
        auto* prop = layer->transform.getProperty(p);
        if (prop) {
            for (const auto& kf : prop->getKeyframes()) {
                frameSet.insert(kf.frame);
            }
        }
    }

    jlongArray res = env->NewLongArray(static_cast<jsize>(frameSet.size()));
    std::vector<jlong> v(frameSet.begin(), frameSet.end());
    env->SetLongArrayRegion(res, 0, static_cast<jsize>(v.size()), v.data());
    return res;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeMoveKeyframe(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jlong oldFrame, jlong newFrame) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    const char* props[] = {"posX", "posY", "posZ", "scaleX", "scaleY", "rotation", "opacity"};
    bool movedAny = false;
    for (const char* p : props) {
        auto* prop = layer->transform.getProperty(p);
        if (prop) {
            Keyframe kf;
            if (prop->findKeyframe(oldFrame, kf)) {
                prop->removeKeyframe(oldFrame);
                kf.frame = newFrame;
                prop->addOrUpdateKeyframe(kf);
                movedAny = true;
            }
        }
    }
    return movedAny ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeDeleteKeyframeAtFrame(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jlong frame) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    const char* props[] = {"posX", "posY", "posZ", "scaleX", "scaleY", "rotation", "opacity"};
    bool deletedAny = false;
    for (const char* p : props) {
        auto* prop = layer->transform.getProperty(p);
        if (prop) {
            if (prop->removeKeyframe(frame)) {
                deletedAny = true;
            }
        }
    }
    return deletedAny ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetKeyframeValues(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jlong frame) {
    auto* project = getProject(projectPtr);
    if (!project) return nullptr;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return nullptr;

    // Evaluated values at this frame
    TransformState state = layer->transform.evaluate(frame);
    // Find interpolation of posX if available, default to BEZIER (0)
    int interp = 0;
    auto* posX = layer->transform.getProperty("posX");
    if (posX) {
        Keyframe kf;
        if (posX->findKeyframe(frame, kf)) {
            interp = static_cast<int>(kf.interpolation);
        }
    }

    jdoubleArray res = env->NewDoubleArray(8);
    jdouble elements[8] = {
        state.posX, state.posY, state.posZ,
        state.scaleX, state.scaleY,
        state.rotation, state.opacity,
        static_cast<double>(interp)
    };
    env->SetDoubleArrayRegion(res, 0, 8, elements);
    return res;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeEvaluateLayerTransform(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jLayerId, jdouble frame, jdoubleArray outArray) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jLayerId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jLayerId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    TransformState state = layer->transform.evaluate(frame);
    jdouble elements[7] = {
        state.posX, state.posY, state.posZ,
        state.scaleX, state.scaleY,
        state.rotation, state.opacity
    };
    env->SetDoubleArrayRegion(outArray, 0, 7, elements);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeRenderFrameToBitmap(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jlong frame, jobject jBitmap) {
    auto* project = getProject(projectPtr);
    if (!project || !jBitmap) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, jBitmap, &info) < 0) {
        LOGE("Failed to get Bitmap info");
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, jBitmap, &pixels) < 0) {
        LOGE("Failed to lock Bitmap pixels");
        return JNI_FALSE;
    }

    SoftwarePreviewRenderer::renderFrame(*project, frame, static_cast<uint32_t*>(pixels), info.width, info.height);

    AndroidBitmap_unlockPixels(env, jBitmap);
    return JNI_TRUE;
}

// Solve Bezier curve directly for UI Graph Editor rendering preview
JNIEXPORT jdouble JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSolveCubicBezier(
        JNIEnv* /* env */, jobject /* this */,
        jdouble x1, jdouble y1, jdouble x2, jdouble y2, jdouble x) {
    CubicBezier bezier(x1, y1, x2, y2);
    return bezier.solve(x);
}

// --- GPU Compositor JNI ---

JNIEXPORT jlong JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeCreateGpuCompositor(
        JNIEnv* /* env */, jobject /* this */) {
    auto* compositor = new GpuCompositor();
    return reinterpret_cast<jlong>(compositor);
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeInitializeGpuCompositor(
        JNIEnv* env, jobject /* this */, jlong compositorPtr, jobject jSurface, jint width, jint height) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    if (compositor && jSurface) {
        ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
        if (window) {
            compositor->initialize(window, width, height);
            // Window is managed internally now
        }
    }
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeUpdateGpuViewport(
        JNIEnv* /* env */, jobject /* this */, jlong compositorPtr, jfloat left, jfloat top, jfloat width, jfloat height) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    if (compositor) {
        compositor->setViewport(left, top, width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeRenderFrameGpu(
        JNIEnv* /* env */, jobject /* this */, jlong compositorPtr, jlong projectPtr, jdouble frame) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    auto* project = getProject(projectPtr);
    if (compositor && project) {
        compositor->renderFrame(*project, static_cast<float>(frame));
    }
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetGpuPresentationTime(
        JNIEnv* /* env */, jobject /* this */, jlong compositorPtr, jlong nsecs) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    if (compositor) {
        compositor->setPresentationTime(static_cast<int64_t>(nsecs));
    }
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetGpuShaderEffects(
        JNIEnv* /* env */, jobject /* this */, jlong compositorPtr,
        jfloat brightness, jfloat contrast, jfloat saturation, jfloat vignette, jint filterMode) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    if (compositor) {
        compositor->setShaderEffects(brightness, contrast, saturation, vignette, filterMode);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeAddTextLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jstring jName, jstring jText,
        jlong startFrame, jlong durationFrames, jint textColor, jdouble fontSize) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    const char* nameC = env->GetStringUTFChars(jName, nullptr);
    const char* textC = env->GetStringUTFChars(jText, nullptr);
    std::string idStr = idC ? idC : "text_layer";
    std::string nameStr = nameC ? nameC : "Teks";
    std::string textStr = textC ? textC : "MotionLab";
    if (idC) env->ReleaseStringUTFChars(jId, idC);
    if (nameC) env->ReleaseStringUTFChars(jName, nameC);
    if (textC) env->ReleaseStringUTFChars(jText, textC);

    auto layer = std::make_shared<Layer>(idStr, nameStr, LayerType::TEXT, startFrame, durationFrames);
    layer->textData.text = textStr;
    layer->textData.textColor = static_cast<uint32_t>(textColor);
    layer->textData.fontSize = fontSize;

    project->addLayer(layer);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeUpdateShapeLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jint fillColor, jdouble width, jdouble height) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer || layer->type != LayerType::SHAPE) return JNI_FALSE;

    layer->shapeData.fillColor = static_cast<uint32_t>(fillColor);
    layer->shapeData.width = width;
    layer->shapeData.height = height;
    layer->shapeData.radius = width * 0.5;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeUpdateTextLayer(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jstring jText, jint textColor, jdouble fontSize) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    const char* textC = env->GetStringUTFChars(jText, nullptr);
    std::string idStr = idC ? idC : "";
    std::string textStr = textC ? textC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);
    if (textC) env->ReleaseStringUTFChars(jText, textC);

    auto layer = project->getLayer(idStr);
    if (!layer || layer->type != LayerType::TEXT) return JNI_FALSE;

    layer->textData.text = textStr;
    layer->textData.textColor = static_cast<uint32_t>(textColor);
    layer->textData.fontSize = fontSize;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetLayerBlendMode(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jint blendMode) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    layer->blendMode = static_cast<BlendMode>(blendMode);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetLayerBlendMode(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId) {
    auto* project = getProject(projectPtr);
    if (!project) return 0;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return 0;

    return static_cast<jint>(layer->blendMode);
}

JNIEXPORT jboolean JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeSetLayerTransition(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jboolean isInTransition, jint transitionType, jlong durationFrames) {
    auto* project = getProject(projectPtr);
    if (!project) return JNI_FALSE;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return JNI_FALSE;

    auto tType = static_cast<TransitionType>(transitionType);
    if (isInTransition) {
        layer->inTransition = tType;
        layer->inTransitionDuration = durationFrames;
    } else {
        layer->outTransition = tType;
        layer->outTransitionDuration = durationFrames;
    }
    return JNI_TRUE;
}

JNIEXPORT jlongArray JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeGetLayerTransition(
        JNIEnv* env, jobject /* this */,
        jlong projectPtr, jstring jId, jboolean isInTransition) {
    auto* project = getProject(projectPtr);
    jlongArray res = env->NewLongArray(2);
    if (!project) return res;

    const char* idC = env->GetStringUTFChars(jId, nullptr);
    std::string idStr = idC ? idC : "";
    if (idC) env->ReleaseStringUTFChars(jId, idC);

    auto layer = project->getLayer(idStr);
    if (!layer) return res;

    jlong vals[2];
    if (isInTransition) {
        vals[0] = static_cast<jlong>(layer->inTransition);
        vals[1] = layer->inTransitionDuration;
    } else {
        vals[0] = static_cast<jlong>(layer->outTransition);
        vals[1] = layer->outTransitionDuration;
    }
    env->SetLongArrayRegion(res, 0, 2, vals);
    return res;
}

JNIEXPORT void JNICALL
Java_com_example_nativecppapp_NativeEngineBridge_nativeDestroyGpuCompositor(
        JNIEnv* /* env */, jobject /* this */, jlong compositorPtr) {
    auto* compositor = reinterpret_cast<GpuCompositor*>(compositorPtr);
    if (compositor) {
        delete compositor;
    }
}

} // extern "C"