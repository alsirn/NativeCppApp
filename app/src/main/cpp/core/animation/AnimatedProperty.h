#pragma once

#include <vector>
#include <string>
#include <memory>
#include "Keyframe.h"
#include "CubicBezier.h"

namespace motion {

class AnimatedProperty {
public:
    std::string name;
    double defaultValue = 0.0;
    std::vector<Keyframe> keyframes;

    AnimatedProperty() = default;
    explicit AnimatedProperty(std::string propName, double defaultVal = 0.0);

    void addOrUpdateKeyframe(const Keyframe& kf);
    void addOrUpdateKeyframe(int64_t frame, double value,
                             InterpolationType interp = InterpolationType::BEZIER,
                             double ox = 0.42, double oy = 0.0,
                             double ix = 0.58, double iy = 1.0);
    bool removeKeyframe(int64_t frame);
    void clearKeyframes();

    bool hasKeyframes() const { return !keyframes.empty(); }
    size_t getKeyframeCount() const { return keyframes.size(); }
    const std::vector<Keyframe>& getKeyframes() const { return keyframes; }

    bool findKeyframe(int64_t frame, Keyframe& outKf) const;
    double evaluate(double frame) const;
    double evaluate(int64_t frame) const { return evaluate(static_cast<double>(frame)); }

private:
    void sortKeyframes();
};

} // namespace motion
