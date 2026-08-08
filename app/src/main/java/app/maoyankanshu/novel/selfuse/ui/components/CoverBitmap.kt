package app.maoyankanshu.novel.selfuse.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Offline cover decode helpers for BookCard tiles.
 * No network; pure sample-size math is unit-testable on the JVM.
 */
object CoverBitmap {
    private const val CACHE_KB = 8 * 1024

    /**
     * Small process-local thumbnail cache. BookCard only requests shelf-sized images, so keeping
     * a bounded set avoids repeated disk/bounds/decode work while scrolling without retaining
     * full-size covers. Lazy creation keeps the pure math helpers usable in local JVM tests
     * without initializing Android framework cache classes.
     *
     * Eviction never recycles bitmaps because an on-screen Compose Image may still hold a
     * reference to one.
     */
    private val cache: LruCache<String, Bitmap> by lazy {
        object : LruCache<String, Bitmap>(CACHE_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

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
        val file = File(path)
        if (!file.isFile) return null
        val key = coverCacheKey(
            path = file.absolutePath,
            lastModified = file.lastModified(),
            fileLength = file.length(),
            reqWidthPx = reqWidthPx,
            reqHeightPx = reqHeightPx,
        )
        synchronized(cache) {
            cache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return it }
        }

        val decoded = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
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
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }

        if (decoded != null) {
            synchronized(cache) {
                cache.put(key, decoded)
            }
        }
        return decoded
    }

    internal fun coverCacheKey(
        path: String,
        lastModified: Long,
        fileLength: Long,
        reqWidthPx: Int,
        reqHeightPx: Int,
    ): String = buildString {
        append(path)
        append('|')
        append(lastModified)
        append('|')
        append(fileLength)
        append('|')
        append(reqWidthPx.coerceAtLeast(1))
        append('x')
        append(reqHeightPx.coerceAtLeast(1))
    }
}
