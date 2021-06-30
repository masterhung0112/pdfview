package com.hungknow.pdfsdk

import android.os.AsyncTask
import com.hungknow.pdfsdk.models.Size
import com.hungknow.pdfsdk.source.DocumentSource
import java.lang.ref.WeakReference

class DecodingAsyncTask(val docSource: DocumentSource, val password: String, val userPages: List<Int>?, pdfView: PdfView, val pdfiumSDK: PdfiumSDK):
    AsyncTask<Void, Void, Throwable>() {
    var pdfViewReference: WeakReference<PdfView> = WeakReference(pdfView)
    var cancelled = false
    var pdfFile: PdfFile? = null

    override fun doInBackground(vararg params: Void?): Throwable? {
        return try {
            val pdfView = pdfViewReference.get()
            if (pdfView != null) {
                val pdfDocument =
                    docSource.createDocument(pdfView.context, pdfiumSDK, password)
                pdfFile = PdfFile(
                    pdfiumSDK,
                    pdfDocument,
                    pdfView.pageFitPolicy,
                    getViewSize(pdfView),
                    userPages,
                    pdfView.swipeVertical,
                    pdfView.spacing,
                    pdfView.autoSpacing,
                    pdfView.fitEachPage
                )
                null
            } else {
                NullPointerException("pdfView == null")
            }
        } catch (t: Exception) {
            t
        }
    }

    private fun getViewSize(pdfView: PdfView): Size {
        return Size(pdfView.getWidth(), pdfView.getHeight())
    }

    override fun onCancelled() {
        cancelled = true
    }

    override fun onPostExecute(result: Throwable?) {
        val pdfView = pdfViewReference.get()
        pdfView?.let {pdfView ->
            if (result != null) {
                pdfView.loadError(result)
                return
            }

            if (!cancelled) {
                pdfFile.let {
                    if (it != null) {
                        pdfView.loadComplete(it)
                    } else {
                        pdfView.loadError(Exception("pdfFile not created"))
                    }
                }
            }
        }
    }
}