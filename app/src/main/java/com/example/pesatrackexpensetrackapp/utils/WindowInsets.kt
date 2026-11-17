package com.example.pesatrackexpensetrackapp.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps activity content clear of status, navigation, and display-cutout areas. */
fun View.applySystemBarInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val safeArea = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.setPadding(
            initialLeft + safeArea.left,
            initialTop + safeArea.top,
            initialRight + safeArea.right,
            initialBottom + safeArea.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
