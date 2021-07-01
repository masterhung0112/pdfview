package com.hungknow.pdfview

import android.app.Application
import android.os.Environment
import java.io.File
import java.io.IOException

class SamplesApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        createSampleFile("Sample.pdf")
    }

    fun createSampleFile(fileName: String): File? {
        try {
            val inStream = assets.open(fileName)
            val target = getSampleFile(fileName)
            if (target.isFile) {
                target.delete()
            }
            inStream.copyTo(target.outputStream())
            return target
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    fun getSampleFile(fileName: String): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
    }

    fun createNewSampleFile(fileName: String): File? {
        val file = getSampleFile(fileName)

        if (!file.isFile()) {
            return createSampleFile(fileName)
        }

        return file
    }
}