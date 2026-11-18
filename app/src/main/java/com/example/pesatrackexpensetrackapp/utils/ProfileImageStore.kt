package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Stores a small profile image inside this app's private files directory. */
object ProfileImageStore {
    private const val MAX_IMAGE_DIMENSION = 512
    private const val MAX_CROP_DIMENSION = 2048

    data class SaveResult(val saved: Boolean, val reason: String? = null)

    fun fileFor(context: Context, userId: String): File {
        val safeUserId = userId.filter(Char::isLetterOrDigit)
        return File(File(context.filesDir, "profile_images"), "$safeUserId.jpg")
    }

    fun loadForCrop(context: Context, uri: Uri): Bitmap? {
        val source = File(context.cacheDir, "profile-source-${System.nanoTime()}")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return null
            input.use { stream -> source.outputStream().use { output -> stream.copyTo(output) } }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_CROP_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_CROP_DIMENSION
            ) sampleSize *= 2

            return BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } catch (exception: Exception) {
            android.util.Log.e("ProfileImageStore", "Failed to read selected profile photo", exception)
            return null
        } finally {
            source.delete()
        }
    }

    fun save(context: Context, userId: String, crop: Bitmap): SaveResult {
        try {
            val destination = fileFor(context, userId)
            val parent = destination.parentFile
                ?: return SaveResult(false, "The app could not create a profile photo folder.")
            if (!parent.exists() && !parent.mkdirs()) {
                return SaveResult(false, "The app could not create a profile photo folder.")
            }

            val resized = if (crop.width > MAX_IMAGE_DIMENSION || crop.height > MAX_IMAGE_DIMENSION) {
                Bitmap.createScaledBitmap(crop, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, true)
            } else crop
            try {
                val temporary = File(parent, "${destination.name}.tmp")
                val compressed = FileOutputStream(temporary).use { output ->
                    val result = resized.compress(Bitmap.CompressFormat.JPEG, 88, output)
                    output.flush()
                    output.fd.sync()
                    result
                }
                if (!compressed) {
                    temporary.delete()
                    return SaveResult(false, "The app could not save the photo on this device.")
                }
                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    return SaveResult(false, "The app could not replace the existing profile photo.")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    return SaveResult(false, "The app could not save the photo on this device.")
                }
                return SaveResult(true)
            } finally {
                if (resized !== crop) resized.recycle()
            }
        } catch (exception: Exception) {
            android.util.Log.e("ProfileImageStore", "Failed to save selected profile photo", exception)
            return SaveResult(false, exception.localizedMessage ?: "An unexpected error occurred while saving the photo.")
        }
    }
}
