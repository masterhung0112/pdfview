package com.hungknow.pdfsdk

import android.os.Handler
import android.os.Looper

/**
 * A {@link Handler} that will process incoming {@link RenderingTask} messages
 * and alert {@link PDFView#onBitmapRendered(PagePart)} when the portion of the
 * PDF is ready to render.
 */
class RenderingHandler(looper: Looper, val pdfDocument: PdfDocument): Handler(looper) {

    companion object {
        val MSG_RENDER_TASK = 1
    }
}