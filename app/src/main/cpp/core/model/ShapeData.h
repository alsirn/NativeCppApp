#pragma once

#include <string>
#include <cstdint>

namespace motion {

enum class ShapeType {
    RECTANGLE,
    CIRCLE,
    STAR,
    POLYGON,
    LINE
};

inline std::string shapeTypeToString(ShapeType type) {
    switch (type) {
        case ShapeType::RECTANGLE: return "RECTANGLE";
        case ShapeType::CIRCLE: return "CIRCLE";
        case ShapeType::STAR: return "STAR";
        case ShapeType::POLYGON: return "POLYGON";
        case ShapeType::LINE: return "LINE";
        default: return "RECTANGLE";
    }
}

inline ShapeType stringToShapeType(const std::string& str) {
    if (str == "CIRCLE") return ShapeType::CIRCLE;
    if (str == "STAR") return ShapeType::STAR;
    if (str == "POLYGON") return ShapeType::POLYGON;
    if (str == "LINE") return ShapeType::LINE;
    return ShapeType::RECTANGLE;
}

struct ShapeData {
    ShapeType type = ShapeType::RECTANGLE;
    double width = 200.0;
    double height = 200.0;
    double radius = 100.0;
    int points = 5; // For star / polygon

    // Colors stored in 0xAARRGGBB format
    uint32_t fillColor = 0xFFFFFFFF;    // Default white
    uint32_t strokeColor = 0xFF000000;  // Default black outline
    float strokeWidth = 0.0f;
    float cornerRadius = 0.0f;
};

} // namespace motion
