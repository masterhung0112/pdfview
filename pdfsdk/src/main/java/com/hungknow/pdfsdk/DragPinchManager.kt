package com.hungknow.pdfsdk

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector


class DragPinchManager(pdfView: PdfView, animationManager: AnimationManager): GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener {
    private val gestureDetector = GestureDetector(pdfView.context, this)
    private val scaleGestureDetector = ScaleGestureDetector(pdfView.context, this)

    private var scrolling = false
    private var scaling = false
    private var enabled = false

    fun disableLongpress() {
        gestureDetector.setIsLongpressEnabled(false)
    }

    override fun onDown(e: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onShowPress(e: MotionEvent?) {
        TODO("Not yet implemented")
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onLongPress(e: MotionEvent?) {
        TODO("Not yet implemented")
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        TODO("Not yet implemented")
    }

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }
}