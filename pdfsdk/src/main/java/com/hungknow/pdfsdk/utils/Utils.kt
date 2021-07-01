package com.hungknow.pdfsdk.utils

import android.util.DisplayMetrics
import android.util.TypedValue

class Utils private constructor() {
    companion object {
        fun getDP(displayMetrics: DisplayMetrics, dp: Int): Int {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), displayMetrics).toInt()
        }
    }
}