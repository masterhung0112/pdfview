package com.hungknow.pdfsdk

import android.os.ParcelFileDescriptor

class PdfiumSDK {
    external fun nativeOpenDocument(fd: Int, password: String): Long
    external fun nativeOpenMemDocument(data: ByteArray, password: String): Long
    private val mNativeDocPtr: Long = 0

    fun newDocument(pfd: ParcelFileDescriptor, password: String): PdfDocument {
        val nativeDocumentPtr = nativeOpenDocument(pfd.fd, password)
        return PdfDocument(nativeDocumentPtr, pfd)
    }

    companion object {
        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }
}