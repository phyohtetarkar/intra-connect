package com.intrachat.server.support

import androidx.fragment.app.Fragment
import com.intrachat.server.MainActivity

fun Fragment.appActivity(): MainActivity? {
    return requireActivity() as? MainActivity
}