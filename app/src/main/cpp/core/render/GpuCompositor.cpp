#include "GpuCompositor.h"
#include "SoftwarePreviewRenderer.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <EGL/eglext.h>

#define LOG_TAG "GpuCompositor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef EGL_RECORDABLE_ANDROID
#define EGL_RECORDABLE_ANDROID 0x3142
#endif

// Fullscreen Quad Vertex Shader (Universal GLES 2.0 / 3.0 compatible)
static const char* VERTEX_SHADER = R"(
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        vTexCoord = aTexCoord;
        gl_Position = aPosition;
    }
)";

// Texture Quad + Post Processing Shader (Universal GLES 2.0 / 3.0 compatible)
static const char* FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    uniform float uBrightness;
    uniform float uContrast;
    uniform float uSaturation;
    uniform float uVignette;
    uniform int uFilterMode;

    void main() {
        vec4 col = texture2D(uTexture, vTexCoord);

        // Visual Filter Presets
        if (uFilterMode == 1) { // Invert
            col.rgb = vec3(1.0) - col.rgb;
        } else if (uFilterMode == 2) { // Monochrome Studio
            float gray = dot(col.rgb, vec3(0.299, 0.587, 0.114));
            col.rgb = vec3(gray);
        } else if (uFilterMode == 3) { // Sepia Vintage
            float r = (col.r * 0.393) + (col.g * 0.769) + (col.b * 0.189);
            float g = (col.r * 0.349) + (col.g * 0.686) + (col.b * 0.168);
            float b = (col.r * 0.272) + (col.g * 0.534) + (col.b * 0.131);
            col.rgb = vec3(r, g, b);
        } else if (uFilterMode == 4) { // Chromatic Aberration / RGB Split
            vec2 offset = vec2(0.006, 0.0);
            float r = texture2D(uTexture, vTexCoord + offset).r;
            float g = col.g;
            float b = texture2D(uTexture, vTexCoord - offset).b;
            col.rgb = vec3(r, g, b);
        } else if (uFilterMode == 5) { // Cyberpunk Neon Glow
            vec2 offset = vec2(0.004, 0.004);
            vec4 glow = (texture2D(uTexture, vTexCoord + offset) +
                         texture2D(uTexture, vTexCoord - offset) +
                         texture2D(uTexture, vTexCoord + vec2(-offset.x, offset.y)) +
                         texture2D(uTexture, vTexCoord + vec2(offset.x, -offset.y))) * 0.25;
            col.rgb = mix(col.rgb, vec3(col.r * 1.3, col.g * 0.8, col.b * 1.5), 0.4) + glow.rgb * 0.35;
        }

        // Brightness & Contrast
        col.rgb += vec3(uBrightness);
        col.rgb = (col.rgb - 0.5) * uContrast + 0.5;

        // Saturation
        float lum = dot(col.rgb, vec3(0.299, 0.587, 0.114));
        col.rgb = mix(vec3(lum), col.rgb, uSaturation);

        // Vignette
        if (uVignette > 0.0) {
            vec2 uv = (vTexCoord - 0.5) * 2.0;
            float dist = length(uv);
            float vig = smoothstep(0.9, 0.9 - (uVignette * 0.6), dist);
            col.rgb *= vig;
        }

        gl_FragColor = clamp(col, 0.0, 1.0);
    }
)";

GpuCompositor::GpuCompositor() {
}

GpuCompositor::~GpuCompositor() {
    destroy();
}

void GpuCompositor::setPresentationTime(int64_t nsecs) {
    if (mDisplay == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE) {
        return;
    }
    typedef khronos_int64_t EGLnsecsANDROID;
    typedef EGLBoolean (EGLAPIENTRYP PFNEGLPRESENTATIONTIMEANDROIDPROC)(EGLDisplay dpy, EGLSurface sur, EGLnsecsANDROID time);
    static auto pfnPresentationTime = (PFNEGLPRESENTATIONTIMEANDROIDPROC)eglGetProcAddress("eglPresentationTimeANDROID");
    if (pfnPresentationTime) {
        pfnPresentationTime(mDisplay, mSurface, static_cast<EGLnsecsANDROID>(nsecs));
    }
}

