#include "ProjectSerializer.h"
#include <sstream>
#include <fstream>
#include <iomanip>
#include <cctype>
#include <map>

namespace motion {

// Simple, robust, self-contained JSON node representation for reading
enum class JsonType { NUL, BOOL, NUMBER, STRING, ARRAY, OBJECT };

struct JsonValue {
    JsonType type = JsonType::NUL;
    bool boolVal = false;
    double numVal = 0.0;
    std::string strVal;
    std::vector<JsonValue> arrVal;
    std::map<std::string, JsonValue> objVal;

    const JsonValue& get(const std::string& key) const {
        static JsonValue nullVal;
        if (type != JsonType::OBJECT) return nullVal;
        auto it = objVal.find(key);
        return (it != objVal.end()) ? it->second : nullVal;
    }

    std::string getString(const std::string& key, const std::string& def = "") const {
        const auto& v = get(key);
        return (v.type == JsonType::STRING) ? v.strVal : def;
    }

    double getDouble(const std::string& key, double def = 0.0) const {
        const auto& v = get(key);
        return (v.type == JsonType::NUMBER) ? v.numVal : def;
    }

    int64_t getInt64(const std::string& key, int64_t def = 0) const {
        const auto& v = get(key);
        return (v.type == JsonType::NUMBER) ? static_cast<int64_t>(v.numVal) : def;
    }

    int getInt(const std::string& key, int def = 0) const {
        return static_cast<int>(getInt64(key, def));
    }

    bool getBool(const std::string& key, bool def = false) const {
        const auto& v = get(key);
        return (v.type == JsonType::BOOL) ? v.boolVal : def;
    }
};

class MiniJsonParser {
public:
    static bool parse(const std::string& src, JsonValue& root) {
        size_t idx = 0;
        skipWs(src, idx);
        return parseValue(src, idx, root);
    }

private:
    static void skipWs(const std::string& src, size_t& idx) {
        while (idx < src.size() && (std::isspace(static_cast<unsigned char>(src[idx])))) {
            idx++;
        }
    }

    static bool parseValue(const std::string& src, size_t& idx, JsonValue& val) {
        skipWs(src, idx);
        if (idx >= src.size()) return false;

        char c = src[idx];
        if (c == '{') return parseObject(src, idx, val);
        if (c == '[') return parseArray(src, idx, val);
        if (c == '"') return parseString(src, idx, val.strVal) && (val.type = JsonType::STRING, true);
        if (c == 't' || c == 'f') return parseBool(src, idx, val);
        if (c == 'n') return parseNull(src, idx, val);
        if (c == '-' || std::isdigit(static_cast<unsigned char>(c))) return parseNumber(src, idx, val);

        return false;
    }

    static bool parseObject(const std::string& src, size_t& idx, JsonValue& val) {
        val.type = JsonType::OBJECT;
        val.objVal.clear();
        idx++; // skip '{'
        skipWs(src, idx);
        if (idx < src.size() && src[idx] == '}') {
            idx++;
            return true;
        }

        while (idx < src.size()) {
            skipWs(src, idx);
            if (idx >= src.size() || src[idx] != '"') return false;
            std::string key;
            if (!parseString(src, idx, key)) return false;

            skipWs(src, idx);
            if (idx >= src.size() || src[idx] != ':') return false;
            idx++; // skip ':'

            JsonValue subVal;
            if (!parseValue(src, idx, subVal)) return false;
            val.objVal[key] = std::move(subVal);

            skipWs(src, idx);
            if (idx >= src.size()) return false;
            if (src[idx] == '}') {
                idx++;
                return true;
            }
            if (src[idx] == ',') {
                idx++;
            } else {
                return false;
            }
        }
        return false;
    }

    static bool parseArray(const std::string& src, size_t& idx, JsonValue& val) {
        val.type = JsonType::ARRAY;
        val.arrVal.clear();
        idx++; // skip '['
        skipWs(src, idx);
        if (idx < src.size() && src[idx] == ']') {
            idx++;
            return true;
        }

        while (idx < src.size()) {
            JsonValue elem;
            if (!parseValue(src, idx, elem)) return false;
            val.arrVal.push_back(std::move(elem));

            skipWs(src, idx);
            if (idx >= src.size()) return false;
            if (src[idx] == ']') {
                idx++;
                return true;
            }
            if (src[idx] == ',') {
                idx++;
            } else {
                return false;
            }
        }
        return false;
    }

