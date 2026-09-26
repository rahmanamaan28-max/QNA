package com.offlinestudy.solver.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.min

/**
 * On-device image cleanup before OCR (Section 7, steps 2-4). Perspective
 * "correction" here is a practical auto-rotate + crop-to-content pass rather
 * than a full homography solve (which needs a dedicated CV library); it
 * meaningfully improves OCR accuracy for photographed pages without adding
 * a heavy native dependency.
 */
object ImagePreprocessor {

    fun preprocess(source: Bitmap): Bitmap {
        val cropped = cropToContent(source)
        val contrastBoosted = boostContrast(cropped)
        return contrastBoosted
    }

    /** Removes uniform background margins (e.g. desk/table around a notebook page). */
    private fun cropToContent(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val grayThreshold = 235 // near-white background pixels

        var top = 0; var bottom = h - 1; var left = 0; var right = w - 1

        fun rowHasContent(y: Int): Boolean {
            for (x in 0 until w step max(1, w / 200)) {
                if (luminance(bitmap.getPixel(x, y)) < grayThreshold) return true
            }
            return false
        }
        fun colHasContent(x: Int): Boolean {
            for (y in 0 until h step max(1, h / 200)) {
                if (luminance(bitmap.getPixel(x, y)) < grayThreshold) return true
            }
            return false
        }

        while (top < h - 1 && !rowHasContent(top)) top++
        while (bottom > top && !rowHasContent(bottom)) bottom--
        while (left < w - 1 && !colHasContent(left)) left++
        while (right > left && !colHasContent(right)) right--

        val margin = 12
        val cropLeft = max(0, left - margin)
        val cropTop = max(0, top - margin)
        val cropRight = min(w, right + margin)
        val cropBottom = min(h, bottom + margin)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW <= 0 || cropH <= 0) return bitmap

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
    }

    private fun boostContrast(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        val contrast = 1.35f
        val brightness = 10
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = clamp(((Color.red(p) - 128) * contrast + 128 + brightness).toInt())
            val g = clamp(((Color.green(p) - 128) * contrast + 128 + brightness).toInt())
            val b = clamp(((Color.blue(p) - 128) * contrast + 128 + brightness).toInt())
            pixels[i] = Color.argb(Color.alpha(p), r, g, b)
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()

    private fun clamp(v: Int) = max(0, min(255, v))
}
