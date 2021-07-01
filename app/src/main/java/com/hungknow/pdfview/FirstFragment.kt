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
import com.hungknow.pdfsdk.PdfiumSDK
import com.hungknow.pdfview.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        decodePDFPage(binding.imageView)

        return binding.root

    }


    fun decodePDFPage(imageView: ImageView) {
        val pdfFile = (activity!!.application as SamplesApplication).createNewSampleFile("Sample.pdf")
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val sdk = PdfiumSDK(72)
        val pdfDocument = sdk.newDocument(fileDescriptor, "")

        Log.d("PDFSDK", "Page count: " + sdk.getPageCount(pdfDocument))

        sdk.openPage(pdfDocument, 0)
        val size = sdk.getPageSize(pdfDocument, 0)
        Log.d("PDFSDK", "Page size: $size")

        val width = Resources.getSystem().displayMetrics.widthPixels
        val height = Resources.getSystem().displayMetrics.heightPixels

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        sdk.renderPageBitmap(pdfDocument, bitmap, 0, 0, 0, width, height, true)
        imageView.setImageBitmap(bitmap)
        sdk.closeDocument(pdfDocument)
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
}