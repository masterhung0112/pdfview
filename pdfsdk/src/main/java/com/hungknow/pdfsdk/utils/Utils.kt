package com.hungknow.pdfsdk.utils

import android.util.DisplayMetrics
import android.util.TypedValue

class Utils private constructor() {
    companion object {
        fun getDP(displayMetrics: DisplayMetrics, dp: Float): Float {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics)
        }
    }
}