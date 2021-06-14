package com.hungknow.pdfsdk

import android.graphics.RectF
import com.hungknow.pdfsdk.utils.Constants.Companion.PART_SIZE
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

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val size = pdfView.pdfFile!!.getPageSize(pageIndex)
        val ratioX: Float = 1f / size.width
        val ratioY: Float = 1f / size.height
        val partHeight: Float = PART_SIZE * ratioY / pdfView.zoom
        val partWidth: Float = PART_SIZE * ratioX / pdfView.zoom
        grid.rows = MathUtils.ceil(1f / partHeight)
        grid.cols = MathUtils.ceil(1f / partWidth)
    }

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

        val firstPage: Int = pdfView.pdfFile!!.getPageAtOffset(offsetFirst, pdfView.zoom)
        val lastPage: Int = pdfView.pdfFile!!.getPageAtOffset(offsetLast, pdfView.zoom)
        val pageCount = lastPage - firstPage + 1

        val renderRanges = mutableListOf<RenderRange>()

        for (page in firstPage..lastPage) {
            val range = RenderRange()
            range.page = page

            var pageFirstXOffset: Float
            var pageFirstYOffset: Float
            var pageLastXOffset: Float
            var pageLastYOffset: Float
            if (page == firstPage) {
                pageFirstXOffset = fixedFirstXOffset
                pageFirstYOffset = fixedFirstYOffset
                if (page == 1) {
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = fixedLastYOffset
                } else {
                    val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)
                    val pageSize = pdfView.pdfFile!!.getScaledPageSize(page, pdfView.zoom)
                    if (pdfView.swipeVertical) {
                        pageLastXOffset = fixedLastXOffset;
                        pageLastYOffset = pageOffset + pageSize.height;
                    } else {
                        pageLastYOffset = fixedLastYOffset;
                        pageLastXOffset = pageOffset + pageSize.width;
                    }
                }
            } else if (page == lastPage) {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)

                if (pdfView.swipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                } else {
                    pageFirstYOffset = fixedFirstYOffset
                    pageFirstXOffset = pageOffset
                }

                pageLastXOffset = fixedLastXOffset
                pageLastYOffset = fixedLastYOffset
            } else {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)
                val pageSize = pdfView.pdfFile!!.getScaledPageSize(page, pdfView.zoom)
                if (pdfView.swipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = pageOffset + pageSize.height
                } else {
                    pageFirstXOffset = pageOffset
                    pageFirstYOffset = fixedFirstYOffset
                    pageLastXOffset = pageOffset + pageSize.width
                    pageLastYOffset = fixedLastYOffset
                }
            }

            // get the page's grid size that rows and cols
            getPageColsRows(
                range.gridSize,
                range.page
            )

            val scaledPageSize =
                pdfView.pdfFile!!.getScaledPageSize(range.page, pdfView.zoom)
            val rowHeight: Float = scaledPageSize.height / range.gridSize.rows
            val colWidth: Float = scaledPageSize.width / range.gridSize.cols

            // get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            val secondaryOffset: Float = pdfView.pdfFile!!.getSecondaryPageOffset(page, pdfView.zoom)

            // calculate the row,col of the point in the leftTop and rightBottom

            // calculate the row,col of the point in the leftTop and rightBottom
            if (pdfView.swipeVertical) {
                range.leftTop.row = MathUtils.floor(
                    Math.abs(
                        pageFirstYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )
                    ) / rowHeight
                )
                range.leftTop.col =
                    MathUtils.floor(MathUtils.min(pageFirstXOffset - secondaryOffset, 0f) / colWidth)
                range.rightBottom.row = MathUtils.ceil(
                    Math.abs(
                        pageLastYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )
                    ) / rowHeight
                )
                range.rightBottom.col =
                    MathUtils.floor(MathUtils.min(pageLastXOffset - secondaryOffset, 0f) / colWidth)
            } else {
                range.leftTop.col = MathUtils.floor(
                    Math.abs(
                        pageFirstXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )
                    ) / colWidth
                )
                range.leftTop.row = MathUtils.floor(
                    MathUtils.min(
                        pageFirstYOffset - secondaryOffset,
                        0f
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    Math.abs(
                        pageLastXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )
                    ) / colWidth
                )
                range.rightBottom.row =
                    MathUtils.floor(MathUtils.min(pageLastYOffset - secondaryOffset, 0f) / rowHeight)
            }

            renderRanges.add(range)
        }

        return renderRanges
    }

    fun loadPages() {
        cacheOrder = 1
        xOffset = -MathUtils.max(pdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(pdfView.currentYOffset, 0f)

        loadVisible()
    }
}