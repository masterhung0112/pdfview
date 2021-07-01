package com.hungknow.pdfsdk

import android.content.Context
import android.graphics.*
import android.os.AsyncTask
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.hungknow.pdfsdk.link.DefaultLinkHandler
import com.hungknow.pdfsdk.link.LinkHandler
import com.hungknow.pdfsdk.listeners.*
import com.hungknow.pdfsdk.models.PagePart
import com.hungknow.pdfsdk.scroll.ScrollHandle
import com.hungknow.pdfsdk.source.AssetSource
import com.hungknow.pdfsdk.source.DocumentSource
import com.hungknow.pdfsdk.utils.Constants.Companion.DEBUG_MODE
import com.hungknow.pdfsdk.utils.FitPolicy
import com.hungknow.pdfsdk.utils.MathUtils
import com.hungknow.pdfsdk.utils.SnapEdge
import com.hungknow.pdfsdk.utils.Utils


class PdfView : RelativeLayout {
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
    var callbacks = Callbacks()

    var scrollHandle: ScrollHandle? = null
    private var isScrollHandleInit = false

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    internal enum class ScrollDir {
        NONE, START, END
    }

    private var scrollDir = ScrollDir.NONE

    /** Rendered parts go to the cache manager  */
    val cacheManager = CacheManager()

    /** Drag manager manage all touch events */
    lateinit var dragPinchManager: DragPinchManager

    /** Animation manager manage all offset and zoom animation */
    var animationManager = AnimationManager(this)

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

    // True if PdfView has been recycled
    private var recycled = true

    /** Current state of the view  */
    private var state = State.DEFAULT

    /** Async task used during the loading phase to decode a PDF document  */
    private var decodingAsyncTask: DecodingAsyncTask? = null

    private lateinit var pdfDocument: PdfDocument

    /** Paint object for drawing  */
    private var paint = Paint()

    /** Paint object for drawing debug stuff  */
    private var debugPaint = Paint()

    /**
     * True if bitmap should use ARGB_8888 format and take more memory
     * False if bitmap should be compressed by using RGB_565 format and take less memory
     */
    private var bestQuality = false

    /**
     * True if annotations should be rendered
     * False otherwise
     */
    private var annotationRendering = false

    /**
     * True if the view should render during scaling<br></br>
     * Can not be forced on older API versions (< Build.VERSION_CODES.KITKAT) as the GestureDetector does
     * not detect scrolling while scaling.<br></br>
     * False otherwise
     */
    private var renderDuringScale = false

    /** Antialiasing and bitmap filtering  */
    private var enableAntialiasing = true
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var nightMode = false

    /** Spacing between pages, in px  */
    var spacing = 0
        set(value) {
            Utils.getDP(context.resources.displayMetrics, value)
        }

    /** Add dynamic spacing to fit each page separately on the screen.  */
    var autoSpacing = false

    /** Fling a single page at a time  */
    private var pageFling = true

    /** Pages numbers used when calling onDrawAll  */
    private val onDrawPagesNums = mutableListOf<Int>()

    /** Holds info whether view has been added to layout and has width and height  */
    private var hasSize = false

    /** Holds last used Configurator that should be loaded when view has size  */
    private var waitingDocumentConfigurator: Configurator? = null

    /** Policy for fitting pages to screen  */
    var pageFitPolicy = FitPolicy.WIDTH
        private set

    var fitEachPage = false

    private var defaultPage = 0

    private var enableSwipe = true

    private var doubletapEnabled = true

    private var pageSnap = true

    constructor(context: Context, set: AttributeSet) : super(context) {
        if (isInEditMode) {
            return
        }

        pagesLoader = PagesLoader(this)
        pdfiumSdk = PdfiumSDK(context.resources.displayMetrics.densityDpi)
        dragPinchManager = DragPinchManager(this, animationManager)

        debugPaint.style = Paint.Style.STROKE
        setWillNotDraw(false)
    }

    private fun load(docSource: DocumentSource, password: String) {
        load(docSource, password, null)
    }

