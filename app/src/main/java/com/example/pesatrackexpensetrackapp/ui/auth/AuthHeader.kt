package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.example.pesatrackexpensetrackapp.R

/** Centered logo, app name, heading and subtitle shared by the sign in and registration screens. */
class AuthHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_auth_header, this, true)
        val title = findViewById<TextView>(R.id.authHeaderTitle)
        ViewCompat.setAccessibilityHeading(title, true)

        val a = context.obtainStyledAttributes(attrs, R.styleable.AuthHeader)
        title.text = a.getString(R.styleable.AuthHeader_headerTitle)
        findViewById<TextView>(R.id.authHeaderSubtitle).text = a.getString(R.styleable.AuthHeader_headerSubtitle)
        a.recycle()
    }
}
