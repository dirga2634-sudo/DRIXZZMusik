package com.drizzx.camera.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.drizzx.camera.config.FilterPreset
import java.io.ByteArrayOutputStream

/**
 * Renders a [FilterPreset] onto a decoded photo and re-encodes it as JPEG.
 * Does real CPU work (a full-bitmap draw) - callers should run this off the
 * main thread.
 */
object PhotoFilterProcessor {

    fun apply(source: Bitmap, preset: FilterPreset, jpegQuality: Int): ByteArray {
        val rendered = if (isIdentity(preset)) source else renderWithMatrix(source, buildColorMatrix(preset))
        val stream = ByteArrayOutputStream()
        rendered.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), stream)
        if (rendered !== source) {
            rendered.recycle()
        }
        return stream.toByteArray()
    }

    private fun isIdentity(preset: FilterPreset): Boolean =
        preset.saturation == 1f && preset.contrast == 1f && preset.warmth == 0f

    private fun renderWithMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun buildColorMatrix(preset: FilterPreset): ColorMatrix {
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(preset.saturation.coerceIn(0f, 2f))
        }

        val contrast = preset.contrast.coerceIn(0.5f, 2f)
        val contrastTranslate = (1 - contrast) * 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, contrastTranslate,
                0f, contrast, 0f, 0f, contrastTranslate,
                0f, 0f, contrast, 0f, contrastTranslate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val warmthShift = preset.warmth.coerceIn(-1f, 1f) * 24f
        val warmthMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, warmthShift,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -warmthShift,
                0f, 0f, 0f, 1f, 0f
            )
        )

        saturationMatrix.postConcat(contrastMatrix)
        saturationMatrix.postConcat(warmthMatrix)
        return saturationMatrix
    }
}
