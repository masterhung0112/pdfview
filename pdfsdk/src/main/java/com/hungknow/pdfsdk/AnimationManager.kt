package com.hungknow.pdfsdk

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.PointF
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller


/**
 * This manager is used by the PDFView to launch animations.
 * It uses the ValueAnimator appeared in API 11 to start
 * an animation, and call moveTo() on the PDFView as a result
 * of each animation update.
 */
class AnimationManager(val pdfView: PdfView) {
    private var animation: ValueAnimator? = null

    private var scroller: OverScroller? = null

    private var flinging = false

    private var pageFlinging = false

    init {
        scroller = OverScroller(pdfView.context)
    }

    fun startXAnimation(xFrom: Float, xTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(xFrom, xTo)
        val xAnimation = XAnimation()
        animation?.let {animation ->
            animation.setInterpolator(DecelerateInterpolator())
            animation.addUpdateListener(xAnimation)
            animation.addListener(xAnimation)
            animation.setDuration(400)
            animation.start()
        }

    }

    fun startYAnimation(yFrom: Float, yTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(yFrom, yTo)
        val yAnimation = YAnimation()
        animation?.let { animation ->
            animation.setInterpolator(DecelerateInterpolator())
            animation.addUpdateListener(yAnimation)
            animation.addListener(yAnimation)
            animation.setDuration(400)
            animation.start()
        }
    }

    fun startZoomAnimation(centerX: Float, centerY: Float, zoomFrom: Float, zoomTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(zoomFrom, zoomTo)
        animation?.let { animation ->
            animation.setInterpolator(DecelerateInterpolator())
            val zoomAnim = ZoomAnimation(centerX, centerY)
            animation.addUpdateListener(zoomAnim)
            animation.addListener(zoomAnim)
            animation.setDuration(400)
            animation.start()
        }
    }

    fun startFlingAnimation(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int
    ) {
        stopAll()
        flinging = true
        scroller!!.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
    }

    fun startPageFlingAnimation(targetOffset: Float) {
        if (pdfView.swipeVertical) {
            startYAnimation(pdfView.currentYOffset, targetOffset)
        } else {
            startXAnimation(pdfView.currentXOffset, targetOffset)
        }
        pageFlinging = true
    }

    fun computeFling() {
        if (scroller!!.computeScrollOffset()) {
            pdfView.moveTo(scroller!!.currX.toFloat(), scroller!!.currY.toFloat())
            pdfView.loadPageByOffset()
        } else if (flinging) { // fling finished
            flinging = false
            pdfView.loadPages()
            hideHandle()
            pdfView.performPageSnap()
        }
    }

    fun stopAll() {
        if (animation != null) {
            animation!!.cancel()
            animation = null
        }
        stopFling()
    }

    fun stopFling() {
        flinging = false
        scroller!!.forceFinished(true)
    }

    fun isFlinging(): Boolean {
        return flinging || pageFlinging
    }

    inner class XAnimation : AnimatorListenerAdapter(),
        AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            this@AnimationManager.pdfView.moveTo(offset, this@AnimationManager.pdfView.currentYOffset)
            this@AnimationManager.pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            this@AnimationManager.pdfView.loadPages()
            this@AnimationManager.pageFlinging = false
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            this@AnimationManager.pdfView.loadPages()
            this@AnimationManager.pageFlinging = false
            hideHandle()
        }
    }

    inner class YAnimation : AnimatorListenerAdapter(),
        AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            pdfView.moveTo(pdfView.currentXOffset, offset)
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            this@AnimationManager.pdfView.loadPages()
            this@AnimationManager.pageFlinging = false
            this@AnimationManager.hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            this@AnimationManager.pdfView.loadPages()
            pageFlinging = false
            hideHandle()
        }
    }

    inner class ZoomAnimation(private val centerX: Float, private val centerY: Float) :
        AnimatorUpdateListener, AnimatorListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val zoom = animation.animatedValue as Float
            pdfView.zoomCenteredTo(zoom, PointF(centerX, centerY))
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
            pdfView.performPageSnap()
            hideHandle()
        }

        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationStart(animation: Animator) {}
    }

    private fun hideHandle() {
        pdfView.scrollHandle?.let {
            it.hideDelayed()
        }
    }
}