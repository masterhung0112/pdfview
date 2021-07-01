package com.hungknow.pdfsdk.source

import android.R.attr
import android.content.Context
import android.os.ParcelFileDescriptor
import com.hungknow.pdfsdk.PdfDocument
import com.hungknow.pdfsdk.PdfiumSDK
import java.io.File

class AssetSource(val assetName: String): DocumentSource {
    override fun createDocument(context: Context, core: PdfiumSDK, password: String): PdfDocument {
        val outFile = File(context.cacheDir, assetName + "-pdfview.pdf")
        if (assetName.contains("/")) {
            outFile.parentFile.mkdirs()
        }
        context.assets.open(assetName).copyTo(outFile.outputStream())
        val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(pfd, password)
    }
}