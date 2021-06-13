package com.hungknow.pdfsdk

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.IOException

class PdfiumSDK(ctx: Context) {
    var mCurrentDpi: Int = ctx.resources.displayMetrics.densityDpi

    external fun nativeOpenDocument(fd: Int, password: String): Long
    external fun nativeOpenMemDocument(data: ByteArray, password: String): Long
    external fun nativeGetPageCount(documentPtr: Long): Int
    external fun nativeCloseDocument(documentPtr: Long)
    private external fun nativeGetDocumentMetaText(documentPtr: Long, tag: String): String
    private external fun nativeLoadPage(documentPtr: Long, pageIndex: Int): Long
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)

    ///////////////////////////////////////
    // PDF TextPage api
    ///////////
    private external fun nativeCloseTextPage(pagePtr: Long)

    fun newDocument(pfd: ParcelFileDescriptor, password: String): PdfDocument {
        val nativeDocumentPtr = nativeOpenDocument(pfd.fd, password)
        return PdfDocument(nativeDocumentPtr, pfd)
    }

    fun closeDocument(doc: PdfDocument) {
        for (index in doc.NativePagesPtr.keys) {
            doc.NativePagesPtr.get(index)?.let { nativeClosePage(it) }
        }
        doc.NativePagesPtr.clear()
        for (ptr in doc.NativeTextPagesPtr.keys) {
            doc.NativeTextPagesPtr.get(ptr)?.let { nativeCloseTextPage(it) }
        }
        doc.NativeTextPagesPtr.clear()
        nativeCloseDocument(doc.NativeDocPtr)
        if (doc.FileDescriptor != null) {
            try {
                doc.FileDescriptor!!.close()
            } catch (ignored: IOException) {
            } finally {
                doc.FileDescriptor = null
            }
        }
    }

    fun getDocumentMeta(doc: PdfDocument): PdfDocumentMeta = PdfDocumentMeta(
        title = nativeGetDocumentMetaText(doc.NativeDocPtr, "Title"),
        author = nativeGetDocumentMetaText(doc.NativeDocPtr, "Author"),
        subject = nativeGetDocumentMetaText (doc.NativeDocPtr, "Subject"),
        keywords = nativeGetDocumentMetaText(doc.NativeDocPtr, "Keywords"),
        creator = nativeGetDocumentMetaText(doc.NativeDocPtr, "Creator"),
        producer = nativeGetDocumentMetaText(doc.NativeDocPtr, "Producer"),
        creationDate = nativeGetDocumentMetaText(doc.NativeDocPtr, "CreationDate"),
        modDate = nativeGetDocumentMetaText(doc.NativeDocPtr, "ModDate")
    )

    // Open page and store native pointer
    fun openPage(doc: PdfDocument, pageIndex: Int): Long {
        var pagePtr = nativeLoadPage(doc.NativeDocPtr, pageIndex)
        doc.NativePagesPtr.put(pageIndex, pagePtr)
        return pagePtr
    }

    companion object {
        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }
}