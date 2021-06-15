package com.hungknow.pdfsdk

import android.graphics.RectF
import android.os.ParcelFileDescriptor

class PdfDocument(val NativeDocPtr: Long, var FileDescriptor: ParcelFileDescriptor?) {
    val NativePagesPtr = mutableMapOf<Int, Long>()
    val NativeTextPagesPtr = mutableMapOf<Int, Long>()

    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private val originalUserPages: MutableList<Int>? = null

    var pagesCount = 0
        private set

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage a page number
     * @return A restricted valid page number (example: -2 >= 0)
     */
    fun determineValidPageNumberFrom(userPage: Int): Int {
        if (userPage <= 0) {
            return 0
        }

        if (originalUserPages != null) {
            if (userPage >= originalUserPages.size) {
                return originalUserPages.size - 1
            }
        } else {
            if (userPage >= pagesCount) {
                return pagesCount - 1
            }
        }

        return userPage
    }

    data class Link(val bounds: RectF, val destPageIdx: Int, val uri: String)
}