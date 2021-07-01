package com.hungknow.pdfsdk

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfiumSDKTest {

    @Test
    fun NativeOpenDocumentOk() {
        val f = FileUtils.getFileFromPath(this, "sample.pdf")
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)

        var documentFd = PdfiumSDK(72).nativeOpenDocument(pfd.fd, "password")
        Assert.assertNotEquals(-1, documentFd)
    }
}