    private fun load(docSource: DocumentSource, password: String, userPages: List<Int>?) {
        check(recycled) { "Don't call load on a PDF View without recycling it first." }
        recycled = false
        // Start decoding document
        decodingAsyncTask = DecodingAsyncTask(docSource, password, userPages, this, pdfiumSdk)
        decodingAsyncTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun showPage(pageNb: Int) {
        if (recycled) {
            return
        }

        // Check the page number and makes the difference between UserPages and DocumentPages
        var validPageNb = pdfDocument.determineValidPageNumberFrom(pageNb)
        currentPage = validPageNb

        loadPages()

        if (scrollHandle != null && !documentFitsView()) {
            scrollHandle!!.setPageNum(currentPage + 1)
        }

        callbacks.callOnPageChange(currentPage, pdfFile!!.pagesCount)
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

    /**
     * Animate to the nearest snapping position for the current SnapPolicy
     */
    fun performPageSnap() {
        if (!pageSnap || pdfFile == null || pdfFile!!.pagesCount == 0) {
            return
        }
        val centerPage: Int = findFocusPage(currentXOffset, currentYOffset)
        val edge: SnapEdge = findSnapEdge(centerPage)
        if (edge === SnapEdge.NONE) {
            return
        }
        val offset: Float = snapOffsetForPage(centerPage, edge)
        if (swipeVertical) {
            animationManager.startYAnimation(currentYOffset, -offset)
        } else {
            animationManager.startXAnimation(currentXOffset, -offset)
        }
    }

    /**
     * Find the edge to snap to when showing the specified page
     */
    fun findSnapEdge(page: Int): SnapEdge {
        if (!pageSnap || page < 0) {
            return SnapEdge.NONE
        }
        val currentOffset = if (swipeVertical) currentYOffset else currentXOffset
        val offset = -pdfFile!!.getPageOffset(page, zoom)
        val length = if (swipeVertical) height else width
        val pageLength: Float = pdfFile!!.getPageLength(page, zoom)
        return if (length >= pageLength) {
            SnapEdge.CENTER
        } else if (currentOffset >= offset) {
            SnapEdge.START
        } else if (offset - pageLength > currentOffset - length) {
            SnapEdge.END
        } else {
            SnapEdge.NONE
        }
    }

    /**
     * Get the offset to move to in order to snap to the page
     */
    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        var offset = pdfFile!!.getPageOffset(pageIndex, zoom)
        val length = if (swipeVertical) height.toFloat() else width.toFloat()
        val pageLength: Float = pdfFile!!.getPageLength(pageIndex, zoom)
        if (edge === SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f
        } else if (edge === SnapEdge.END) {
            offset = offset - length + pageLength
        }
        return offset
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val currOffset = if (swipeVertical) yOffset else xOffset
        val length = if (swipeVertical) height.toFloat() else width.toFloat()
        // make sure first and last page can be found
        if (currOffset > -1) {
            return 0
        } else if (currOffset < -pdfFile!!.getDocLen(zoom) + length + 1) {
            return pdfFile!!.pagesCount - 1
        }
        // else find page in center
        val center = currOffset - length / 2f
        return pdfFile!!.getPageAtOffset(-center, zoom)
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see .moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(currentXOffset + dx, currentYOffset + dy)
    }

    /**
     * Change the zoom level
     */
    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     *
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val dzoom = zoom / this.zoom
        zoomTo(zoom)
        var baseX = currentXOffset * dzoom
        var baseY = currentYOffset * dzoom
        baseX += pivot.x - pivot.x * dzoom
        baseY += pivot.y - pivot.y * dzoom
        moveTo(baseX, baseY)
    }

