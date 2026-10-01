#pragma once

#include <cstdint>
#include <vector>
#include "../model/Project.h"

namespace motion {

class SoftwarePreviewRenderer {
public:
    // Renders the project composition at the given frame into an RGBA8888 buffer
    static void renderFrame(const Project& project, int64_t frame, uint32_t* pixelBuffer, int targetWidth, int targetHeight);

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

    static inline uint32_t blendPixel(uint32_t dst, uint32_t src, double alphaMultiplier, BlendMode mode = BlendMode::NORMAL);
};

} // namespace motion
