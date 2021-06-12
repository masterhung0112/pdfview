package com.hungknow.pdfsdk

class PdfiumSDK {
    external fun nativeOpenDocument(fd: Int, password: String): Long

    companion object {
        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }
}