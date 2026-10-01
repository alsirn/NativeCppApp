#include "AnimatedProperty.h"
#include <algorithm>

namespace motion {

AnimatedProperty::AnimatedProperty(std::string propName, double defaultVal)
    : name(std::move(propName)), defaultValue(defaultVal) {}

void AnimatedProperty::addOrUpdateKeyframe(const Keyframe& kf) {
    for (auto& existing : keyframes) {
        if (existing.frame == kf.frame) {
            existing = kf;
            return;
        }
    }
    keyframes.push_back(kf);
    sortKeyframes();
}

void AnimatedProperty::addOrUpdateKeyframe(int64_t frame, double value,
                                           InterpolationType interp,
                                           double ox, double oy,
                                           double ix, double iy) {
    Keyframe kf(frame, value, interp, ox, oy, ix, iy);
    addOrUpdateKeyframe(kf);
}

bool AnimatedProperty::removeKeyframe(int64_t frame) {
    auto it = std::remove_if(keyframes.begin(), keyframes.end(),
                             [frame](const Keyframe& k) { return k.frame == frame; });
    if (it != keyframes.end()) {
        keyframes.erase(it, keyframes.end());
        return true;
    }
    return false;
}

void AnimatedProperty::clearKeyframes() {
    keyframes.clear();
}

bool AnimatedProperty::findKeyframe(int64_t frame, Keyframe& outKf) const {
    for (const auto& k : keyframes) {
        if (k.frame == frame) {
            outKf = k;
            return true;
        }
    }
    return false;
}

void AnimatedProperty::sortKeyframes() {
    std::sort(keyframes.begin(), keyframes.end(),
              [](const Keyframe& a, const Keyframe& b) {
                  return a.frame < b.frame;
              });
}

double AnimatedProperty::evaluate(double frame) const {
    if (keyframes.empty()) {
        return defaultValue;
    }
    if (keyframes.size() == 1 || frame <= static_cast<double>(keyframes.front().frame)) {
        return keyframes.front().value;
    }
    if (frame >= static_cast<double>(keyframes.back().frame)) {
        return keyframes.back().value;
    }

    // Find interval [k1, k2] such that k1.frame <= frame < k2.frame
    for (size_t i = 0; i < keyframes.size() - 1; ++i) {
        const auto& k1 = keyframes[i];
        const auto& k2 = keyframes[i + 1];

        if (frame >= static_cast<double>(k1.frame) && frame <= static_cast<double>(k2.frame)) {
            double span = static_cast<double>(k2.frame - k1.frame);
            if (span <= 0.0) {
                return k1.value;
            }

            double u = (frame - static_cast<double>(k1.frame)) / span;
            u = std::clamp(u, 0.0, 1.0);

            switch (k1.interpolation) {
                case InterpolationType::HOLD:
                    return k1.value;

                case InterpolationType::LINEAR:
                    return k1.value + u * (k2.value - k1.value);

                case InterpolationType::BEZIER:
                case InterpolationType::AUTO_BEZIER:
                case InterpolationType::CONTINUOUS_BEZIER: {
                    CubicBezier bezier(k1.outHandleX, k1.outHandleY, k1.inHandleX, k1.inHandleY);
                    double progress = bezier.solve(u);
                    return k1.value + progress * (k2.value - k1.value);
                }
            }
        }
    }

    return keyframes.back().value;
}

} // namespace motion
