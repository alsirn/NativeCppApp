#pragma once

#include "../animation/AnimatedProperty.h"
#include <string>

namespace motion {

struct TransformState {
    double posX = 0.0;
    double posY = 0.0;
    double posZ = 0.0;
    double scaleX = 1.0;
    double scaleY = 1.0;
    double rotation = 0.0;
    double opacity = 1.0;
};

class Transform {
public:
    AnimatedProperty posX{"posX", 0.0};
    AnimatedProperty posY{"posY", 0.0};
    AnimatedProperty posZ{"posZ", 0.0};
    AnimatedProperty scaleX{"scaleX", 1.0};
    AnimatedProperty scaleY{"scaleY", 1.0};
    AnimatedProperty rotation{"rotation", 0.0};
    AnimatedProperty opacity{"opacity", 1.0};

    Transform();

    TransformState evaluate(int64_t frame) const;
    TransformState evaluate(double frame) const;

    AnimatedProperty* getProperty(const std::string& name);
    const AnimatedProperty* getProperty(const std::string& name) const;
    bool hasKeyframes() const;
};

} // namespace motion