void GpuCompositor::setShaderEffects(float brightness, float contrast, float saturation, float vignette, int filterMode) {
    mBrightness = brightness;
    mContrast = contrast;
    mSaturation = saturation;
    mVignette = vignette;
    mFilterMode = filterMode;
}

void GpuCompositor::initialize(ANativeWindow* window, int width, int height) {
    mWindow = window;
    mSurfaceWidth = width;
    mSurfaceHeight = height;

    if (initEGL()) {
        initGL();
        setViewport(0.0f, 0.0f, static_cast<float>(width), static_cast<float>(height));
    }
}

void GpuCompositor::destroy() {
    if (mFrameTexture != 0) {
        glDeleteTextures(1, &mFrameTexture);
        mFrameTexture = 0;
    }
    if (mVbo != 0) {
        glDeleteBuffers(1, &mVbo);
        mVbo = 0;
    }
    if (mShaderProgram != 0) {
        glDeleteProgram(mShaderProgram);
        mShaderProgram = 0;
    }
    destroyEGL();
    mWindow = nullptr;
}

void GpuCompositor::setViewport(float left, float top, float width, float height) {
    mViewLeft = left;
    mViewTop = top;
    mViewWidth = width;
    mViewHeight = height;
    
    if (mSurfaceWidth > 0 && mSurfaceHeight > 0 && mDisplay != EGL_NO_DISPLAY) {
        glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
    }
}

bool GpuCompositor::initEGL() {
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDisplay == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed: 0x%x", eglGetError());
        return false;
    }

    if (!eglInitialize(mDisplay, nullptr, nullptr)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    // Try ES2 recordable first (the standard Android MediaCodec surface config)
    const EGLint attribsRecordableES2[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_RECORDABLE_ANDROID, 1,
        EGL_NONE
    };

    const EGLint attribsRecordableES3[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_RECORDABLE_ANDROID, 1,
        EGL_NONE
    };

    const EGLint attribsFallback[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    EGLConfig config = nullptr;
    EGLint numConfigs = 0;
    int clientVersion = 2;

    if (eglChooseConfig(mDisplay, attribsRecordableES2, &config, 1, &numConfigs) && numConfigs > 0) {
        clientVersion = 2;
        LOGI("Selected EGL config: ES2 with RECORDABLE_ANDROID");
    } else if (eglChooseConfig(mDisplay, attribsRecordableES3, &config, 1, &numConfigs) && numConfigs > 0) {
        clientVersion = 3;
        LOGI("Selected EGL config: ES3 with RECORDABLE_ANDROID");
    } else if (eglChooseConfig(mDisplay, attribsFallback, &config, 1, &numConfigs) && numConfigs > 0) {
        clientVersion = 2;
        LOGI("Selected EGL config: ES2 fallback");
    } else {
        LOGE("eglChooseConfig failed for all configs: 0x%x", eglGetError());
        return false;
    }

    mSurface = eglCreateWindowSurface(mDisplay, config, mWindow, nullptr);
    if (mSurface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: error 0x%x", eglGetError());
        return false;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, clientVersion,
        EGL_NONE
    };

    mContext = eglCreateContext(mDisplay, config, EGL_NO_CONTEXT, contextAttribs);
    if (mContext == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed (v%d): error 0x%x", clientVersion, eglGetError());
        return false;
    }

    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        LOGE("eglMakeCurrent failed: error 0x%x", eglGetError());
        return false;
    }

    LOGI("EGL initialized successfully for surface %dx%d (client v%d)", mSurfaceWidth, mSurfaceHeight, clientVersion);
    return true;
}

void GpuCompositor::destroyEGL() {
    if (mDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (mContext != EGL_NO_CONTEXT) {
            eglDestroyContext(mDisplay, mContext);
            mContext = EGL_NO_CONTEXT;
        }
        if (mSurface != EGL_NO_SURFACE) {
            eglDestroySurface(mDisplay, mSurface);
            mSurface = EGL_NO_SURFACE;
        }
        eglTerminate(mDisplay);
        mDisplay = EGL_NO_DISPLAY;
    }
}

