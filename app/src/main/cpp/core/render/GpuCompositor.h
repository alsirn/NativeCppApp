#ifndef MOTIONLAB_GPU_COMPOSITOR_H
#define MOTIONLAB_GPU_COMPOSITOR_H

#include <android/native_window.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <vector>
#include <memory>
#include "../model/Project.h"

class GpuCompositor {
public:
    GpuCompositor();
    ~GpuCompositor();

    void initialize(ANativeWindow* window, int width, int height);
    void destroy();
    void renderFrame(const motion::Project& project, float currentFrame);
    void setViewport(float left, float top, float width, float height);
    void setPresentationTime(int64_t nsecs);
    void setShaderEffects(float brightness, float contrast, float saturation, float vignette, int filterMode);

private:
    ANativeWindow* mWindow = nullptr;
    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext = EGL_NO_CONTEXT;

    int mSurfaceWidth = 0;
    int mSurfaceHeight = 0;
    
    float mViewLeft = 0.0f;
    float mViewTop = 0.0f;
    float mViewWidth = 0.0f;
    float mViewHeight = 0.0f;

    // Visual Shader Effects
    float mBrightness = 0.0f;
    float mContrast = 1.0f;
    float mSaturation = 1.0f;
    float mVignette = 0.0f;
    int mFilterMode = 0; // 0=Normal, 1=Invert, 2=Grayscale, 3=Sepia

    GLuint mShaderProgram = 0;
    GLuint mVbo = 0;
    GLuint mFrameTexture = 0;
    std::vector<uint32_t> mFramePixelBuffer;

    bool initEGL();
    void destroyEGL();
    
    bool initGL();
    GLuint loadShader(GLenum type, const char* shaderSrc);
};

#endif // MOTIONLAB_GPU_COMPOSITOR_H
