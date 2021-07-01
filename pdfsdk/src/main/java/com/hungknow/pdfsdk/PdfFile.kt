package com.hungknow.pdfsdk

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.SparseBooleanArray
import com.hungknow.pdfsdk.exceptions.PageRenderingException
import com.hungknow.pdfsdk.models.Size
import com.hungknow.pdfsdk.models.SizeF
import com.hungknow.pdfsdk.utils.FitPolicy
import java.util.*

class PdfFile(
    val pdfiumSDK: PdfiumSDK,
    var pdfDocument: PdfDocument?,
    val pageFitPolicy: FitPolicy,
    val viewSize: Size,
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    var originalUserPages: List<Int>? = mutableListOf(),
    /** True if scrolling is vertical, else it's horizontal  */
    val isVertical: Boolean,
    /** Fixed spacing between pages in pixels  */
    val spacing: Float,
    /** Calculate spacing automatically so each page fits on it's own in the center of the view  */
    val autoSpacing: Boolean,
    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    val fitEachPage: Boolean
) {
    companion object {
        val lock = Any()
    }

    var pagesCount = 0
        private set

    /** Original page sizes  */
    private val originalPageSizes = mutableListOf<Size>()

    /** Scaled page sizes  */
    private val pageSizes = mutableListOf<SizeF>()

    /** Opened pages with indicator whether opening was successful  */
    private val openedPages = SparseBooleanArray()

    /** Page with maximum width  */
    private val originalMaxWidthPageSize = Size(0, 0)

    /** Page with maximum height  */
    private val originalMaxHeightPageSize = Size(0, 0)

    /** Scaled page with maximum height  */
    private val maxHeightPageSize = SizeF(0f, 0f)

    /** Scaled page with maximum width  */
    private val maxWidthPageSize = SizeF(0f, 0f)

    /** True if scrolling is vertical, else it's horizontal  */
//    private val isVertical = false

    /** Fixed spacing between pages in pixels  */
//    private val spacingPx = 0

    /** Calculate spacing automatically so each page fits on it's own in the center of the view  */
//    private val autoSpacing = false

    /** Calculated offsets for pages  */
    private val pageOffsets = mutableListOf<Float>()

    /** Calculated auto spacing for pages  */
    private val pageSpacing = mutableListOf<Float>()

    /** Calculated document length (width or height, depending on swipe mode)  */
    private val documentLength = 0f
//    private val pageFitPolicy: FitPolicy? = null

    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
//    private val fitEachPage = false

    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
//    private val originalUserPages = mutableListOf<Int>()

    val maxPageSize
        get() = if (isVertical) maxWidthPageSize else maxHeightPageSize

    val maxPageWidth
        get() = maxPageSize.width

    val maxPageHeight
        get() = maxPageSize.height

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var currentPage = 0
        for (i in 0 until pagesCount) {
            val off: Float = pageOffsets.get(i) * zoom - getPageSpacing(i, zoom) / 2f
            if (off >= offset) {
                break
            }
            currentPage++
        }
        return if (--currentPage >= 0) currentPage else 0
    }

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        val spacing = if (autoSpacing) pageSpacing[pageIndex] else spacing
        return spacing.toFloat() * zoom
    }

    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        var docPage = documentPage(pageIndex)
        if (docPage < 0) {
            docPage = 0
        }
        return pageOffsets.get(pageIndex) * zoom
    }

    /** Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll  */
    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val pageSize = getPageSize(pageIndex)
        return if (isVertical) {
            val maxWidth: Float = maxPageWidth
            zoom * (maxWidth - pageSize.width) / 2 //x
        } else {
            val maxHeight: Float = maxPageHeight
            zoom * (maxHeight - pageSize.height) / 2 //y
        }
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val docPage = documentPage(pageIndex)
        return if (docPage < 0) {
            SizeF(0f, 0f)
        } else pageSizes[pageIndex]
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val size = getPageSize(pageIndex)
        return SizeF(size.width * zoom, size.height * zoom)
    }


    fun documentPage(userPage: Int): Int {
        var documentPage = userPage
        originalUserPages?.let { originalUserPages ->
            if (userPage < 0 || userPage >= originalUserPages.size) {
                return -1
            } else {
                documentPage = originalUserPages[userPage]
            }
        }

        if (documentPage < 0 || userPage >= pagesCount) {
            return -1
        }

        return documentPage
    }

    fun getDocLen(zoom: Float): Float {
        return documentLength * zoom
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val size = getPageSize(pageIndex)
        return (if (isVertical) size.height else size.width) * zoom
    }

    fun dispose() {
        pdfiumSDK?.let {pdfiumSDK ->
            pdfDocument?.let { pdfDocument ->
                pdfiumSDK.closeDocument(pdfDocument)
            }
        }
        pdfDocument = null
        originalUserPages = null
    }

    fun openPage(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        if (docPage < 1) {
            return false
        }

        synchronized(lock) {
            if (openedPages.indexOfKey(docPage) < 0) {
                try {
                    val pdfDocument = this.pdfDocument ?: return false
                    pdfiumSDK.openPage(pdfDocument, docPage)
                    openedPages.put(docPage, true)
                    return true
                } catch (e: Exception) {
                    openedPages.put(docPage, false)
                    throw PageRenderingException(pageIndex, e)
                }
            }
            return false
        }
    }

    fun pageHasError(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        return !openedPages.get(docPage, false)
    }

    fun renderPageBitmap(bitmap: Bitmap, pageIndex: Int, bounds: Rect, annotationRendering: Boolean) {
        val pdfDocument = this.pdfDocument ?: return
        val docPage = documentPage(pageIndex)
        pdfiumSDK.renderPageBitmap(pdfDocument, bitmap, docPage, bounds.left, bounds.top, bounds.width(), bounds.height(), annotationRendering)
    }
}