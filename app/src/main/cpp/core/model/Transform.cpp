#include "Transform.h"

namespace motion {

Transform::Transform()
    : posX("posX", 0.0),
      posY("posY", 0.0),
      posZ("posZ", 0.0),
      scaleX("scaleX", 1.0),
      scaleY("scaleY", 1.0),
      rotation("rotation", 0.0),
      opacity("opacity", 1.0) {}

TransformState Transform::evaluate(double frame) const {
    TransformState state;
    state.posX = posX.evaluate(frame);
    state.posY = posY.evaluate(frame);
    state.posZ = posZ.evaluate(frame);
    state.scaleX = scaleX.evaluate(frame);
    state.scaleY = scaleY.evaluate(frame);
    state.rotation = rotation.evaluate(frame);
    state.opacity = opacity.evaluate(frame);
    return state;
}

TransformState Transform::evaluate(int64_t frame) const {
    return evaluate(static_cast<double>(frame));
}

AnimatedProperty* Transform::getProperty(const std::string& name) {
    if (name == "posX" || name == "x") return &posX;
    if (name == "posY" || name == "y") return &posY;
    if (name == "posZ" || name == "z") return &posZ;
    if (name == "scaleX") return &scaleX;
    if (name == "scaleY") return &scaleY;
    if (name == "scale" || name == "zoom") return &scaleX; // Convenience
    if (name == "rotation" || name == "rot") return &rotation;
    if (name == "opacity" || name == "alpha") return &opacity;
    return nullptr;
}

const AnimatedProperty* Transform::getProperty(const std::string& name) const {
    if (name == "posX" || name == "x") return &posX;
    if (name == "posY" || name == "y") return &posY;
    if (name == "posZ" || name == "z") return &posZ;
    if (name == "scaleX") return &scaleX;
    if (name == "scaleY") return &scaleY;
    if (name == "scale" || name == "zoom") return &scaleX;
    if (name == "rotation" || name == "rot") return &rotation;
    if (name == "opacity" || name == "alpha") return &opacity;
    return nullptr;
}

bool Transform::hasKeyframes() const {
    return posX.hasKeyframes() || posY.hasKeyframes() || posZ.hasKeyframes() ||
           scaleX.hasKeyframes() || scaleY.hasKeyframes() ||
           rotation.hasKeyframes() || opacity.hasKeyframes();
}

} // namespace motion
