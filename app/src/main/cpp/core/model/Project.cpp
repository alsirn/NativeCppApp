#include "Project.h"
#include <algorithm>
#include <chrono>

namespace motion {

Project::Project() = default;

Project::Project(std::string title, int width, int height, double fps, int64_t durationFrames, uint32_t bg)
    : title(std::move(title)),
      width(width),
      height(height),
      fps(fps),
      durationFrames(durationFrames),
      backgroundColor(bg) {}

void Project::addLayer(std::shared_ptr<Layer> layer) {
    if (!layer) return;
    layer->zIndex = static_cast<int>(layers.size());
    layers.push_back(layer);
}

bool Project::removeLayer(const std::string& layerId) {
    auto it = std::remove_if(layers.begin(), layers.end(),
                             [&layerId](const std::shared_ptr<Layer>& l) {
                                 return l && l->id == layerId;
                             });
    if (it != layers.end()) {
        layers.erase(it, layers.end());
        // Re-index zIndex
        for (size_t i = 0; i < layers.size(); ++i) {
            if (layers[i]) layers[i]->zIndex = static_cast<int>(i);
        }
        return true;
    }
    return false;
}

std::shared_ptr<Layer> Project::getLayer(const std::string& layerId) const {
    for (const auto& l : layers) {
        if (l && l->id == layerId) return l;
    }
    return nullptr;
}

void Project::reorderLayer(const std::string& layerId, int newIndex) {
    auto it = std::find_if(layers.begin(), layers.end(),
                           [&layerId](const std::shared_ptr<Layer>& l) {
                               return l && l->id == layerId;
                           });
    if (it == layers.end()) return;

    auto layer = *it;
    layers.erase(it);

    newIndex = std::clamp(newIndex, 0, static_cast<int>(layers.size()));
    layers.insert(layers.begin() + newIndex, layer);

    for (size_t i = 0; i < layers.size(); ++i) {
        if (layers[i]) layers[i]->zIndex = static_cast<int>(i);
    }
}

bool Project::setLayerSpan(const std::string& layerId, int64_t startFrame, int64_t durationFrames) {
    auto layer = getLayer(layerId);
    if (!layer) return false;
    layer->startFrame = std::max<int64_t>(0, startFrame);
    layer->durationFrames = std::max<int64_t>(1, durationFrames);
    return true;
}

bool Project::splitLayer(const std::string& layerId, int64_t splitFrame) {
    auto original = getLayer(layerId);
    if (!original) return false;

    int64_t origStart = original->startFrame;
    int64_t origEnd = origStart + original->durationFrames;

    // Check that splitFrame is strictly inside the layer
    if (splitFrame <= origStart || splitFrame >= origEnd) {
        return false;
    }

    // Shorten original layer to end at splitFrame
    original->durationFrames = splitFrame - origStart;

    // Create split piece starting at splitFrame
    auto splitPiece = std::make_shared<Layer>(*original);
    splitPiece->id = original->id + "_split_" + std::to_string(splitFrame);
    splitPiece->name = original->name + " (Bagi)";
    splitPiece->startFrame = splitFrame;
    splitPiece->durationFrames = origEnd - splitFrame;

    addLayer(splitPiece);
    return true;
}

std::shared_ptr<Layer> Project::duplicateLayer(const std::string& layerId) {
    auto original = getLayer(layerId);
    if (!original) return nullptr;

    auto dup = std::make_shared<Layer>(*original);
    dup->id = original->id + "_copy_" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count() % 10000);
    dup->name = original->name + " Salinan";
    // Shift slightly so it's visible if it has transform
    auto* posX = dup->transform.getProperty("posX");
    if (posX && posX->keyframes.empty()) {
        posX->defaultValue += 30.0;
    }
    auto* posY = dup->transform.getProperty("posY");
    if (posY && posY->keyframes.empty()) {
        posY->defaultValue += 30.0;
    }

    addLayer(dup);
    return dup;
}

std::vector<std::shared_ptr<Layer>> Project::getLayersSortedByZ() const {
    auto sorted = layers;
    std::sort(sorted.begin(), sorted.end(),
              [](const std::shared_ptr<Layer>& a, const std::shared_ptr<Layer>& b) {
                  int za = a ? a->zIndex : 0;
                  int zb = b ? b->zIndex : 0;
                  return za < zb;
              });
    return sorted;
}

std::vector<std::shared_ptr<Layer>> Project::getActiveLayersAtFrame(int64_t frame) const {
    std::vector<std::shared_ptr<Layer>> active;
    for (const auto& l : getLayersSortedByZ()) {
        if (l && l->isVisible && l->isActiveAtFrame(frame)) {
            active.push_back(l);
        }
    }
    return active;
}

void Project::setSettings(int w, int h, double f, int64_t frames, uint32_t bg) {
    width = w;
    height = h;
    fps = f;
    durationFrames = frames;
    backgroundColor = bg;
}

} // namespace motion
