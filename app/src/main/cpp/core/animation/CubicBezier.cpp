#include "CubicBezier.h"

namespace motion {

constexpr double kEpsilon = 1e-6;

CubicBezier::CubicBezier(double p1x, double p1y, double p2x, double p2y)
    : x1(std::clamp(p1x, 0.0, 1.0)),
      y1(p1y),
      x2(std::clamp(p2x, 0.0, 1.0)),
      y2(p2y) {}

CubicBezier CubicBezier::linear() {
    return CubicBezier(0.0, 0.0, 1.0, 1.0);
}

CubicBezier CubicBezier::easeIn() {
    return CubicBezier(0.42, 0.0, 1.0, 1.0);
}

CubicBezier CubicBezier::easeOut() {
    return CubicBezier(0.0, 0.0, 0.58, 1.0);
}

CubicBezier CubicBezier::easeInOut() {
    return CubicBezier(0.42, 0.0, 0.58, 1.0);
}

double CubicBezier::sampleCurveX(double t) const {
    // ((1-3*x2+3*x1)*t + (3*x2-6*x1))*t + 3*x1)*t
    double cx = 3.0 * x1;
    double bx = 3.0 * (x2 - x1) - cx;
    double ax = 1.0 - cx - bx;
    return ((ax * t + bx) * t + cx) * t;
}

double CubicBezier::sampleCurveY(double t) const {
    double cy = 3.0 * y1;
    double by = 3.0 * (y2 - y1) - cy;
    double ay = 1.0 - cy - by;
    return ((ay * t + by) * t + cy) * t;
}

double CubicBezier::sampleCurveDerivativeX(double t) const {
    double cx = 3.0 * x1;
    double bx = 3.0 * (x2 - x1) - cx;
    double ax = 1.0 - cx - bx;
    return (3.0 * ax * t + 2.0 * bx) * t + cx;
}

double CubicBezier::solveCurveX(double x) const {
    // First try Newton-Raphson iteration
    double t = x;
    for (int i = 0; i < 8; ++i) {
        double currentX = sampleCurveX(t) - x;
        if (std::abs(currentX) < kEpsilon) {
            return t;
        }
        double dX = sampleCurveDerivativeX(t);
        if (std::abs(dX) < kEpsilon) {
            break;
        }
        t -= currentX / dX;
    }

    // Fall back to bisection if Newton's method fails or diverges
    double t0 = 0.0;
    double t1 = 1.0;
    t = x;

    if (t < t0) return t0;
    if (t > t1) return t1;

    while (t0 < t1) {
        double currentX = sampleCurveX(t);
        if (std::abs(currentX - x) < kEpsilon) {
            return t;
        }
        if (x > currentX) {
            t0 = t;
        } else {
            t1 = t;
        }
        t = (t1 + t0) * 0.5;
    }

    return t;
}

double CubicBezier::solve(double x) const {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    if (std::abs(x1 - y1) < kEpsilon && std::abs(x2 - y2) < kEpsilon) {
        return x; // Perfectly linear
    }
    return sampleCurveY(solveCurveX(x));
}

} // namespace motion
