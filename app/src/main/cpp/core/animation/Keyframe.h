#pragma once

#include <cstdint>
#include "InterpolationType.h"

namespace motion {

struct Keyframe {
    int64_t frame = 0;
    double value = 0.0;
    InterpolationType interpolation = InterpolationType::BEZIER;

    // Bezier control handles (normalized in [0, 1] relative to the interval between keyframes)
    double outHandleX = 0.42;
    double outHandleY = 0.0;
    double inHandleX = 0.58;
    double inHandleY = 1.0;

    Keyframe() = default;
    Keyframe(int64_t f, double val, InterpolationType interp = InterpolationType::BEZIER)
        : frame(f), value(val), interpolation(interp) {}
    
    Keyframe(int64_t f, double val, InterpolationType interp,
             double ox, double oy, double ix, double iy)
        : frame(f), value(val), interpolation(interp),
          outHandleX(ox), outHandleY(oy), inHandleX(ix), inHandleY(iy) {}
};

} // namespace motion
