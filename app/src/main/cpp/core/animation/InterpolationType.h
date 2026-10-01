#pragma once

#include <string>

namespace motion {

enum class InterpolationType {
    LINEAR,
    BEZIER,
    HOLD,
    AUTO_BEZIER,
    CONTINUOUS_BEZIER
};

inline std::string interpolationToString(InterpolationType type) {
    switch (type) {
        case InterpolationType::LINEAR: return "LINEAR";
        case InterpolationType::BEZIER: return "BEZIER";
        case InterpolationType::HOLD: return "HOLD";
        case InterpolationType::AUTO_BEZIER: return "AUTO_BEZIER";
        case InterpolationType::CONTINUOUS_BEZIER: return "CONTINUOUS_BEZIER";
        default: return "LINEAR";
    }
}

inline InterpolationType stringToInterpolation(const std::string& str) {
    if (str == "BEZIER") return InterpolationType::BEZIER;
    if (str == "HOLD") return InterpolationType::HOLD;
    if (str == "AUTO_BEZIER") return InterpolationType::AUTO_BEZIER;
    if (str == "CONTINUOUS_BEZIER") return InterpolationType::CONTINUOUS_BEZIER;
    return InterpolationType::LINEAR;
}

} // namespace motion
