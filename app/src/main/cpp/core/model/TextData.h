#pragma once
#include <string>
#include <cstdint>

namespace motion {

class TextData {
public:
    std::string text = "MotionLab";
    double fontSize = 48.0;
    uint32_t textColor = 0xFFFFFFFF;
    std::string fontFamily = "sans-serif";
};

} // namespace motion
