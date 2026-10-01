#include "MediaDecoder.h"
#include <android/log.h>

#define LOG_TAG "MediaDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MediaDecoder::MediaDecoder() {
}

MediaDecoder::~MediaDecoder() {
    close();
}

bool MediaDecoder::open(const std::string& path) {
    mExtractor = AMediaExtractor_new();
    if (!mExtractor) {
        LOGE("Failed to create AMediaExtractor");
        return false;
    }

    // Set the data source. For file paths we need a file descriptor in real implementation.
    // Assuming for now it's a direct file path string, though AMediaExtractor_setDataSource
    // works well with FD. We'll use a placeholder URL approach.
    media_status_t status = AMediaExtractor_setDataSource(mExtractor, path.c_str());
    if (status != AMEDIA_OK) {
        LOGE("Failed to set data source: %d", status);
        close();
        return false;
    }

    size_t numTracks = AMediaExtractor_getTrackCount(mExtractor);
    for (size_t i = 0; i < numTracks; i++) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(mExtractor, i);
        const char* mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            AMediaFormat_delete(format);
            continue;
        }

        if (strncmp(mime, "video/", 6) == 0) {
            mVideoTrackIndex = i;
            AMediaExtractor_selectTrack(mExtractor, i);
            
            mCodec = AMediaCodec_createDecoderByType(mime);
            if (mCodec) {
                // In a real implementation we would pass an ANativeWindow surface here
                // to decode directly to an OpenGL texture.
                status = AMediaCodec_configure(mCodec, format, nullptr, nullptr, 0);
                if (status == AMEDIA_OK) {
                    AMediaCodec_start(mCodec);
                    LOGI("Successfully configured and started codec for %s", mime);
                } else {
                    LOGE("Failed to configure codec");
                }
            }
            AMediaFormat_delete(format);
            break;
        }
        AMediaFormat_delete(format);
    }

    if (mVideoTrackIndex < 0) {
        LOGE("No video track found");
        close();
        return false;
    }

    return true;
}

void MediaDecoder::close() {
    if (mCodec) {
        AMediaCodec_stop(mCodec);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }
    if (mExtractor) {
        AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    mVideoTrackIndex = -1;
}

bool MediaDecoder::decodeNextFrame() {
    if (!mCodec || !mExtractor) return false;

    // Decoding logic: 
    // 1. Dequeue input buffer, read sample data, queue input buffer
    // 2. Dequeue output buffer, release to surface if valid
    
    // Simplified stub
    return true;
}
