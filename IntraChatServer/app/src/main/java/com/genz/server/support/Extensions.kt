package com.genz.server.support

import androidx.fragment.app.Fragment
import com.genz.server.MainActivity

fun Fragment.appActivity(): MainActivity? {
    return requireActivity() as? MainActivity
}