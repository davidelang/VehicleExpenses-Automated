#pragma once

#include <opencv2/core.hpp>
#include <string>

/** Develop one DNG from an open fd into an RGB CV_8UC3 Mat sized by that image. */
bool veDngDevelopRgb(int fd, cv::Mat* outRgb, std::string* err);
