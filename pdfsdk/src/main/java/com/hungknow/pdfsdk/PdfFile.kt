package com.hungknow.pdfsdk

import android.util.SparseBooleanArray
import com.hungknow.pdfsdk.models.Size
import com.hungknow.pdfsdk.models.SizeF
import com.hungknow.pdfsdk.utils.FitPolicy
import java.util.*

class PdfFile {
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
    private val isVertical = false

    /** Fixed spacing between pages in pixels  */
    private val spacingPx = 0

    /** Calculate spacing automatically so each page fits on it's own in the center of the view  */
    private val autoSpacing = false

    /** Calculated offsets for pages  */
    private val pageOffsets = mutableListOf<Float>()

    /** Calculated auto spacing for pages  */
    private val pageSpacing = mutableListOf<Float>()

    /** Calculated document length (width or height, depending on swipe mode)  */
    private val documentLength = 0f
    private val pageFitPolicy: FitPolicy? = null

    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    private val fitEachPage = false

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
        val spacing = if (autoSpacing) pageSpacing[pageIndex] else spacingPx
        return spacing * zoom
    }
}