#include "SoftwarePreviewRenderer.h"
#include "Font8x8.h"
#include <cmath>
#include <algorithm>

namespace motion {

// Android Bitmap ARGB_8888 byte order on little-endian: [R, G, B, A] -> 0xAABBGGRR in uint32
static inline uint32_t argbToRgbaByteOrder(uint32_t argb) {
    uint32_t a = (argb >> 24) & 0xFF;
    uint32_t r = (argb >> 16) & 0xFF;
    uint32_t g = (argb >> 8) & 0xFF;
    uint32_t b = argb & 0xFF;
    return (a << 24) | (b << 16) | (g << 8) | r;
}

uint32_t SoftwarePreviewRenderer::blendPixel(uint32_t dst, uint32_t src, double alphaMultiplier, BlendMode mode) {
    uint32_t sa = ((src >> 24) & 0xFF);
    sa = static_cast<uint32_t>(sa * std::clamp(alphaMultiplier, 0.0, 1.0));
    if (sa == 0) return dst;

    uint32_t da = (dst >> 24) & 0xFF;
    uint32_t sr = src & 0xFF;
    uint32_t sg = (src >> 8) & 0xFF;
    uint32_t sb = (src >> 16) & 0xFF;

    uint32_t dr = dst & 0xFF;
    uint32_t dg = (dst >> 8) & 0xFF;
    uint32_t db = (dst >> 16) & 0xFF;

    uint32_t blendedR = sr;
    uint32_t blendedG = sg;
    uint32_t blendedB = sb;

    switch (mode) {
        case BlendMode::NORMAL:
            blendedR = sr;
            blendedG = sg;
            blendedB = sb;
            break;
        case BlendMode::MULTIPLY:
            blendedR = (sr * dr) / 255;
            blendedG = (sg * dg) / 255;
            blendedB = (sb * db) / 255;
            break;
        case BlendMode::SCREEN:
            blendedR = 255 - ((255 - sr) * (255 - dr)) / 255;
            blendedG = 255 - ((255 - sg) * (255 - dg)) / 255;
            blendedB = 255 - ((255 - sb) * (255 - db)) / 255;
            break;
        case BlendMode::OVERLAY:
            blendedR = (dr < 128) ? (2 * sr * dr) / 255 : 255 - (2 * (255 - sr) * (255 - dr)) / 255;
            blendedG = (dg < 128) ? (2 * sg * dg) / 255 : 255 - (2 * (255 - sg) * (255 - dg)) / 255;
            blendedB = (db < 128) ? (2 * sb * db) / 255 : 255 - (2 * (255 - sb) * (255 - db)) / 255;
            break;
        case BlendMode::ADD:
            blendedR = std::min(255u, sr + dr);
            blendedG = std::min(255u, sg + dg);
            blendedB = std::min(255u, sb + db);
            break;
        case BlendMode::DIFFERENCE:
            blendedR = (sr > dr) ? (sr - dr) : (dr - sr);
            blendedG = (sg > dg) ? (sg - dg) : (dg - sg);
            blendedB = (sb > db) ? (sb - db) : (db - sb);
            break;
    }

    uint32_t invSa = 255 - sa;
    uint32_t outR = (blendedR * sa + dr * invSa) / 255;
    uint32_t outG = (blendedG * sa + dg * invSa) / 255;
    uint32_t outB = (blendedB * sa + db * invSa) / 255;
    uint32_t outA = sa + (da * invSa) / 255;

    return (outA << 24) | (outB << 16) | (outG << 8) | outR;
}

void SoftwarePreviewRenderer::drawCircle(uint32_t* buffer, int bufW, int bufH,
                                        double cx, double cy, double radius,
                                        double scaleX, double scaleY,
                                        double opacity, uint32_t color,
                                        BlendMode mode) {
    double rx = radius * std::abs(scaleX);
    double ry = radius * std::abs(scaleY);
    if (rx <= 0.5 || ry <= 0.5) return;

    int minX = std::max(0, static_cast<int>(cx - rx - 1.0));
    int maxX = std::min(bufW - 1, static_cast<int>(cx + rx + 1.0));
    int minY = std::max(0, static_cast<int>(cy - ry - 1.0));
    int maxY = std::min(bufH - 1, static_cast<int>(cy + ry + 1.0));

    double invRx2 = 1.0 / (rx * rx);
    double invRy2 = 1.0 / (ry * ry);
    uint32_t srcColor = argbToRgbaByteOrder(color);

    for (int y = minY; y <= maxY; ++y) {
        double dy = y - cy;
        double dy2Norm = (dy * dy) * invRy2;
        if (dy2Norm > 1.0) continue;

        for (int x = minX; x <= maxX; ++x) {
            double dx = x - cx;
            double distSq = (dx * dx) * invRx2 + dy2Norm;
            if (distSq <= 1.0) {
                // Anti-aliased edge smoothing
                double edgeAlpha = (1.0 - distSq) * rx;
                double a = std::clamp(edgeAlpha, 0.0, 1.0) * opacity;
                uint32_t idx = y * bufW + x;
                buffer[idx] = blendPixel(buffer[idx], srcColor, a, mode);
            }
        }
    }
}

void SoftwarePreviewRenderer::drawRectangle(uint32_t* buffer, int bufW, int bufH,
                                           double cx, double cy, double w, double h,
                                           double rotationRad, double scaleX, double scaleY,
                                           double opacity, uint32_t color, float cornerRadius,
                                           BlendMode mode) {
    double halfW = (w * 0.5) * std::abs(scaleX);
    double halfH = (h * 0.5) * std::abs(scaleY);
    if (halfW <= 0.5 || halfH <= 0.5) return;

    double cosR = std::cos(-rotationRad);
    double sinR = std::sin(-rotationRad);

    double boundRadius = std::sqrt(halfW * halfW + halfH * halfH);
    int minX = std::max(0, static_cast<int>(cx - boundRadius - 1.0));
    int maxX = std::min(bufW - 1, static_cast<int>(cx + boundRadius + 1.0));
    int minY = std::max(0, static_cast<int>(cy - boundRadius - 1.0));
    int maxY = std::min(bufH - 1, static_cast<int>(cy + boundRadius + 1.0));

    uint32_t srcColor = argbToRgbaByteOrder(color);

    for (int y = minY; y <= maxY; ++y) {
        for (int x = minX; x <= maxX; ++x) {
            double dx = x - cx;
            double dy = y - cy;

            // Rotate back into rectangle local space
            double localX = dx * cosR - dy * sinR;
            double localY = dx * sinR + dy * cosR;

            if (std::abs(localX) <= halfW && std::abs(localY) <= halfH) {
                uint32_t idx = y * bufW + x;
                buffer[idx] = blendPixel(buffer[idx], srcColor, opacity, mode);
            }
        }
    }
}

void SoftwarePreviewRenderer::drawText(uint32_t* buffer, int bufW, int bufH,
                                      double cx, double cy, const std::string& text,
                                      double fontSize, double rotationRad,
                                      double scaleX, double scaleY,
                                      double opacity, uint32_t color,
                                      BlendMode mode) {
    if (text.empty() || fontSize <= 1.0) return;
    double absSx = std::abs(scaleX);
    double absSy = std::abs(scaleY);
    if (absSx <= 0.01 || absSy <= 0.01) return;

    double pixelUnit = fontSize / 8.0;
    double charAdvance = 9.0 * pixelUnit * absSx;
    double charH = 8.0 * pixelUnit * absSy;
    double totalW = (text.length() * 9.0 - 1.0) * pixelUnit * absSx;
    if (totalW <= 1.0 || charH <= 1.0) return;

    double halfW = totalW * 0.5;
    double halfH = charH * 0.5;

    double cosR = std::cos(-rotationRad);
    double sinR = std::sin(-rotationRad);

    double boundRadius = std::sqrt(halfW * halfW + halfH * halfH);
    int minX = std::max(0, static_cast<int>(cx - boundRadius - 1.0));
    int maxX = std::min(bufW - 1, static_cast<int>(cx + boundRadius + 1.0));
    int minY = std::max(0, static_cast<int>(cy - boundRadius - 1.0));
    int maxY = std::min(bufH - 1, static_cast<int>(cy + boundRadius + 1.0));

    uint32_t srcColor = argbToRgbaByteOrder(color);

    for (int y = minY; y <= maxY; ++y) {
        double dy = y - cy;
        for (int x = minX; x <= maxX; ++x) {
            double dx = x - cx;

            // Rotate back into text line local coordinates centered at (0, 0)
            double localX = dx * cosR - dy * sinR;
            double localY = dx * sinR + dy * cosR;

            if (localX >= -halfW && localX <= halfW && localY >= -halfH && localY <= halfH) {
                double lineX = localX + halfW;
                int charIdx = static_cast<int>(lineX / (9.0 * pixelUnit * absSx));
                if (charIdx >= 0 && charIdx < static_cast<int>(text.length())) {
                    char c = text[charIdx];
                    double inCharX = lineX - (charIdx * 9.0 * pixelUnit * absSx);
                    double glyphUnitX = pixelUnit * absSx;
                    double glyphUnitY = pixelUnit * absSy;

                    // 8 pixels of character + 1 blank spacing pixel
                    int gx = static_cast<int>(inCharX / glyphUnitX);
                    int gy = static_cast<int>((localY + halfH) / glyphUnitY);

                    if (gx >= 0 && gx < 8 && gy >= 0 && gy < 8) {
                        const uint8_t* glyph = getFontGlyph(c);
                        uint8_t row = glyph[gy];
                        if ((row >> (7 - gx)) & 1) {
                            uint32_t idx = y * bufW + x;
                            buffer[idx] = blendPixel(buffer[idx], srcColor, opacity, mode);
                        }
                    }
                }
            }
        }
    }
}

void SoftwarePreviewRenderer::renderFrame(const Project& project, int64_t frame, uint32_t* pixelBuffer, int targetWidth, int targetHeight) {
    if (!pixelBuffer || targetWidth <= 0 || targetHeight <= 0) return;

    // Fill background
    uint32_t bgColor = argbToRgbaByteOrder(project.backgroundColor);
    int totalPixels = targetWidth * targetHeight;
    for (int i = 0; i < totalPixels; ++i) {
        pixelBuffer[i] = bgColor;
    }

    // Scale ratio between project resolution and preview buffer
    double projW = (project.width > 0) ? static_cast<double>(project.width) : static_cast<double>(targetWidth);
    double scale = static_cast<double>(targetWidth) / projW;
    double originX = targetWidth * 0.5;
    double originY = targetHeight * 0.5;

    auto activeLayers = project.getActiveLayersAtFrame(frame);
    for (const auto& layer : activeLayers) {
        if (!layer || !layer->isVisible) continue;

        TransformState transform = layer->evaluateTransform(frame, project.width, project.height);
        if (transform.opacity <= 0.0) continue;

        double layerX = originX + (transform.posX * scale);
        double layerY = originY + (transform.posY * scale);
        double rotRad = transform.rotation * (M_PI / 180.0);

        if (layer->type == LayerType::SHAPE) {
            if (layer->shapeData.type == ShapeType::CIRCLE) {
                drawCircle(pixelBuffer, targetWidth, targetHeight,
                           layerX, layerY,
                           layer->shapeData.radius * scale,
                           transform.scaleX, transform.scaleY,
                           transform.opacity, layer->shapeData.fillColor,
                           layer->blendMode);
            } else {
                drawRectangle(pixelBuffer, targetWidth, targetHeight,
                              layerX, layerY,
                              layer->shapeData.width * scale,
                              layer->shapeData.height * scale,
                              rotRad,
                              transform.scaleX, transform.scaleY,
                              transform.opacity, layer->shapeData.fillColor,
                              layer->shapeData.cornerRadius,
                              layer->blendMode);
            }
        } else if (layer->type == LayerType::TEXT) {
            drawText(pixelBuffer, targetWidth, targetHeight,
                     layerX, layerY,
                     layer->textData.text,
                     layer->textData.fontSize * scale,
                     rotRad,
                     transform.scaleX, transform.scaleY,
                     transform.opacity, layer->textData.textColor,
                     layer->blendMode);
        }
    }
}

} // namespace motion
