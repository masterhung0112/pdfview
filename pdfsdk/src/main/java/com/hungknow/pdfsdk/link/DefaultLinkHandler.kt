package com.hungknow.pdfsdk.link

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.hungknow.pdfsdk.PdfView
import com.hungknow.pdfsdk.models.LinkTapEvent

class DefaultLinkHandler(val pdfView: PdfView): LinkHandler {
    override fun handleLinkEvent(event: LinkTapEvent) {
        val uri: String = event.link.uri
        val page: Int = event.link.destPageIdx
        if (uri != null && !uri.isEmpty()) {
            handleUri(uri)
        } else if (page != null) {
            handlePage(page)
        }
    }

    private fun handleUri(uri: String) {
        val parsedUri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        val context = pdfView.context
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.w(TAG, "No activity found for URI: $uri")
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }

    companion object {
        private val TAG = DefaultLinkHandler::class.java.simpleName
    }
}
