package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * TXT/EPUB import Intent helpers for [SearchActivity].
 *
 * Manifest registers [Intent.ACTION_VIEW] for `content://` and `file://`, and
 * [Intent.ACTION_SEND] with [Intent.EXTRA_STREAM]. Temporary read grants from the
 * sender ([Intent.FLAG_GRANT_READ_URI_PERMISSION]) are used while the activity runs;
 * persistable grants are taken when the provider allows them.
 *
 * Scheme / action resolution is pure string logic so JVM unit tests do not need
 * a working [Uri.parse] mock (android.jar stubs throw on the JVM).
 */
object ImportIntentUris {
    /** Same key as [SearchActivity.EXTRA_IMPORT] / [AppIntents.importLocal]. */
    const val EXTRA_IMPORT: String = "open_import"

    /** Supported URI schemes for offline book import. */
    fun isSupportedScheme(scheme: String?): Boolean {
        if (scheme.isNullOrEmpty()) return false
        return scheme.equals("content", ignoreCase = true) ||
            scheme.equals("file", ignoreCase = true)
    }

    /**
     * Scheme of a URI string without [Uri.parse] (JVM-safe).
     * `"content://x"` → `"content"`; malformed → null.
     */
    fun schemeOf(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val colon = uriString.indexOf(':')
        if (colon <= 0) return null
        val scheme = uriString.substring(0, colon).trim()
        return scheme.ifEmpty { null }
    }

    /**
     * Resolve the book URI string from VIEW data, SEND [Intent.EXTRA_STREAM], or ClipData.
     * Does not open the stream — caller uses [LocalBookImport.fromUri].
     */
    fun resolveUriString(
        action: String?,
        dataUri: String?,
        streamUri: String?,
        clipUri: String? = null,
    ): String? {
        val resolved = when (action) {
            Intent.ACTION_VIEW -> dataUri
            Intent.ACTION_SEND -> streamUri ?: clipUri ?: dataUri
            else -> dataUri ?: streamUri ?: clipUri
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (isSupportedScheme(schemeOf(resolved))) resolved else null
    }

    fun wantsOpenImportPicker(openImportExtra: Boolean): Boolean = openImportExtra

    /**
     * Extract a content/file [Uri] from a system share or "open with" Intent.
     * Handles [Intent.ACTION_VIEW], [Intent.ACTION_SEND] + [Intent.EXTRA_STREAM], ClipData.
     */
    fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        val stream = streamExtra(intent)
        val clip = clipUri(intent)
        val data = intent.dataString
        val resolved = resolveUriString(intent.action, data, stream?.toString(), clip?.toString())
            ?: return null
        return Uri.parse(resolved)
    }

    fun takeReadPermissionIfPossible(context: Context, intent: Intent?, uri: Uri?) {
        if (uri == null || intent == null) return
        if (!isSupportedScheme(uri.scheme) || !uri.scheme.equals("content", ignoreCase = true)) return
        val flags = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Provider may only grant temporary access for this activity launch.
        }
    }

    private fun streamExtra(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }

    private fun clipUri(intent: Intent): Uri? {
        val clip = intent.clipData ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0)?.uri
    }
}
