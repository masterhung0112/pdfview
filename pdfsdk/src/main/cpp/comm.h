#ifndef PDFVIEW_COMM_H
#define PDFVIEW_COMM_H

#include <jni.h>

extern "C" {
#include <stdlib.h>
}

#include <android/log.h>

#define JNI_FUNC(retType, bindClass, name)  JNIEXPORT retType JNICALL Java_com_hungknow_pdfsdk_##bindClass##_##name
#define JNI_ARGS    JNIEnv *env, jobject thiz

#define LOG_TAG "PDFSDK"
#define LOGI(...)   __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...)   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...)   __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#endif //PDFVIEW_COMM_H
