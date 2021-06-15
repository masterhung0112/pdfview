package com.hungknow.pdfsdk.listeners

import android.view.MotionEvent

import android.view.ViewTreeObserver.OnDrawListener

import com.hungknow.pdfsdk.link.LinkHandler
import com.hungknow.pdfsdk.models.LinkTapEvent

class Callbacks {
    /**
     * Call back object to call when the PDF is loaded
     */
    var onLoadCompleteListener: OnLoadCompleteListener? = null

    /**
     * Call back object to call when document loading error occurs
     */
    var onErrorListener: OnErrorListener? = null

    /**
     * Call back object to call when the page load error occurs
     */
    var onPageErrorListener: OnPageErrorListener? = null

    /**
     * Call back object to call when the document is initially rendered
     */
    var onRenderListener: OnRenderListener? = null

    /**
     * Call back object to call when the page has changed
     */
    var onPageChangeListener: OnPageChangeListener? = null

    /**
     * Call back object to call when the page is scrolled
     */
    var onPageScrollListener: OnPageScrollListener? = null

    /**
     * Call back object to call when the above layer is to drawn
     */
    var onDrawListener: OnDrawListener? = null

    var onDrawAllListener: OnDrawListener? = null

    /**
     * Call back object to call when the user does a tap gesture
     */
    var onTapListener: OnTapListener? = null

    /**
     * Call back object to call when the user does a long tap gesture
     */
    var onLongPressListener: OnLongPressListener? = null

    /**
     * Call back object to call when clicking link
     */
    var linkHandler: LinkHandler? = null

    fun callOnLoadComplete(pagesCount: Int) {
        onLoadCompleteListener?.loadComplete(pagesCount)
    }


    fun setOnPageError(onPageErrorListener: OnPageErrorListener?) {
        this.onPageErrorListener = onPageErrorListener
    }

    fun callOnPageError(page: Int, error: Throwable): Boolean {
        if (onPageErrorListener != null) {
            onPageErrorListener!!.onPageError(page, error)
            return true
        }
        return false
    }

    fun callOnRender(pagesCount: Int) {
        onRenderListener?.onInitiallyRendered(pagesCount)
    }

    fun callOnPageChange(page: Int, pagesCount: Int) {
        onPageChangeListener?.onPageChanged(page, pagesCount)
    }

    fun callOnPageScroll(currentPage: Int, offset: Float) {
        onPageScrollListener?.onPageScrolled(currentPage, offset)
    }


    fun callOnTap(event: MotionEvent): Boolean {
        return onTapListener != null && onTapListener!!.onTap(event)
    }

    fun callOnLongPress(event: MotionEvent) {
        onLongPressListener?.onLongPress(event)
    }

    fun callLinkHandler(event: LinkTapEvent) {
        linkHandler?.handleLinkEvent(event)
    }
}