    /**
     * @see .zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(dzoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom * dzoom, pivot)
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
            if (callbacks.onDrawAll != null
                && !onDrawPagesNums.contains(part.page)
            ) {
                onDrawPagesNums.add(part.page)
            }
        }

        for (page in onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.onDrawAll)
        }
        onDrawPagesNums.clear()

        drawWithListener(canvas, currentPage, callbacks.onDraw)

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        if (listener != null) {
            var translateX = 0f
            var translateY = 0f
            if (swipeVertical) {
                translateX = 0f
                translateY = pdfFile!!.getPageOffset(page, zoom)
            } else {
                translateX = pdfFile!!.getPageOffset(page, zoom)
                translateY = 0f
            }

            canvas.translate(translateX, translateY)
            val size = pdfFile!!.getPageSize(page)
            listener.onLayerDrawn(
                canvas,
                toCurrentScale(size.width),
                toCurrentScale(size.height),
                page
            )
            canvas.translate(-translateX, -translateY)
        }
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
        var localTranslationX = 0f
        var localTranslationY = 0f
        var size = pdfFile.getPageSize(part.page)

        if (swipeVertical) {
            localTranslationY = pdfFile.getPageOffset(part.page, zoom)
            localTranslationX = toCurrentScale(pdfFile.maxPageWidth - size.width) / 2
        } else {
            localTranslationX = pdfFile.getPageOffset(part.page, zoom)
            localTranslationY = toCurrentScale(pdfFile.maxPageHeight - size.height) / 2
        }
        canvas.translate(localTranslationX, localTranslationY)

        var srcRect = Rect(0, 0, renderedBitmap.width, renderedBitmap.height)

        val offsetX = toCurrentScale(pageRelativeBounds.left * size.width)
        val offsetY = toCurrentScale(pageRelativeBounds.top * size.height)
        val width = toCurrentScale(pageRelativeBounds.width() * size.width)
        val height = toCurrentScale(pageRelativeBounds.height() * size.height)

        // If we use float values for this rectangle, there will be
        // a possible gap between page parts, especially when
        // the zoom level is high.
        val dstRect = Rect(
            offsetX.toInt(),
            offsetY.toInt(),
            (offsetX + width).toInt(),
            (offsetY + height).toInt()
        )

        canvas.drawBitmap(renderedBitmap, srcRect, dstRect, paint)

        if (DEBUG_MODE) {
            debugPaint.color = if (part.page % 2 === 0) Color.RED else Color.BLUE
            canvas.drawRect(dstRect, debugPaint)
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY)
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

    fun enableDoubletap(enableDoubletap: Boolean) {
        this.doubletapEnabled = enableDoubletap
    }

    fun isDoubletapEnabled(): Boolean {
        return doubletapEnabled
    }

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     *
     * @return true if whole document can displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val len = pdfFile!!.getDocLen(1f)
        return if (swipeVertical) {
            len < height
        } else {
            len < width
        }
    }

    fun recycle() {
        waitingDocumentConfigurator = null
        animationManager.stopAll()
        dragPinchManager.disable()

        // Stop tasks
        renderingHandler?.let {
            it.stop()
            it.removeMessages(RenderingHandler.MSG_RENDER_TASK)
        }

        decodingAsyncTask?.let {
            it.cancel(true)
        }

        // Clear caches
        cacheManager.recycle()

        scrollHandle?.let {
            if (isScrollHandleInit) {
                it.destroyLayout()
            }
        }

        pdfFile?.let {
            it.dispose()
        }
        pdfFile = null

        renderingHandler = null
        scrollHandle = null
        isScrollHandleInit = false
        currentXOffset = 0f
        currentYOffset = 0f
        zoom = 1f
        recycled = true
        callbacks = Callbacks()
        state = State.DEFAULT
    }

    fun loadError(t: Throwable) {
        state = State.ERROR

        // store reference, because callbacks will be cleared in recycle() method
        var onErrorListener = callbacks.onError
        recycle()
        invalidate()
        if (onErrorListener != null) {
            onErrorListener.onError(t)
        } else {
            Log.e("PdfView", "load pdf error", t)
        }
    }

    /**
     * Called when a rendering task is over and
     * a PagePart has been freshly created.
     *
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // when it is first rendered part
        if (state === State.LOADED) {
            state = State.SHOWN
            callbacks.callOnRender(pdfFile!!.pagesCount)
        }
        if (part.thumbnail) {
            cacheManager.cacheThumbnail(part)
        } else {
            cacheManager.cachePart(part)
        }
        invalidate()
    }

    fun moveTo(offsetX: Float, offsetY: Float) {
        moveTo(offsetX, offsetY, true)
    }

    /**
     * Move to the given X and Y offsets, but check them ahead of time
     * to be sure not to go outside the the big strip.
     *
     * @param offsetX    The big strip X offset to use as the left border of the screen.
     * @param offsetY    The big strip Y offset to use as the right border of the screen.
     * @param moveHandle whether to move scroll handle or not
     */
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean) {
        var offsetX = offsetX
        var offsetY = offsetY
        if (swipeVertical) {
            // Check X offset
            val scaledPageWidth = toCurrentScale(pdfFile!!.maxPageWidth)
            if (scaledPageWidth < width) {
                offsetX = width / 2 - scaledPageWidth / 2
            } else {
                if (offsetX > 0) {
                    offsetX = 0f
                } else if (offsetX + scaledPageWidth < width) {
                    offsetX = width - scaledPageWidth
                }
            }

            // Check Y offset
            val contentHeight = pdfFile!!.getDocLen(zoom)
            if (contentHeight < height) { // whole document height visible on screen
                offsetY = (height - contentHeight) / 2
            } else {
                if (offsetY > 0) { // top visible
                    offsetY = 0f
                } else if (offsetY + contentHeight < height) { // bottom visible
                    offsetY = -contentHeight + height
                }
            }
            scrollDir = if (offsetY < currentYOffset) {
                ScrollDir.END
            } else if (offsetY > currentYOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        } else {
            // Check Y offset
            val scaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
            if (scaledPageHeight < height) {
                offsetY = height / 2 - scaledPageHeight / 2
            } else {
                if (offsetY > 0) {
                    offsetY = 0f
                } else if (offsetY + scaledPageHeight < height) {
                    offsetY = height - scaledPageHeight
                }
            }

            // Check X offset
            val contentWidth = pdfFile!!.getDocLen(zoom)
            if (contentWidth < width) { // whole document width visible on screen
                offsetX = (width - contentWidth) / 2
            } else {
                if (offsetX > 0) { // left visible
                    offsetX = 0f
                } else if (offsetX + contentWidth < width) { // right visible
                    offsetX = -contentWidth + width
                }
            }
            scrollDir = if (offsetX < currentXOffset) {
                ScrollDir.END
            } else if (offsetX > currentXOffset) {
                ScrollDir.START
            } else {
                ScrollDir.NONE
            }
        }
        currentXOffset = offsetX
        currentYOffset = offsetY
        val positionOffset = getPositionOffset()
        if (moveHandle && scrollHandle != null && !documentFitsView()) {
            scrollHandle!!.setScroll(positionOffset)
        }
        callbacks.callOnPageScroll(currentPage, positionOffset)
        invalidate()
    }

    fun loadPageByOffset() {
        if (0 == pdfFile!!.pagesCount) {
            return
        }
        val offset: Float
        val screenCenter: Float
        if (swipeVertical) {
            offset = currentYOffset
            screenCenter = height.toFloat() / 2
        } else {
            offset = currentXOffset
            screenCenter = width.toFloat() / 2
        }
        val page = pdfFile!!.getPageAtOffset(-(offset - screenCenter), zoom)
        if (page >= 0 && page <= pdfFile!!.pagesCount - 1 && page != currentPage) {
            showPage(page)
        } else {
            loadPages()
        }
    }

    fun loadComplete(pdfFile: PdfFile) {
        state = State.LOADED
        this.pdfFile = pdfFile

        if (!renderingHandlerThread.isAlive) {
            renderingHandlerThread.start()
        }

        renderingHandler = RenderingHandler(renderingHandlerThread.looper, this)
        renderingHandler!!.start()

        scrollHandle?.let {
            it.setupLayout(this)
            isScrollHandleInit = true
        }

        dragPinchManager.enable()

        callbacks.callOnLoadComplete(pdfFile.pagesCount)

        jumpTo(defaultPage, false)
    }

    /**
     * Get current position as ratio of document length to visible area.
     * 0 means that document start is visible, 1 that document end is visible
     *
     * @return offset between 0 and 1
     */
    fun getPositionOffset(): Float {
        val offset: Float
        offset = if (swipeVertical) {
            -currentYOffset / (pdfFile!!.getDocLen(zoom) - height)
        } else {
            -currentXOffset / (pdfFile!!.getDocLen(zoom) - width)
        }
        return MathUtils.limit(offset, 0f, 1f)
    }

    /**
     * @param progress   must be between 0 and 1
     * @param moveHandle whether to move scroll handle
     * @see PDFView.getPositionOffset
     */
    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        if (swipeVertical) {
            moveTo(currentXOffset, (-pdfFile!!.getDocLen(zoom) + height) * progress, moveHandle)
        } else {
            moveTo((-pdfFile!!.getDocLen(zoom) + width) * progress, currentYOffset, moveHandle)
        }
        loadPageByOffset()
    }

    fun setPositionOffset(progress: Float) {
        setPositionOffset(progress, true)
    }

    fun stopFling() {
        animationManager.stopFling()
    }

    fun getPageCount(): Int {
        return if (pdfFile == null) {
            0
        } else pdfFile!!.pagesCount
    }

    /** Use an asset file as the pdf source  */
    fun fromAsset(assetName: String): Configurator {
        return Configurator(AssetSource(assetName))
    }

    /** Use a file as the pdf source  */
