package app.maoyankanshu.novel.selfuse.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Offline cover decode helpers for BookCard tiles.
 * No network; pure sample-size math is unit-testable on the JVM.
 */
object CoverBitmap {

    /**
     * Power-of-two [BitmapFactory.Options.inSampleSize] so decoded pixels stay at least
     * [reqWidth]×[reqHeight] when the source is larger (standard Android downsample).
     * Returns 1 when source already fits or dimensions are invalid.
     */
    fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) {
            return 1
        }
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while (
                halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Decode a local cover file with bounds probe + bounded sample size for a small tile.
     * Missing/malformed/unreadable files → null (caller shows gradient fallback).
     */
    fun decodeFile(path: String, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    reqWidthPx,
                    reqHeightPx,
                )
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