GLuint GpuCompositor::loadShader(GLenum type, const char* shaderSrc) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;

    glShaderSource(shader, 1, &shaderSrc, nullptr);
    glCompileShader(shader);

    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            char* infoLog = new char[infoLen];
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog);
            LOGE("Error compiling shader:\n%s", infoLog);
            delete[] infoLog;
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool GpuCompositor::initGL() {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

    mShaderProgram = glCreateProgram();
    glAttachShader(mShaderProgram, vertexShader);
    glAttachShader(mShaderProgram, fragmentShader);
    glBindAttribLocation(mShaderProgram, 0, "aPosition");
    glBindAttribLocation(mShaderProgram, 1, "aTexCoord");
    glLinkProgram(mShaderProgram);

    GLint linked;
    glGetProgramiv(mShaderProgram, GL_LINK_STATUS, &linked);
    if (!linked) {
        LOGE("Error linking program");
        return false;
    }

    // Fullscreen quad covering -1 to +1 NDC
    // Top-to-bottom V coordinates
    GLfloat vertices[] = {
        // x,     y,     z,     u,    v
        -1.0f, -1.0f, 0.0f,  0.0f, 1.0f,
         1.0f, -1.0f, 0.0f,  1.0f, 1.0f,
        -1.0f,  1.0f, 0.0f,  0.0f, 0.0f,
         1.0f,  1.0f, 0.0f,  1.0f, 0.0f,
    };

    glGenBuffers(1, &mVbo);
    glBindBuffer(GL_ARRAY_BUFFER, mVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    // Frame Texture
    glGenTextures(1, &mFrameTexture);
    glBindTexture(GL_TEXTURE_2D, mFrameTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    return true;
}

void GpuCompositor::renderFrame(const motion::Project& project, float currentFrame) {
    if (mDisplay == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE || mContext == EGL_NO_CONTEXT) {
        LOGE("renderFrame aborted: invalid display/surface/context");
        return;
    }
    if (mSurfaceWidth <= 0 || mSurfaceHeight <= 0) {
        return;
    }

    // Ensure EGL context is current for this rendering thread
    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        LOGE("renderFrame eglMakeCurrent failed: 0x%x", eglGetError());
        return;
    }

    size_t totalPixels = static_cast<size_t>(mSurfaceWidth * mSurfaceHeight);
    if (mFramePixelBuffer.size() != totalPixels) {
        mFramePixelBuffer.resize(totalPixels);
    }

    // 1. Render all layers, shapes, keyframes, transitions, blend modes into pixel buffer
    motion::SoftwarePreviewRenderer::renderFrame(
        project, static_cast<int64_t>(currentFrame),
        mFramePixelBuffer.data(), mSurfaceWidth, mSurfaceHeight
    );

    // 2. Setup GL state for drawing fullscreen quad
    glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    glUseProgram(mShaderProgram);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mFrameTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mSurfaceWidth, mSurfaceHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, mFramePixelBuffer.data());
    glUniform1i(glGetUniformLocation(mShaderProgram, "uTexture"), 0);

    // 3. Set shader effects
    glUniform1f(glGetUniformLocation(mShaderProgram, "uBrightness"), mBrightness);
    glUniform1f(glGetUniformLocation(mShaderProgram, "uContrast"), mContrast);
    glUniform1f(glGetUniformLocation(mShaderProgram, "uSaturation"), mSaturation);
    glUniform1f(glGetUniformLocation(mShaderProgram, "uVignette"), mVignette);
    glUniform1i(glGetUniformLocation(mShaderProgram, "uFilterMode"), mFilterMode);

    // 4. Draw quad
    glBindBuffer(GL_ARRAY_BUFFER, mVbo);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), (void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), (void*)(3 * sizeof(GLfloat)));
    glEnableVertexAttribArray(1);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);

    // 5. Ensure all drawing commands are executed and swap buffers to encoder input surface
    glFinish();
    eglSwapBuffers(mDisplay, mSurface);
}