//    fun fromFile(file: File): Configurator {
//        return Configurator(FileSource(file))
//    }

    /** Use URI as the pdf source, for use with content providers  */
//    fun fromUri(uri: Uri): Configurator {
//        return Configurator(UriSource(uri))
//    }

    /** Use bytearray as the pdf source, documents is not saved  */
//    fun fromBytes(bytes: ByteArray): Configurator {
//        return Configurator(ByteArraySource(bytes))
//    }

    /** Use stream as the pdf source. Stream will be written to bytearray, because native code does not support Java Streams  */
//    fun fromStream(stream: InputStream): Configurator {
//        return Configurator(InputStreamSource(stream))
//    }

    /** Use custom source as pdf source  */
    fun fromSource(docSource: DocumentSource): Configurator {
        return Configurator(docSource!!)
    }

    private enum class State {
        DEFAULT, LOADED, SHOWN, ERROR
    }

    inner class Configurator constructor(val documentSource: DocumentSource) {
        private var pageNumbers: List<Int>? = null
        private var enableSwipe = true
        private var enableDoubletap = true
        private var onDrawListener: OnDrawListener? = null
        private var onDrawAll: OnDrawListener? = null
        private var onLoadCompleteListener: OnLoadCompleteListener? = null
        private var onErrorListener: OnErrorListener? = null
        private var onPageChangeListener: OnPageChangeListener? = null
        private var onPageScrollListener: OnPageScrollListener? = null
        private var onRenderListener: OnRenderListener? = null
        private var onTapListener: OnTapListener? = null
        private var onLongPressListener: OnLongPressListener? = null
        private var onPageErrorListener: OnPageErrorListener? = null
        private var linkHandler: LinkHandler = DefaultLinkHandler(this@PdfView)
        private var defaultPage = 0
        private var swipeHorizontal = false
        private var annotationRendering = false
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var antialiasing = true
        private var spacing = 0f
        private var autoSpacing = false
        private var pageFitPolicy = FitPolicy.WIDTH
        private var fitEachPage = false
        private var pageFling = false
        private var pageSnap = false
        private var nightMode = false
        fun pages(vararg pageNumbers: Int): Configurator {
            this.pageNumbers = pageNumbers.asList()
            return this
        }

        fun enableSwipe(enableSwipe: Boolean): Configurator {
            this.enableSwipe = enableSwipe
            return this
        }

        fun enableDoubletap(enableDoubletap: Boolean): Configurator {
            this.enableDoubletap = enableDoubletap
            return this
        }

        fun enableAnnotationRendering(annotationRendering: Boolean): Configurator {
            this.annotationRendering = annotationRendering
            return this
        }

        fun onDraw(onDrawListener: OnDrawListener?): Configurator {
            this.onDrawListener = onDrawListener
            return this
        }

        fun onDrawAll(onDrawAll: OnDrawListener?): Configurator {
            this.onDrawAll = onDrawAll
            return this
        }

        fun onLoad(onLoadCompleteListener: OnLoadCompleteListener?): Configurator {
            this.onLoadCompleteListener = onLoadCompleteListener
            return this
        }

        fun onPageScroll(onPageScrollListener: OnPageScrollListener?): Configurator {
            this.onPageScrollListener = onPageScrollListener
            return this
        }

        fun onError(onErrorListener: OnErrorListener?): Configurator {
            this.onErrorListener = onErrorListener
            return this
        }

        fun onPageError(onPageErrorListener: OnPageErrorListener?): Configurator {
            this.onPageErrorListener = onPageErrorListener
            return this
        }

        fun onPageChange(onPageChangeListener: OnPageChangeListener?): Configurator {
            this.onPageChangeListener = onPageChangeListener
            return this
        }

        fun onRender(onRenderListener: OnRenderListener?): Configurator {
            this.onRenderListener = onRenderListener
            return this
        }

        fun onTap(onTapListener: OnTapListener?): Configurator {
            this.onTapListener = onTapListener
            return this
        }

        fun onLongPress(onLongPressListener: OnLongPressListener?): Configurator {
            this.onLongPressListener = onLongPressListener
            return this
        }

        fun linkHandler(linkHandler: LinkHandler): Configurator {
            this.linkHandler = linkHandler
            return this
        }

        fun defaultPage(defaultPage: Int): Configurator {
            this.defaultPage = defaultPage
            return this
        }

        fun swipeHorizontal(swipeHorizontal: Boolean): Configurator {
            this.swipeHorizontal = swipeHorizontal
            return this
        }

        fun password(password: String?): Configurator {
            this.password = password
            return this
        }

        fun scrollHandle(scrollHandle: ScrollHandle?): Configurator {
            this.scrollHandle = scrollHandle
            return this
        }

        fun enableAntialiasing(antialiasing: Boolean): Configurator {
            this.antialiasing = antialiasing
            return this
        }

        fun spacing(spacing: Float): Configurator {
            this.spacing = spacing
            return this
        }

        fun autoSpacing(autoSpacing: Boolean): Configurator {
            this.autoSpacing = autoSpacing
            return this
        }

        fun pageFitPolicy(pageFitPolicy: FitPolicy): Configurator {
            this.pageFitPolicy = pageFitPolicy
            return this
        }

        fun fitEachPage(fitEachPage: Boolean): Configurator {
            this.fitEachPage = fitEachPage
            return this
        }

        fun pageSnap(pageSnap: Boolean): Configurator {
            this.pageSnap = pageSnap
            return this
        }

        fun pageFling(pageFling: Boolean): Configurator {
            this.pageFling = pageFling
            return this
        }

        fun nightMode(nightMode: Boolean): Configurator {
            this.nightMode = nightMode
            return this
        }

        fun disableLongpress(): Configurator {
            this@PdfView.dragPinchManager.disableLongpress()
            return this
        }

        fun load() {
            if (!hasSize) {
                waitingDocumentConfigurator = this
                return
            }
            this@PdfView.recycle()
            this@PdfView.callbacks.onLoadComplete = onLoadCompleteListener
            this@PdfView.callbacks.onError = onErrorListener
            this@PdfView.callbacks.onDraw = onDrawListener
            this@PdfView.callbacks.onDrawAll = onDrawAll
            this@PdfView.callbacks.onPageChange = onPageChangeListener
            this@PdfView.callbacks.onPageScroll = onPageScrollListener
            this@PdfView.callbacks.onRender = onRenderListener
            this@PdfView.callbacks.onTap = onTapListener
            this@PdfView.callbacks.onLongPress = onLongPressListener
            this@PdfView.callbacks.onPageError = onPageErrorListener
            this@PdfView.callbacks.linkHandler = linkHandler
            this@PdfView.enableSwipe = enableSwipe
            this@PdfView.nightMode = nightMode
            this@PdfView.enableDoubletap(enableDoubletap)
            this@PdfView.defaultPage = defaultPage
            this@PdfView.swipeVertical = !swipeHorizontal
            this@PdfView.annotationRendering = annotationRendering
            this@PdfView.scrollHandle = scrollHandle
            this@PdfView.enableAntialiasing = antialiasing
            this@PdfView.spacing = spacing
            this@PdfView.autoSpacing = autoSpacing
            this@PdfView.pageFitPolicy = pageFitPolicy
            this@PdfView.fitEachPage = fitEachPage
            this@PdfView.pageSnap = pageSnap
            this@PdfView.pageFling = pageFling
            val usedPassword = this.password ?: ""

            pageNumbers.let {
                if (it != null) {
                    this@PdfView.load(documentSource, usedPassword, it)
                } else {
                    this@PdfView.load(documentSource, usedPassword)
                }
            }
        }
    }
}