package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.pesatrackexpensetrackapp.R

/** Loads the app-bundled Roboto faces and applies them consistently on all supported Android versions. */
object AppTypography {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null

    fun typeface(context: Context, isBold: Boolean = false): Typeface = synchronized(this) {
        val cached = if (isBold) bold else regular
        if (cached != null) return cached
        val loaded = ResourcesCompat.getFont(context, if (isBold) R.font.roboto_bold else R.font.roboto_regular)
            ?: Typeface.create("sans-serif", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        if (isBold) bold = loaded else regular = loaded
        loaded
    }

    fun apply(view: View) {
        if (view is TextView) {
            view.typeface = typeface(view.context, view.typeface?.isBold == true)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) apply(view.getChildAt(index))
        }
    }

    fun installOnFragments(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    apply(view)
                }
            },
            true,
        )
    }
}