    static bool parseString(const std::string& src, size_t& idx, std::string& str) {
        idx++; // skip '"'
        str.clear();
        while (idx < src.size()) {
            char c = src[idx++];
            if (c == '"') return true;
            if (c == '\\' && idx < src.size()) {
                char esc = src[idx++];
                if (esc == '"') str += '"';
                else if (esc == '\\') str += '\\';
                else if (esc == 'n') str += '\n';
                else if (esc == 't') str += '\t';
                else if (esc == 'r') str += '\r';
                else str += esc;
            } else {
                str += c;
            }
        }
        return false;
    }

    static bool parseNumber(const std::string& src, size_t& idx, JsonValue& val) {
        size_t start = idx;
        if (src[idx] == '-') idx++;
        while (idx < src.size() && (std::isdigit(static_cast<unsigned char>(src[idx])) || src[idx] == '.' || src[idx] == 'e' || src[idx] == 'E' || src[idx] == '+' || src[idx] == '-')) {
            idx++;
        }
        std::string numStr = src.substr(start, idx - start);
        try {
            val.numVal = std::stod(numStr);
            val.type = JsonType::NUMBER;
            return true;
        } catch (...) {
            return false;
        }
    }

    static bool parseBool(const std::string& src, size_t& idx, JsonValue& val) {
        if (src.compare(idx, 4, "true") == 0) {
            val.type = JsonType::BOOL;
            val.boolVal = true;
            idx += 4;
            return true;
        }
        if (src.compare(idx, 5, "false") == 0) {
            val.type = JsonType::BOOL;
            val.boolVal = false;
            idx += 5;
            return true;
        }
        return false;
    }

