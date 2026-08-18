package com.drizzx.camera.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Builds MediaStore output targets for CameraX so captures show up straight
 * in the system gallery, under Pictures/DrizzxCam and Movies/DrizzxCam.
 *
 * On Android 10+ this needs no storage permission (scoped storage). On
 * Android 9 and below, WRITE_EXTERNAL_STORAGE must already be granted -
 * MainActivity takes care of asking for it on those OS versions only.
 */
object MediaStoreUtils {

    private const val RELATIVE_PATH_PICTURES = "Pictures/DrizzxCam"
    private const val RELATIVE_PATH_MOVIES = "Movies/DrizzxCam"

    private fun timestampedName(prefix: String, extension: String): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${prefix}_${fmt.format(System.currentTimeMillis())}.$extension"
    }

    fun buildPhotoOutputOptions(context: Context): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, timestampedName("IMG", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
    }

    fun buildVideoOutputOptions(context: Context): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, timestampedName("VID", "mp4"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH_MOVIES)
            }
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()
    }

    /**
     * Writes already-processed JPEG bytes (e.g. after a filter pass) as a
     * brand new MediaStore entry. Used instead of [buildPhotoOutputOptions]
     * whenever the raw camera JPEG isn't going to MediaStore unmodified.
     */
    fun writePhotoBytes(context: Context, jpegBytes: ByteArray): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, timestampedName("IMG", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        val wroteOk = try {
            resolver.openOutputStream(uri)?.use { it.write(jpegBytes) } != null
        } catch (e: Exception) {
            false
        }
        if (!wroteOk) {
            resolver.delete(uri, null, null)
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clearPending = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, clearPending, null, null)
        }
        return uri
    }
}
