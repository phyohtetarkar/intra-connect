package com.intrachat.connect.support

import androidx.annotation.ColorRes
import androidx.fragment.app.Fragment
import com.intrachat.connect.MainActivity

fun Fragment.getResourceColor(@ColorRes id: Int): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        resources.getColor(id, null)
    } else {
        resources.getColor(id)
    }
}

fun Fragment.appActivity(): MainActivity? {
    return requireActivity() as? MainActivity
}