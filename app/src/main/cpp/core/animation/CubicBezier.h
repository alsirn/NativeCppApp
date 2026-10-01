#pragma once

#include <cmath>
#include <algorithm>

namespace motion {

class CubicBezier {
public:
    double x1;
    double y1;
    double x2;
    double y2;

    CubicBezier(double p1x = 0.42, double p1y = 0.0, double p2x = 0.58, double p2y = 1.0);

    // Evaluates Y for a given progress X (0.0 to 1.0)
    double solve(double x) const;

    // Standard preset curves
    static CubicBezier linear();
    static CubicBezier easeIn();
    static CubicBezier easeOut();
    static CubicBezier easeInOut();

private:
    double sampleCurveX(double t) const;
    double sampleCurveY(double t) const;
    double sampleCurveDerivativeX(double t) const;
    double solveCurveX(double x) const;
};

} // namespace motion
