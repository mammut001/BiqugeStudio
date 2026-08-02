package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Import and load a user TTF/OTF for the Compose reader.
 * Fonts live under `files/fonts/` so they survive process death with the prefs name.
 */
object ReaderCustomFont {
    const val FONTS_DIR = "fonts"
    const val MAX_FONT_BYTES: Long = 8L * 1024L * 1024L // 8 MiB

    fun fontsDirectory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, FONTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun customFontFile(context: Context, fileName: String): File? {
        val clean = sanitizeFileName(fileName)
        if (clean.isEmpty()) return null
        val file = File(fontsDirectory(context), clean)
        return if (file.isFile && file.length() > 0) file else null
    }

    /**
     * Copy [uri] into app-private fonts and return the stored file name, or null on failure.
     */
    fun importFromUri(context: Context, uri: Uri, displayNameHint: String?): String? {
        val app = context.applicationContext
        val resolver = app.contentResolver
        val rawName = displayNameHint?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "custom.ttf"
        val base = sanitizeFileName(rawName).ifEmpty { "custom.ttf" }
        val name = ensureFontExtension(base)
        val dest = File(fontsDirectory(app), name)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FONT_BYTES) {
                            dest.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            if (!dest.isFile || dest.length() <= 0) {
                dest.delete()
                return null
            }
            // Validate typeface can load.
            if (loadTypeface(dest) == null) {
                dest.delete()
                return null
            }
            name
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    fun deleteCustomFont(context: Context, fileName: String) {
        customFontFile(context, fileName)?.delete()
    }

    fun loadTypeface(file: File): Typeface? =
        try {
            Typeface.createFromFile(file)
        } catch (_: Exception) {
            null
        }

    fun loadFontFamily(context: Context, fileName: String): FontFamily? {
        val file = customFontFile(context, fileName) ?: return null
        val typeface = loadTypeface(file) ?: return null
        return FontFamily(ComposeTypeface(typeface))
    }

    fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        return base.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]"), "_")
            .take(80)
    }

    fun ensureFontExtension(name: String): String {
        val lower = name.lowercase(Locale.US)
        return if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            name
        } else {
            "$name.ttf"
        }
    }

    fun isSupportedFontName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }
}
