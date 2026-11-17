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

    data class SaveResult(val saved: Boolean, val reason: String? = null)

    fun fileFor(context: Context, userId: String): File {
        val safeUserId = userId.filter(Char::isLetterOrDigit)
        return File(File(context.filesDir, "profile_images"), "$safeUserId.jpg")
    }

    fun save(context: Context, userId: String, uri: Uri): SaveResult {
        val source = File(context.cacheDir, "profile-source-${System.nanoTime()}")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return SaveResult(false, "Android could not open the selected photo.")
            input.use { stream -> source.outputStream().use { output -> stream.copyTo(output) } }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return SaveResult(false, "This photo format is not supported. Choose a JPG or PNG image.")
            }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) sampleSize *= 2

            val bitmap = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return SaveResult(false, "Android could not decode this photo. Choose a JPG or PNG image.")

            try {
                val destination = fileFor(context, userId)
                val parent = destination.parentFile
                    ?: return SaveResult(false, "The app could not create a profile photo folder.")
                if (!parent.exists() && !parent.mkdirs()) {
                    return SaveResult(false, "The app could not create a profile photo folder.")
                }

                val temporary = File(parent, "${destination.name}.tmp")
                val compressed = FileOutputStream(temporary).use { output ->
                    val result = bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
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
                bitmap.recycle()
            }
        } catch (exception: Exception) {
            android.util.Log.e("ProfileImageStore", "Failed to save selected profile photo", exception)
            return SaveResult(false, exception.localizedMessage ?: "An unexpected error occurred while saving the photo.")
        } finally {
            source.delete()
        }
    }
}
