#pragma once

#include <string>
#include <memory>
#include "../model/Project.h"

namespace motion {

class ProjectSerializer {
public:
    static std::string serializeToJson(const Project& project);
    static bool deserializeFromJson(const std::string& jsonString, Project& outProject);

    static bool saveToFile(const std::string& filePath, const Project& project);
    static bool loadFromFile(const std::string& filePath, Project& outProject);
};

} // namespace motion
