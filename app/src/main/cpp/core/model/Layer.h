#pragma once

#include <string>
#include <memory>
#include <optional>
#include "Transform.h"
#include "ShapeData.h"
#include "MediaData.h"
#include "AudioData.h"
#include "TextData.h"

namespace motion {

enum class LayerType {
    SHAPE,
    MEDIA,
    AUDIO,
    TEXT
};

inline std::string layerTypeToString(LayerType type) {
    switch (type) {
        case LayerType::SHAPE: return "SHAPE";
        case LayerType::MEDIA: return "MEDIA";
        case LayerType::AUDIO: return "AUDIO";
        case LayerType::TEXT: return "TEXT";
        default: return "SHAPE";
    }
}

inline LayerType stringToLayerType(const std::string& str) {
    if (str == "MEDIA") return LayerType::MEDIA;
    if (str == "AUDIO") return LayerType::AUDIO;
    if (str == "TEXT") return LayerType::TEXT;
    return LayerType::SHAPE;
}

enum class BlendMode {
    NORMAL = 0,
    MULTIPLY = 1,
    SCREEN = 2,
    OVERLAY = 3,
    ADD = 4,
    DIFFERENCE = 5
};

inline std::string blendModeToString(BlendMode mode) {
    switch (mode) {
        case BlendMode::NORMAL: return "NORMAL";
        case BlendMode::MULTIPLY: return "MULTIPLY";
        case BlendMode::SCREEN: return "SCREEN";
        case BlendMode::OVERLAY: return "OVERLAY";
        case BlendMode::ADD: return "ADD";
        case BlendMode::DIFFERENCE: return "DIFFERENCE";
        default: return "NORMAL";
    }
}

inline BlendMode stringToBlendMode(const std::string& str) {
    if (str == "MULTIPLY") return BlendMode::MULTIPLY;
    if (str == "SCREEN") return BlendMode::SCREEN;
    if (str == "OVERLAY") return BlendMode::OVERLAY;
    if (str == "ADD") return BlendMode::ADD;
    if (str == "DIFFERENCE") return BlendMode::DIFFERENCE;
    return BlendMode::NORMAL;
}

enum class TransitionType {
    NONE = 0,
    FADE = 1,
    SLIDE_LEFT = 2,
    SLIDE_RIGHT = 3,
    SLIDE_UP = 4,
    SLIDE_DOWN = 5,
    ZOOM = 6,
    SPIN = 7
};

inline std::string transitionTypeToString(TransitionType t) {
    switch (t) {
        case TransitionType::NONE: return "NONE";
        case TransitionType::FADE: return "FADE";
        case TransitionType::SLIDE_LEFT: return "SLIDE_LEFT";
        case TransitionType::SLIDE_RIGHT: return "SLIDE_RIGHT";
        case TransitionType::SLIDE_UP: return "SLIDE_UP";
        case TransitionType::SLIDE_DOWN: return "SLIDE_DOWN";
        case TransitionType::ZOOM: return "ZOOM";
        case TransitionType::SPIN: return "SPIN";
        default: return "NONE";
    }
}

inline TransitionType stringToTransitionType(const std::string& str) {
    if (str == "FADE") return TransitionType::FADE;
    if (str == "SLIDE_LEFT") return TransitionType::SLIDE_LEFT;
    if (str == "SLIDE_RIGHT") return TransitionType::SLIDE_RIGHT;
    if (str == "SLIDE_UP") return TransitionType::SLIDE_UP;
    if (str == "SLIDE_DOWN") return TransitionType::SLIDE_DOWN;
    if (str == "ZOOM") return TransitionType::ZOOM;
    if (str == "SPIN") return TransitionType::SPIN;
    return TransitionType::NONE;
}

class Layer {
public:
    std::string id;
    std::string name;
    LayerType type = LayerType::SHAPE;
    int64_t startFrame = 0;
    int64_t durationFrames = 300; // Default 5 seconds at 60fps
    int zIndex = 0;
    bool isVisible = true;
    bool isLocked = false;

    // Blend Mode
    BlendMode blendMode = BlendMode::NORMAL;

    // Transition In & Out
    TransitionType inTransition = TransitionType::NONE;
    int64_t inTransitionDuration = 0; // In frames
    TransitionType outTransition = TransitionType::NONE;
    int64_t outTransitionDuration = 0; // In frames

    Transform transform;

    // Type-specific payloads
    ShapeData shapeData;
    MediaData mediaData;
    AudioData audioData;
    TextData textData;

    Layer() = default;
    Layer(std::string id, std::string name, LayerType type, int64_t startFrame = 0, int64_t duration = 300);

    bool isActiveAtFrame(int64_t frame) const {
        return frame >= startFrame && frame < (startFrame + durationFrames);
    }

    int64_t getRelativeFrame(int64_t frame) const {
        return frame - startFrame;
    }

    TransformState evaluateTransform(int64_t frame, int projectWidth = 1080, int projectHeight = 1920) const {
        TransformState state = transform.evaluate(frame);

        // Smooth cubic easing: 3t^2 - 2t^3
        auto smoothStep = [](double t) -> double {
            if (t <= 0.0) return 0.0;
            if (t >= 1.0) return 1.0;
            return t * t * (3.0 - 2.0 * t);
        };

        int64_t relFrame = frame - startFrame;
        int64_t framesLeft = (startFrame + durationFrames) - frame;

        // Apply In-Transition
        if (inTransition != TransitionType::NONE && inTransitionDuration > 0 && relFrame >= 0 && relFrame < inTransitionDuration) {
            double t = smoothStep(static_cast<double>(relFrame) / static_cast<double>(inTransitionDuration));
            switch (inTransition) {
                case TransitionType::FADE:
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_LEFT:
                    state.posX += (1.0 - t) * (projectWidth * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_RIGHT:
                    state.posX -= (1.0 - t) * (projectWidth * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_UP:
                    state.posY += (1.0 - t) * (projectHeight * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_DOWN:
                    state.posY -= (1.0 - t) * (projectHeight * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::ZOOM:
                    state.scaleX *= t;
                    state.scaleY *= t;
                    state.opacity *= t;
                    break;
                case TransitionType::SPIN:
                    state.rotation += (1.0 - t) * 360.0;
                    state.scaleX *= t;
                    state.scaleY *= t;
                    state.opacity *= t;
                    break;
                default:
                    break;
            }
        }

        // Apply Out-Transition
        if (outTransition != TransitionType::NONE && outTransitionDuration > 0 && framesLeft >= 0 && framesLeft < outTransitionDuration) {
            double t = smoothStep(static_cast<double>(framesLeft) / static_cast<double>(outTransitionDuration));
            switch (outTransition) {
                case TransitionType::FADE:
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_LEFT:
                    state.posX -= (1.0 - t) * (projectWidth * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_RIGHT:
                    state.posX += (1.0 - t) * (projectWidth * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_UP:
                    state.posY -= (1.0 - t) * (projectHeight * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::SLIDE_DOWN:
                    state.posY += (1.0 - t) * (projectHeight * 0.75);
                    state.opacity *= t;
                    break;
                case TransitionType::ZOOM:
                    state.scaleX *= t;
                    state.scaleY *= t;
                    state.opacity *= t;
                    break;
                case TransitionType::SPIN:
                    state.rotation -= (1.0 - t) * 360.0;
                    state.scaleX *= t;
                    state.scaleY *= t;
                    state.opacity *= t;
                    break;
                default:
                    break;
            }
        }

        return state;
    }
};

} // namespace motion
