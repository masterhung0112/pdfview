package com.hungknow.pdfsdk

/**
 * This manager is used by the PDFView to launch animations.
 * It uses the ValueAnimator appeared in API 11 to start
 * an animation, and call moveTo() on the PDFView as a result
 * of each animation update.
 */
class AnimationManager(val pdfView: PdfView) {
    fun stopAll() {

    }
}