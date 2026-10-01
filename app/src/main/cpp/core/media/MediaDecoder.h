#ifndef MOTIONLAB_MEDIA_DECODER_H
#define MOTIONLAB_MEDIA_DECODER_H

#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <string>

class MediaDecoder {
public:
    MediaDecoder();
    ~MediaDecoder();

    bool open(const std::string& path);
    void close();

    // Just a placeholder for actual extraction logic to surface later
    bool decodeNextFrame();

private:
    AMediaExtractor* mExtractor = nullptr;
    AMediaCodec* mCodec = nullptr;
    int mVideoTrackIndex = -1;
};

#endif // MOTIONLAB_MEDIA_DECODER_H
