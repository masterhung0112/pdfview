package com.hungknow.pdfsdk

import android.graphics.RectF
import com.hungknow.pdfsdk.utils.MathUtils


class PagesLoader(val pdfView: PdfView) {
    private class Holder {
        var row = 0
        var col = 0
        override fun toString(): String {
            return "Holder{" +
                    "row=" + row +
                    ", col=" + col +
                    '}'
        }
    }

    private class RenderRange {
        var page = 0
        var gridSize: GridSize = GridSize()
        var leftTop: Holder = Holder()
        var rightBottom: Holder = Holder()

        override fun toString(): String {
            return "RenderRange{" +
                    "page=" + page +
                    ", gridSize=" + gridSize +
                    ", leftTop=" + leftTop +
                    ", rightBottom=" + rightBottom +
                    '}'
        }
    }

    private class GridSize {
        var rows = 0
        var cols = 0
        override fun toString(): String {
            return "GridSize{" +
                    "rows=" + rows +
                    ", cols=" + cols +
                    '}'
        }
    }

    private var cacheOrder = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f
    private var thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private var preloadOffset = 0

    private fun loadVisible() {
        var parts = 0
        var scaledPreloadOffset = preloadOffset
        var firstXOffset = -xOffset + scaledPreloadOffset
        var lastXOffset = -xOffset - pdfView.width - scaledPreloadOffset
        val firstYOffset = -yOffset + scaledPreloadOffset
        val lastYOffset = -yOffset - pdfView.height - scaledPreloadOffset

        val rangeList: List<RenderRange> =
            getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset)

    }

    /**
     * calculate the render range of each page
     */
    private fun getRenderRangeList(firstXOffset: Float, firstYOffset: Float, lastXOffset: Float, lastYOffset: Float): List<RenderRange> {
        val fixedFirstXOffset: Float = -MathUtils.max(firstXOffset, 0f)
        val fixedFirstYOffset: Float = -MathUtils.max(firstYOffset, 0f)

        val fixedLastXOffset: Float = -MathUtils.max(lastXOffset, 0f)
        val fixedLastYOffset: Float = -MathUtils.max(lastYOffset, 0f)

        val offsetFirst = if (pdfView.swipeVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast = if (pdfView.swipeVertical) fixedLastYOffset else fixedLastXOffset

        val firstPage: Int = pdfView.pdfFile.getPageAtOffset(offsetFirst, pdfView.zoom)
        val lastPage: Int = pdfView.pdfFile.getPageAtOffset(offsetLast, pdfView.zoom)
        val pageCount = lastPage - firstPage + 1
    }

    fun loadPages() {
        cacheOrder = 1
        xOffset = -MathUtils.max(pdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(pdfView.currentYOffset, 0f)

        loadVisible()
    }
}