package com.hungknow.pdfsdk

import androidx.test.platform.app.InstrumentationRegistry
import java.io.*

class FileUtils {
    companion object {
        fun getFileFromPath(obj: Any, assetName: String): File {
            val context = InstrumentationRegistry.getInstrumentation().context
            val outFile = File(context.getCacheDir(), assetName + "-pdfview.pdf")
            if (assetName.contains("/")) {
                outFile.parentFile.mkdirs()
            }
            copy(
                context.getAssets().open(assetName),
                outFile
            )
            return outFile
        }

        @Throws(IOException::class)
        fun copy(inputStream: InputStream?, output: File?) {
            var outputStream: OutputStream? = null
            try {
                outputStream = FileOutputStream(output)
                var read = 0
                val bytes = ByteArray(1024)
                while (inputStream!!.read(bytes).also { read = it } != -1) {
                    outputStream.write(bytes, 0, read)
                }
            } finally {
                try {
                    inputStream?.close()
                } finally {
                    outputStream?.close()
                }
            }
        }
    }
}