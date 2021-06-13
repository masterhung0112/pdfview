package com.hungknow.pdfsdk

import android.os.ParcelFileDescriptor

class PdfDocument(val NativeDocPtr: Long, var FileDescriptor: ParcelFileDescriptor?) {
    val NativePagesPtr = mutableMapOf<Int, Long>()
    val NativeTextPagesPtr = mutableMapOf<Int, Long>()
}