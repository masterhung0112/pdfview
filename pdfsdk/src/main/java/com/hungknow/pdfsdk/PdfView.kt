package com.hungknow.pdfsdk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.os.HandlerThread
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.hungknow.pdfsdk.listeners.Callbacks
import com.hungknow.pdfsdk.models.PagePart


class PdfView: RelativeLayout {
    companion object {
        private val TAG: String = PdfView::class.java.simpleName

        const val DEFAULT_MAX_SCALE = 3.0f
        const val DEFAULT_MID_SCALE = 1.75f
        const val DEFAULT_MIN_SCALE = 1.0f
    }

    private val minZoom: Float = DEFAULT_MIN_SCALE
    private val midZoom: Float = DEFAULT_MID_SCALE
    private val maxZoom: Float = DEFAULT_MAX_SCALE

    var renderingHandlerThread = HandlerThread("PDF Renderer")

    /** Handler always waiting in the background and rendering tasks  */
    var renderingHandler: RenderingHandler? = null

    lateinit var pdfiumSdk: PdfiumSDK
    lateinit var pagesLoader: PagesLoader
    val callbacks = Callbacks()


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

    /** Current state of the view  */
    private val state = State.DEFAULT

    private lateinit var pdfDocument: PdfDocument

    /** Paint object for drawing  */
    private val paint: Paint? = null

    /** Paint object for drawing debug stuff  */
    private val debugPaint: Paint? = null

    /** Antialiasing and bitmap filtering  */
    private val enableAntialiasing = true
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var nightMode = false

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

    override fun onDraw(canvas: Canvas?) {
        if (isInEditMode || canvas == null) {
            return
        }

        // Draw background
        if (enableAntialiasing) {
            canvas.drawFilter = antialiasFilter
        }

        val bg = background
        if (bg == null) {
            canvas.drawColor(if (nightMode) Color.BLACK else Color.WHITE)
        } else {
            bg.draw(canvas)
        }

        if (recycled) {
            return
        }

        if (state != State.SHOWN) {
            return
        }

        // Moves the canvas before drawing any element
        val currentXOffset = this.currentXOffset
        val currentYOffset = this.currentYOffset
        canvas.translate(currentXOffset, currentYOffset)

        // Draw thumbnails
        for (part in cacheManager.thumbnails) {
            drawPart(canvas, part)
        }

        // Draw parts
        for (part in cacheManager.pageParts) {
            drawPart(canvas, part)
            if (callbacks.getOnDrawAll() != null
                && !onDrawPagesNums.contains(part.page)) {
                onDrawPagesNums.add(part.page);
            }
        }

        for (page in onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.getOnDrawAll())
        }
        onDrawPagesNums.clear()

        drawWithListener(canvas, currentPage, callbacks.getOnDraw())

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    /** Draw a given PagePart on the canvas */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        // Can seem strange, but avoid lot of calls
        val pageRelativeBounds = part.pageRelativeBounds
        val renderedBitmap = part.renderedBitmap
        val pdfFile = this.pdfFile ?: return

        if (renderedBitmap == null || renderedBitmap.isRecycled) {
            return
        }

        // Move to the target page
        var localTransactionX = 0f
        var localTransactionY = 0f
        var size = pdfFile.getPageSize(part.page)

        if (swipeVertical) {
            localTransactionY = pdfFile.getPageOffset(part.page, zoom)
            localTranslationX = toCurrentScale(pdfFile.maxPageWidth - size.width) / 2
        }

    }

    fun toRealScale(size: Float): Float {
        return size / zoom
    }

    fun toCurrentScale(size: Float): Float {
        return size * zoom
    }

    fun isZooming(): Boolean {
        return zoom != minZoom
    }

    fun jumpTo(page: Int) {
        jumpTo(page, false)
    }

    /**
     * Go to the given page.
     *
     * @param page Page index.
     */
    fun jumpTo(page: Int, withAnimation: Boolean) {

    }

    private enum class State {
        DEFAULT, LOADED, SHOWN, ERROR
    }
}