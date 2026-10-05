#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <memory>
#include "../model/Project.h"

namespace motion {

struct CachedImage {
    int width = 0;
    int height = 0;
    std::vector<uint32_t> pixels;
};

class SoftwarePreviewRenderer {
public:
    // Renders the project composition at the given frame into an RGBA8888 buffer
    static void renderFrame(const Project& project, int64_t frame, uint32_t* pixelBuffer, int targetWidth, int targetHeight);

    // Image loading / cache
    static std::shared_ptr<CachedImage> getImage(const std::string& filePath);
    static bool getImageDimensions(const std::string& filePath, int& outW, int& outH);
    static void clearImageCache();

private:
    static void drawRectangle(uint32_t* buffer, int bufW, int bufH,
                              double cx, double cy, double w, double h,
                              double rotationRad, double scaleX, double scaleY,
                              double opacity, uint32_t color, float cornerRadius,
                              BlendMode mode = BlendMode::NORMAL);

    static void drawCircle(uint32_t* buffer, int bufW, int bufH,
                           double cx, double cy, double radius,
                           double scaleX, double scaleY,
                           double opacity, uint32_t color,
                           BlendMode mode = BlendMode::NORMAL);

    static void drawText(uint32_t* buffer, int bufW, int bufH,
                         double cx, double cy, const std::string& text,
                         double fontSize, double rotationRad,
                         double scaleX, double scaleY,
                         double opacity, uint32_t color,
                         BlendMode mode = BlendMode::NORMAL);

    static void drawImage(uint32_t* buffer, int bufW, int bufH,
                          double cx, double cy, double w, double h,
                          double rotationRad, double scaleX, double scaleY,
                          double opacity, const CachedImage* img,
                          BlendMode mode = BlendMode::NORMAL);

    static inline uint32_t blendPixel(uint32_t dst, uint32_t src, double alphaMultiplier, BlendMode mode = BlendMode::NORMAL);
};

} // namespace motion
