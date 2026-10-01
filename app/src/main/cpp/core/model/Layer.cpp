#include "Layer.h"

namespace motion {

Layer::Layer(std::string id, std::string name, LayerType type, int64_t startFrame, int64_t duration)
    : id(std::move(id)),
      name(std::move(name)),
      type(type),
      startFrame(startFrame),
      durationFrames(duration),
      zIndex(0),
      isVisible(true),
      isLocked(false) {}

} // namespace motion
