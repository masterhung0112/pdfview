package com.hungknow.pdfsdk.source

import android.content.Context
import com.hungknow.pdfsdk.PdfDocument
import com.hungknow.pdfsdk.PdfiumSDK

interface DocumentSource {
    fun createDocument(context: Context, core: PdfiumSDK, password: String): PdfDocument
}