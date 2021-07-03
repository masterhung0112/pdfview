package com.hungknow.pdfsdk.scroll

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hungknow.pdfsdk.PdfView
import com.hungknow.pdfsdk.R
import com.hungknow.pdfsdk.utils.Utils
import java.lang.String


class DefaultScrollHandle(context: Context, var inverted: Boolean): RelativeLayout(context), ScrollHandle {
    val customHandler = Handler()
    val hidePageScrollerRunnable = Runnable {
        hide()
    }
    lateinit var pdfView: PdfView
    lateinit var textView: TextView
//    var context: Context
//    var inverted: Boolean
    var currentPos: Float = 0f
    var relativeHandlerMiddle = 0f

    constructor(context: Context): this(context, false) {

    }

    init {
//        this.context = context
//        this.inverted = inverted
        textView = TextView(context)
        setVisibility(INVISIBLE)
        textView.setTextColor(Color.BLACK)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TEXT_SIZE.toFloat())
    }

    override fun setScroll(position: Float) {
        if (!shown()) {
            show()
        } else {
            customHandler.removeCallbacks(hidePageScrollerRunnable)
        }
        if (pdfView != null) {
            setPosition((if (pdfView.swipeVertical) pdfView.getHeight() else pdfView.getWidth()) * position)
        }
    }

    fun setPosition(posAttr: Float) {
        var pos = posAttr
        if (java.lang.Float.isInfinite(pos) || java.lang.Float.isNaN(pos)) {
            return
        }
        val pdfViewSize: Float
        pdfViewSize = if (pdfView.swipeVertical) {
            pdfView.height.toFloat()
        } else {
            pdfView.width.toFloat()
        }
        pos -= relativeHandlerMiddle

        val displayMetrics = context.resources.displayMetrics
        if (pos < 0) {
            pos = 0f
        } else if (pos > pdfViewSize - Utils.getDP(displayMetrics, HANDLE_SHORT)) {
            pos = pdfViewSize - Utils.getDP(displayMetrics, HANDLE_SHORT)
        }

        if (pdfView.swipeVertical) {
            y = pos
        } else {
            x = pos
        }

        calculateMiddle()
        invalidate()
    }

    private fun calculateMiddle() {
        val pos: Float
        val viewSize: Float
        val pdfViewSize: Float
        if (pdfView.swipeVertical) {
            pos = y
            viewSize = height.toFloat()
            pdfViewSize = pdfView.height.toFloat()
        } else {
            pos = x
            viewSize = width.toFloat()
            pdfViewSize = pdfView.width.toFloat()
        }
        relativeHandlerMiddle = (pos + relativeHandlerMiddle) / pdfViewSize * viewSize
    }

    override fun setupLayout(pdfView: PdfView) {
        var align: Int
        var width: Int
        var height: Int
        var background: Drawable
        // determine handler position, default is right (when scrolling vertically) or bottom (when scrolling horizontally)
        if (pdfView.swipeVertical) {
            width = HANDLE_LONG
            height = HANDLE_SHORT;
            if (inverted) { // left
                align = ALIGN_PARENT_LEFT;
                background =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_left)!!;
            } else { // right
                align = ALIGN_PARENT_RIGHT;
                background =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_right)!!;
            }
        } else {
            width = HANDLE_SHORT;
            height = HANDLE_LONG;
            if (inverted) { // top
                align = ALIGN_PARENT_TOP;
                background =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_top)!!;
            } else { // bottom
                align = ALIGN_PARENT_BOTTOM;
                background =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_bottom)!!;
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            setBackgroundDrawable(background)
        } else {
            setBackground(background)
        }

        val displayMetric = context.resources.displayMetrics
        val lp = LayoutParams(Utils.getDP(displayMetric, width), Utils.getDP(displayMetric, height))
        lp.setMargins(0, 0, 0, 0)

        val tvlp =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tvlp.addRule(CENTER_IN_PARENT, TRUE)

        addView(textView, tvlp)

        lp.addRule(align)
        pdfView.addView(this, lp)

        this.pdfView = pdfView
    }

    override fun destroyLayout() {
        pdfView.removeView(this)
    }

    override fun setPageNum(pageNum: Int) {
        val text = String.valueOf(pageNum)
        if (textView.text != text) {
            textView.text = text
        }
    }

    override fun shown(): Boolean {
        return getVisibility() == VISIBLE
    }

    override fun show() {
        setVisibility(VISIBLE)
    }

    override fun hide() {
        setVisibility(INVISIBLE)
    }

    override fun hideDelayed() {
        customHandler.postDelayed(hidePageScrollerRunnable, 1000)
    }

    private fun isPDFViewReady(): Boolean {
        return pdfView != null && pdfView.getPageCount() > 0 && !pdfView.documentFitsView()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady()) {
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pdfView.stopFling()
                customHandler.removeCallbacks(hidePageScrollerRunnable)
                currentPos = if (pdfView.swipeVertical) {
                    event.rawY - y
                } else {
                    event.rawX - x
                }
                if (pdfView.swipeVertical) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pdfView.swipeVertical) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView.setPositionOffset(relativeHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hideDelayed()
                pdfView.performPageSnap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        val HANDLE_LONG = 65
        val HANDLE_SHORT = 40
        val DEFAULT_TEXT_SIZE = 16
    }
}