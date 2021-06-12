package com.hungknow.pdfsdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumSDKTest {

    @Test
    fun NativeOpenDocumentOk() {
        val documentFd = PdfiumSDK().nativeOpenDocument(-1, "password")
        Assert.assertNotEquals(-1, documentFd)
    }
}