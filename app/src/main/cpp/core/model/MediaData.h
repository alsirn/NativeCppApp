#pragma once

#include <string>
#include <cstdint>

namespace motion {

enum class MediaType {
    IMAGE,
    VIDEO
};

inline std::string mediaTypeToString(MediaType type) {
    return (type == MediaType::VIDEO) ? "VIDEO" : "IMAGE";
}

inline MediaType stringToMediaType(const std::string& str) {
    return (str == "VIDEO") ? MediaType::VIDEO : MediaType::IMAGE;
}

struct MediaData {
    std::string filePath;
    MediaType type = MediaType::IMAGE;
    int originalWidth = 0;
    int originalHeight = 0;
    int64_t sourceDurationFrames = 0;
    int64_t trimIn = 0;
    int64_t trimOut = 0;
    double speedMultiplier = 1.0;
};

} // namespace motion
