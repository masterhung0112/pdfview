package com.hungknow.pdfview

import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import com.hungknow.pdfsdk.PdfView
import com.hungknow.pdfsdk.PdfiumSDK
import com.hungknow.pdfsdk.listeners.OnLoadCompleteListener
import com.hungknow.pdfsdk.listeners.OnPageChangeListener
import com.hungknow.pdfsdk.listeners.OnPageErrorListener
import com.hungknow.pdfsdk.scroll.DefaultScrollHandle
import com.hungknow.pdfsdk.utils.FitPolicy
import com.hungknow.pdfview.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment(), OnPageChangeListener, OnPageErrorListener,
    OnLoadCompleteListener {

    private var _binding: FragmentFirstBinding? = null
    private var pageNumber = 0
    private var pdfFileName = "Sample.pdf"

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        decodePDFPage(_binding!!.pdfView)

        return binding.root

    }


    fun decodePDFPage(pdfView: PdfView) {
//        val pdfFile = (activity!!.application as SamplesApplication).createNewSampleFile("Sample.pdf")
//        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
//        val sdk = PdfiumSDK(72)
//        val pdfDocument = sdk.newDocument(fileDescriptor, "")
//
//        Log.d("PDFSDK", "Page count: " + sdk.getPageCount(pdfDocument))
//
//        sdk.openPage(pdfDocument, 0)
//        val size = sdk.getPageSize(pdfDocument, 0)
//        Log.d("PDFSDK", "Page size: $size ${size.width.toFloat()/size.height}")
//
//        val width = Resources.getSystem().displayMetrics.widthPixels
//        val height = Resources.getSystem().displayMetrics.heightPixels
//        Log.d("PDFSDK", "Screen: $width $height ${width.toFloat()/height}")
//
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        sdk.renderPageBitmap(pdfDocument, bitmap, 0, 0, 0, width, height, true)
//        imageView.setImageBitmap(bitmap)
//        sdk.closeDocument(pdfDocument)
        pdfView.fromAsset("Sample.pdf")
            .defaultPage(pageNumber)
            .onPageChange(this)
            .enableAnnotationRendering(true)
            .onLoad(this)
            .scrollHandle(DefaultScrollHandle(requireActivity().applicationContext))
            .spacing(10) // in dp
            .onPageError(this)
            .pageFitPolicy(FitPolicy.BOTH)
            .load();
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        binding.buttonFirst.setOnClickListener {
//            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
        requireActivity().title = String.format("%s %s / %s", pdfFileName, page + 1, pageCount)
    }

    override fun onPageError(page: Int, t: Throwable) {
        Log.e(TAG, "Cannot load page " + page);
    }

    companion object {
        val TAG = FirstFragment::class.simpleName
    }

    override fun loadComplete(nbPages: Int) {

    }
}