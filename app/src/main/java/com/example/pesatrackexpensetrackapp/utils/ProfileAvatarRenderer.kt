package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.pesatrackexpensetrackapp.R
import java.util.Locale

fun profileInitial(name: String?, email: String?): String {
    val source = name?.trim().orEmpty().ifBlank { email?.substringBefore('@').orEmpty() }
    return source.firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: "P"
}

fun renderProfileAvatar(
    context: Context,
    userId: String?,
    name: String?,
    email: String?,
    container: FrameLayout,
    initialView: TextView,
    photoView: ImageView,
) {
    initialView.text = profileInitial(name, email)
    val photoFile = userId?.takeIf(String::isNotBlank)?.let { ProfileImageStore.fileFor(context, it) }
    val bitmap = photoFile?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    if (bitmap == null) {
        photoView.setImageDrawable(null)
        photoView.visibility = View.GONE
        initialView.visibility = View.VISIBLE
        container.contentDescription = "Profile picture, ${initialView.text}"
    } else {
        photoView.setImageBitmap(bitmap)
        photoView.visibility = View.VISIBLE
        initialView.visibility = View.GONE
        container.contentDescription = "Profile photo"
    }
}