    static bool parseNull(const std::string& src, size_t& idx, JsonValue& val) {
        if (src.compare(idx, 4, "null") == 0) {
            val.type = JsonType::NUL;
            idx += 4;
            return true;
        }
        return false;
    }
};

static std::string escapeJson(const std::string& s) {
    std::ostringstream o;
    for (char c : s) {
        if (c == '"') o << "\\\"";
        else if (c == '\\') o << "\\\\";
        else if (c == '\b') o << "\\b";
        else if (c == '\f') o << "\\f";
        else if (c == '\n') o << "\\n";
        else if (c == '\r') o << "\\r";
        else if (c == '\t') o << "\\t";
        else o << c;
    }
    return o.str();
}

static void serializeProperty(std::ostringstream& ss, const std::string& propName, const AnimatedProperty& prop, int indent) {
    std::string ind(indent, ' ');
    ss << ind << "\"" << propName << "\": {\n";
    ss << ind << "  \"defaultValue\": " << prop.defaultValue << ",\n";
    ss << ind << "  \"keyframes\": [";
    const auto& kfs = prop.getKeyframes();
    for (size_t i = 0; i < kfs.size(); ++i) {
        const auto& k = kfs[i];
        ss << "\n" << ind << "    {\n";
        ss << ind << "      \"frame\": " << k.frame << ",\n";
        ss << ind << "      \"value\": " << k.value << ",\n";
        ss << ind << "      \"interpolation\": \"" << interpolationToString(k.interpolation) << "\",\n";
        ss << ind << "      \"outHandleX\": " << k.outHandleX << ",\n";
        ss << ind << "      \"outHandleY\": " << k.outHandleY << ",\n";
        ss << ind << "      \"inHandleX\": " << k.inHandleX << ",\n";
        ss << ind << "      \"inHandleY\": " << k.inHandleY << "\n";
        ss << ind << "    }";
        if (i + 1 < kfs.size()) ss << ",";
    }
    if (!kfs.empty()) ss << "\n" << ind << "  ";
    ss << "]\n" << ind << "}";
}

static void deserializeProperty(const JsonValue& obj, AnimatedProperty& prop) {
    if (obj.type != JsonType::OBJECT) return;
    prop.defaultValue = obj.getDouble("defaultValue", prop.defaultValue);
    prop.clearKeyframes();
    const auto& kfsNode = obj.get("keyframes");
    if (kfsNode.type == JsonType::ARRAY) {
        for (const auto& kNode : kfsNode.arrVal) {
            int64_t frame = kNode.getInt64("frame", 0);
            double val = kNode.getDouble("value", 0.0);
            InterpolationType interp = stringToInterpolation(kNode.getString("interpolation", "BEZIER"));
            double ox = kNode.getDouble("outHandleX", 0.42);
            double oy = kNode.getDouble("outHandleY", 0.0);
            double ix = kNode.getDouble("inHandleX", 0.58);
            double iy = kNode.getDouble("inHandleY", 1.0);
            prop.addOrUpdateKeyframe(frame, val, interp, ox, oy, ix, iy);
        }
    }
}

std::string ProjectSerializer::serializeToJson(const Project& project) {
    std::ostringstream ss;
    ss << "{\n";
    ss << "  \"version\": \"1.0\",\n";
    ss << "  \"project\": {\n";
    ss << "    \"title\": \"" << escapeJson(project.title) << "\",\n";
    ss << "    \"width\": " << project.width << ",\n";
    ss << "    \"height\": " << project.height << ",\n";
    ss << "    \"fps\": " << project.fps << ",\n";
    ss << "    \"durationFrames\": " << project.durationFrames << ",\n";
    ss << "    \"backgroundColor\": " << project.backgroundColor << "\n";
    ss << "  },\n";
    ss << "  \"layers\": [\n";

    for (size_t i = 0; i < project.layers.size(); ++i) {
        const auto& layer = project.layers[i];
        if (!layer) continue;

        ss << "    {\n";
        ss << "      \"id\": \"" << escapeJson(layer->id) << "\",\n";
        ss << "      \"name\": \"" << escapeJson(layer->name) << "\",\n";
        ss << "      \"type\": \"" << layerTypeToString(layer->type) << "\",\n";
        ss << "      \"startFrame\": " << layer->startFrame << ",\n";
        ss << "      \"durationFrames\": " << layer->durationFrames << ",\n";
        ss << "      \"zIndex\": " << layer->zIndex << ",\n";
        ss << "      \"isVisible\": " << (layer->isVisible ? "true" : "false") << ",\n";
        ss << "      \"isLocked\": " << (layer->isLocked ? "true" : "false") << ",\n";
        ss << "      \"blendMode\": \"" << blendModeToString(layer->blendMode) << "\",\n";
        ss << "      \"inTransition\": \"" << transitionTypeToString(layer->inTransition) << "\",\n";
        ss << "      \"inTransitionDuration\": " << layer->inTransitionDuration << ",\n";
        ss << "      \"outTransition\": \"" << transitionTypeToString(layer->outTransition) << "\",\n";
        ss << "      \"outTransitionDuration\": " << layer->outTransitionDuration << ",\n";
        ss << "      \"motionBlurEnabled\": " << (layer->motionBlurEnabled ? "true" : "false") << ",\n";
        ss << "      \"motionBlurStrength\": " << layer->motionBlurStrength << ",\n";

        if (layer->type == LayerType::SHAPE) {
            ss << "      \"shape\": {\n";
            ss << "        \"type\": \"" << shapeTypeToString(layer->shapeData.type) << "\",\n";
            ss << "        \"width\": " << layer->shapeData.width << ",\n";
            ss << "        \"height\": " << layer->shapeData.height << ",\n";
            ss << "        \"radius\": " << layer->shapeData.radius << ",\n";
            ss << "        \"points\": " << layer->shapeData.points << ",\n";
            ss << "        \"fillColor\": " << layer->shapeData.fillColor << ",\n";
            ss << "        \"strokeColor\": " << layer->shapeData.strokeColor << ",\n";
            ss << "        \"strokeWidth\": " << layer->shapeData.strokeWidth << ",\n";
            ss << "        \"cornerRadius\": " << layer->shapeData.cornerRadius << "\n";
            ss << "      },\n";
        } else if (layer->type == LayerType::MEDIA) {
            ss << "      \"media\": {\n";
            ss << "        \"filePath\": \"" << escapeJson(layer->mediaData.filePath) << "\",\n";
            ss << "        \"type\": \"" << mediaTypeToString(layer->mediaData.type) << "\",\n";
            ss << "        \"originalWidth\": " << layer->mediaData.originalWidth << ",\n";
            ss << "        \"originalHeight\": " << layer->mediaData.originalHeight << ",\n";
            ss << "        \"sourceDurationFrames\": " << layer->mediaData.sourceDurationFrames << ",\n";
            ss << "        \"trimIn\": " << layer->mediaData.trimIn << ",\n";
            ss << "        \"trimOut\": " << layer->mediaData.trimOut << ",\n";
            ss << "        \"speedMultiplier\": " << layer->mediaData.speedMultiplier << "\n";
            ss << "      },\n";
        } else if (layer->type == LayerType::AUDIO) {
            ss << "      \"audio\": {\n";
            ss << "        \"filePath\": \"" << escapeJson(layer->audioData.filePath) << "\",\n";
            ss << "        \"volume\": " << layer->audioData.volume << ",\n";
            ss << "        \"isMuted\": " << (layer->audioData.isMuted ? "true" : "false") << ",\n";
            ss << "        \"sourceDurationFrames\": " << layer->audioData.sourceDurationFrames << ",\n";
            ss << "        \"trimIn\": " << layer->audioData.trimIn << ",\n";
            ss << "        \"trimOut\": " << layer->audioData.trimOut << "\n";
            ss << "      },\n";
        } else if (layer->type == LayerType::TEXT) {
            ss << "      \"text\": {\n";
            ss << "        \"text\": \"" << escapeJson(layer->textData.text) << "\",\n";
            ss << "        \"fontSize\": " << layer->textData.fontSize << ",\n";
            ss << "        \"textColor\": " << layer->textData.textColor << ",\n";
            ss << "        \"fontFamily\": \"" << escapeJson(layer->textData.fontFamily) << "\"\n";
            ss << "      },\n";
        }

        // Serialize transform tracks
        ss << "      \"transform\": {\n";
        serializeProperty(ss, "posX", layer->transform.posX, 8); ss << ",\n";
        serializeProperty(ss, "posY", layer->transform.posY, 8); ss << ",\n";
        serializeProperty(ss, "posZ", layer->transform.posZ, 8); ss << ",\n";
        serializeProperty(ss, "scaleX", layer->transform.scaleX, 8); ss << ",\n";
        serializeProperty(ss, "scaleY", layer->transform.scaleY, 8); ss << ",\n";
        serializeProperty(ss, "rotation", layer->transform.rotation, 8); ss << ",\n";
        serializeProperty(ss, "opacity", layer->transform.opacity, 8); ss << "\n";
        ss << "      }\n";

        ss << "    }";
        if (i + 1 < project.layers.size()) ss << ",";
        ss << "\n";
    }

    ss << "  ]\n";
    ss << "}\n";
    return ss.str();
}

bool ProjectSerializer::deserializeFromJson(const std::string& jsonString, Project& outProject) {
    JsonValue root;
    if (!MiniJsonParser::parse(jsonString, root)) {
        return false;
    }

    const auto& pObj = root.get("project");
    if (pObj.type == JsonType::OBJECT) {
        outProject.title = pObj.getString("title", "Proyek Baru");
        outProject.width = pObj.getInt("width", 1080);
        outProject.height = pObj.getInt("height", 1920);
        outProject.fps = pObj.getDouble("fps", 60.0);
        outProject.durationFrames = pObj.getInt64("durationFrames", 1800);
        outProject.backgroundColor = static_cast<uint32_t>(pObj.getInt64("backgroundColor", 0xFF131315));
    }

    outProject.layers.clear();
    const auto& layersArr = root.get("layers");
    if (layersArr.type == JsonType::ARRAY) {
        for (const auto& lNode : layersArr.arrVal) {
            std::string id = lNode.getString("id", "layer");
            std::string name = lNode.getString("name", "Layer");
            LayerType type = stringToLayerType(lNode.getString("type", "SHAPE"));
            int64_t start = lNode.getInt64("startFrame", 0);
            int64_t dur = lNode.getInt64("durationFrames", 300);

            auto layer = std::make_shared<Layer>(id, name, type, start, dur);
            layer->zIndex = lNode.getInt("zIndex", 0);
            layer->isVisible = lNode.getBool("isVisible", true);
            layer->isLocked = lNode.getBool("isLocked", false);
            layer->blendMode = stringToBlendMode(lNode.getString("blendMode", "NORMAL"));
            layer->inTransition = stringToTransitionType(lNode.getString("inTransition", "NONE"));
            layer->inTransitionDuration = lNode.getInt64("inTransitionDuration", 0);
            layer->outTransition = stringToTransitionType(lNode.getString("outTransition", "NONE"));
            layer->outTransitionDuration = lNode.getInt64("outTransitionDuration", 0);
            layer->motionBlurEnabled = lNode.getBool("motionBlurEnabled", false);
            layer->motionBlurStrength = lNode.getDouble("motionBlurStrength", 1.0);

            if (type == LayerType::SHAPE) {
                const auto& sNode = lNode.get("shape");
                if (sNode.type == JsonType::OBJECT) {
                    layer->shapeData.type = stringToShapeType(sNode.getString("type", "RECTANGLE"));
                    layer->shapeData.width = sNode.getDouble("width", 200.0);
                    layer->shapeData.height = sNode.getDouble("height", 200.0);
                    layer->shapeData.radius = sNode.getDouble("radius", 100.0);
                    layer->shapeData.points = sNode.getInt("points", 5);
                    layer->shapeData.fillColor = static_cast<uint32_t>(sNode.getInt64("fillColor", 0xFFFFFFFF));
                    layer->shapeData.strokeColor = static_cast<uint32_t>(sNode.getInt64("strokeColor", 0xFF000000));
                    layer->shapeData.strokeWidth = static_cast<float>(sNode.getDouble("strokeWidth", 0.0));
                    layer->shapeData.cornerRadius = static_cast<float>(sNode.getDouble("cornerRadius", 0.0));
                }
            } else if (type == LayerType::MEDIA) {
                const auto& mNode = lNode.get("media");
                if (mNode.type == JsonType::OBJECT) {
                    layer->mediaData.filePath = mNode.getString("filePath", "");
                    layer->mediaData.type = stringToMediaType(mNode.getString("type", "IMAGE"));
                    layer->mediaData.originalWidth = mNode.getInt("originalWidth", 0);
                    layer->mediaData.originalHeight = mNode.getInt("originalHeight", 0);
                    layer->mediaData.sourceDurationFrames = mNode.getInt64("sourceDurationFrames", 0);
                    layer->mediaData.trimIn = mNode.getInt64("trimIn", 0);
                    layer->mediaData.trimOut = mNode.getInt64("trimOut", 0);
                    layer->mediaData.speedMultiplier = mNode.getDouble("speedMultiplier", 1.0);
                }
            } else if (type == LayerType::AUDIO) {
                const auto& aNode = lNode.get("audio");
                if (aNode.type == JsonType::OBJECT) {
                    layer->audioData.filePath = aNode.getString("filePath", "");
                    layer->audioData.volume = static_cast<float>(aNode.getDouble("volume", 1.0));
                    layer->audioData.isMuted = aNode.getBool("isMuted", false);
                    layer->audioData.sourceDurationFrames = aNode.getInt64("sourceDurationFrames", 0);
                    layer->audioData.trimIn = aNode.getInt64("trimIn", 0);
                    layer->audioData.trimOut = aNode.getInt64("trimOut", 0);
                }
            } else if (type == LayerType::TEXT) {
                const auto& tDataNode = lNode.get("text");
                if (tDataNode.type == JsonType::OBJECT) {
                    layer->textData.text = tDataNode.getString("text", "MotionLab");
                    layer->textData.fontSize = tDataNode.getDouble("fontSize", 48.0);
                    layer->textData.textColor = static_cast<uint32_t>(tDataNode.getInt64("textColor", 0xFFFFFFFF));
                    layer->textData.fontFamily = tDataNode.getString("fontFamily", "sans-serif");
                }
            }

            const auto& tNode = lNode.get("transform");
            if (tNode.type == JsonType::OBJECT) {
                deserializeProperty(tNode.get("posX"), layer->transform.posX);
                deserializeProperty(tNode.get("posY"), layer->transform.posY);
                deserializeProperty(tNode.get("posZ"), layer->transform.posZ);
                deserializeProperty(tNode.get("scaleX"), layer->transform.scaleX);
                deserializeProperty(tNode.get("scaleY"), layer->transform.scaleY);
                deserializeProperty(tNode.get("rotation"), layer->transform.rotation);
                deserializeProperty(tNode.get("opacity"), layer->transform.opacity);
            }

            outProject.addLayer(layer);
        }
    }

    return true;
}

bool ProjectSerializer::saveToFile(const std::string& filePath, const Project& project) {
    std::ofstream out(filePath);
    if (!out.is_open()) return false;
    out << serializeToJson(project);
    return out.good();
}

bool ProjectSerializer::loadFromFile(const std::string& filePath, Project& outProject) {
    std::ifstream in(filePath);
    if (!in.is_open()) return false;
    std::string content((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    return deserializeFromJson(content, outProject);
}

} // namespace motion
