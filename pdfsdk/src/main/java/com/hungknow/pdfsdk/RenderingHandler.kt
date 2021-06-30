package com.hungknow.pdfsdk

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.hungknow.pdfsdk.exceptions.PageRenderingException
import com.hungknow.pdfsdk.models.PagePart
import java.lang.IllegalArgumentException

/**
 * A {@link Handler} that will process incoming {@link RenderingTask} messages
 * and alert {@link PDFView#onBitmapRendered(PagePart)} when the portion of the
 * PDF is ready to render.
 */
class RenderingHandler(looper: Looper, val pdfView: PdfView): Handler(looper) {

    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false

    companion object {
        val MSG_RENDER_TASK = 1
        val TAG = RenderingHandler::class.simpleName
    }

    fun stop() {
        running = false
    }

    fun start() {
        running = true
    }

    override fun handleMessage(msg: Message) {
        val task = msg.obj as RenderingTask
        try {
            val part = proceed(task)
            if (part != null) {
                if (running) {

                } else {
//                    part.rend
                }
            }
        } catch (e: PageRenderingException) {

        }
    }

    private fun proceed(renderingTask: RenderingTask): PagePart? {
        val pdfFile = pdfView.pdfFile ?: return null
        pdfFile.openPage(renderingTask.page)

        val w = Math.round(renderingTask.width)
        val h = Math.round(renderingTask.height)

        if (w == 0 || h == 0 || pdfFile.pageHasError(renderingTask.page)) {
            return null
        }

        var render: Bitmap
        try {
            render = Bitmap.createBitmap(w, h, if (renderingTask.bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "cannot create bitmap", e)
            return null
        }
        calculateBounds(w, h, renderingTask.bounds)

        pdfFile.renderPageBitmap(render, renderingTask.page, roundedRenderBounds, renderingTask.annotationRendering)

        return PagePart(renderingTask.page, render, renderingTask.bounds, renderingTask.thumbnail, renderingTask.cacheOrder)
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF) {
        renderMatrix.reset()
        renderMatrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height)
        renderMatrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height())

        renderBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        renderMatrix.mapRect(renderBounds)
        renderBounds.round(roundedRenderBounds)
    }

    private data class RenderingTask(val width: Float, val height: Float, val bounds: RectF, val page: Int, val thumbnail: Boolean, val cacheOrder: Int, val bestQuality: Boolean, val annotationRendering: Boolean) {

    }
}