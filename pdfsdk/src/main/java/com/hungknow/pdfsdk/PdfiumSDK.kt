package com.hungknow.pdfsdk

import android.R.attr
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import java.io.FileDescriptor
import java.io.IOException


class PdfiumSDK(val densityDpi: Int) {

    external fun nativeOpenDocument(fd: Int, password: String): Long
    external fun nativeOpenMemDocument(data: ByteArray, password: String): Long
    external fun nativeGetPageCount(documentPtr: Long): Int
    external fun nativeCloseDocument(documentPtr: Long)
    private external fun nativeGetDocumentMetaText(documentPtr: Long, tag: String): String
    private external fun nativeLoadPage(documentPtr: Long, pageIndex: Int): Long
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeRenderPage(pagePtr: Long, surface: Surface, dpi: Int,
                                          startX: Int, startY: Int,
                                          drawSizeHor: Int, drawSizeVer: Int,
                                          renderAnnot: Boolean)

    private external fun nativeRenderPageBitmap(pagePtr: Long, bitmap: Bitmap, dpi: Int,
    startX: Int, startY: Int,
    drawSizeHor: Int, drawSizeVer: Int,
    renderAnnot: Boolean)

    ///////////////////////////////////////
    // PDF TextPage api
    ///////////
    private external fun nativeCloseTextPage(pagePtr: Long)

    fun getPageCount(doc: PdfDocument): Int {
       return nativeGetPageCount(doc.NativeDocPtr)
    }

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

    // Render page fragment on Bitmap. page must be opened before rendering
    // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
    // RGB_565 - little worse quality, twice less memory usage
    fun renderPageBitmap(doc: PdfDocument, bitmap: Bitmap, pageIndex: Int, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int) {
        renderPageBitmap(doc, bitmap, pageIndex, startX, startY, drawSizeX, drawSizeY, false);
    }

    fun renderPageBitmap(doc: PdfDocument, bitmap: Bitmap, pageIndex: Int, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, renderAnnot: Boolean) {
        synchronized(lock) {
            try {
                doc.NativePagesPtr[pageIndex]?.let {
                    nativeRenderPageBitmap(
                        it, bitmap, mCurrentDpi,
                        startX, startY, drawSizeX, drawSizeY, renderAnnot
                    )
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "mContext may be null")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native")
                e.printStackTrace()
            }
        }
    }

    companion object {
        val lock = Any()
        val TAG = PdfiumSDK::class.simpleName
        val FD_CLASS = FileDescriptor::class
        val FD_FIELD_NAME = "descriptor"
        val mCurrentDpi = 72

        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }
}