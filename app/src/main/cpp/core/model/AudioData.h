#pragma once

#include <string>
#include <cstdint>

namespace motion {

struct AudioData {
    std::string filePath;
    float volume = 1.0f;
    bool isMuted = false;
    int64_t sourceDurationFrames = 0;
    int64_t trimIn = 0;
    int64_t trimOut = 0;
};

} // namespace motion
