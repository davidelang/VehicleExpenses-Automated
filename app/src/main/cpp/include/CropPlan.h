#pragma once

#include <cmath>

struct Plan40 {
    float s;
    int destW;
    int destH;
    int srcW;
    int srcH;
    float marginSrc;
};

inline int veEvenAtLeast2(int n) {
    int v = n < 2 ? 2 : n;
    return (v & 1) ? (v + 1) : v;
}

inline int veJavaRoundFloat(float a) {
    if (!(a == a)) return 0;
    if (a >= 2147483647.f) return 2147483647;
    if (a <= -2147483648.f) return static_cast<int>(-2147483647 - 1);
    return static_cast<int>(std::floor(static_cast<double>(a) + 0.5));
}

/** One crop planner for QfRotGmm and ExpRotGmm. Width is the scaled crop. Scale stays 40/cropH. */
inline Plan40 planContent40(float cropW, float cropH, int destHIn) {
    constexpr int kBorderPx = 4;
    constexpr int kRightBorderPx = 4;
    const float h = cropH < 1.f ? 1.f : cropH;
    const float w = cropW < 1.f ? 1.f : cropW;
    const int canvasH = destHIn < 2 ? 2 : destHIn;
    auto destWidth = [&](float scale) {
        return veEvenAtLeast2(kBorderPx + kRightBorderPx + veJavaRoundFloat(w * scale));
    };
    const float s = 40.f / h;
    const int destW = destWidth(s);
    const int srcW = veEvenAtLeast2(static_cast<int>(std::ceil((destW + 1.0) / static_cast<double>(s))));
    const int srcH = veEvenAtLeast2(static_cast<int>(std::ceil((canvasH + 1.0) / static_cast<double>(s))));
    Plan40 plan;
    plan.s = s;
    plan.destW = destW;
    plan.destH = canvasH;
    plan.srcW = srcW;
    plan.srcH = srcH;
    plan.marginSrc = static_cast<float>(kBorderPx) / s;
    return plan;
}
