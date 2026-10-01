#pragma once

#include <string>
#include <vector>
#include <memory>
#include <cstdint>
#include "Layer.h"

namespace motion {

class Project {
public:
    std::string title = "Proyek Tanpa Judul";
    int width = 1080;
    int height = 1920;
    double fps = 60.0;
    int64_t durationFrames = 1800; // 30 seconds at 60fps
    uint32_t backgroundColor = 0xFF131315; // Dark obsidian default

    std::vector<std::shared_ptr<Layer>> layers;

    Project();
    Project(std::string title, int width, int height, double fps, int64_t durationFrames, uint32_t bg = 0xFF131315);

    // Layer management
    void addLayer(std::shared_ptr<Layer> layer);
    bool removeLayer(const std::string& layerId);
    std::shared_ptr<Layer> getLayer(const std::string& layerId) const;
    void reorderLayer(const std::string& layerId, int newIndex);
    bool setLayerSpan(const std::string& layerId, int64_t startFrame, int64_t durationFrames);
    bool splitLayer(const std::string& layerId, int64_t splitFrame);
    std::shared_ptr<Layer> duplicateLayer(const std::string& layerId);

    std::vector<std::shared_ptr<Layer>> getLayersSortedByZ() const;
    std::vector<std::shared_ptr<Layer>> getActiveLayersAtFrame(int64_t frame) const;

    double getTotalDurationSeconds() const {
        return (fps > 0.0) ? (static_cast<double>(durationFrames) / fps) : 0.0;
    }

    void setSettings(int w, int h, double f, int64_t frames, uint32_t bg);
};

} // namespace motion
