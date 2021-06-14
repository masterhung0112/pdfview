package com.hungknow.pdfsdk

import android.content.Context
import android.os.HandlerThread
import android.util.AttributeSet
import android.widget.RelativeLayout

class PdfView: RelativeLayout {
    var renderingHandlerThread = HandlerThread("PDF Renderer")

    /** Handler always waiting in the background and rendering tasks  */
    var renderingHandler: RenderingHandler? = null

    lateinit var pdfiumSdk: PdfiumSDK
    lateinit var pagesLoader: PagesLoader

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    internal enum class ScrollDir {
        NONE, START, END
    }

    private val scrollDir = ScrollDir.NONE

    /** Rendered parts go to the cache manager  */
    val cacheManager = CacheManager()

    /** The index of the current sequence */
    var currentPage = 0
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentXOffset = 0f
        private set

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    var currentYOffset = 0f
        private set

    /** True if should scroll through pages vertically instead of horizontally */
    var swipeVertical = true
        private set

    // The zoom level, always >= 1
    var zoom = 1f
        private set

    var pdfFile: PdfFile? = null
        private set

    // True if PDFView has been recycled
    private var recycled = true

    private lateinit var pdfDocument: PdfDocument

    constructor(context: Context, set: AttributeSet): super(context) {
        if (isInEditMode) {
            return
        }

        pagesLoader = PagesLoader(this)
        pdfiumSdk = PdfiumSDK(context.resources.displayMetrics.densityDpi)
        setWillNotDraw(false)
    }

    fun showPage(pageNb: Int) {
        if (recycled) {
            return
        }

        // Check the page number and makes the difference between UserPages and DocumentPages
        var validPageNb = pdfDocument.determineValidPageNumberFrom(pageNb)
        currentPage = validPageNb

        loadPages()

//        if (scrollHandle != null && !documentFitsView()) {
//            scrollHandle.setPageNum(currentPage + 1)
//        }
//
//        callbacks.callOnPageChange(currentPage, pdfFile.getPagesCount())
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    fun loadPages() {
        if (pdfDocument == null || renderingHandler == null) {
            return
        }

        // Cancel all current tasks
        renderingHandler!!.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        cacheManager.makeANewSet()
        pagesLoader.loadPages()
        invalidate()
    }

}