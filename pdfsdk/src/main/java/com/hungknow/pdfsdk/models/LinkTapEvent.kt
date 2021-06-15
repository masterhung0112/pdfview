package com.hungknow.pdfsdk.models

import android.graphics.RectF
import com.hungknow.pdfsdk.PdfDocument

data class LinkTapEvent(val originalX: Float, val originalY: Float, val documentX: Float, val documentY: Float, val mappedLinkRect: RectF, val link: PdfDocument.Link) {
}