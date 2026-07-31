package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.ArrayList

/**
 * TXT/EPUB import Intent helpers for [SearchActivity].
 *
 * Manifest: [Intent.ACTION_VIEW] (`content://` / `file://`), [Intent.ACTION_SEND] and
 * [Intent.ACTION_SEND_MULTIPLE] with [Intent.EXTRA_STREAM] (single [Uri] or
 * [ArrayList] of URIs). Temporary [Intent.FLAG_GRANT_READ_URI_PERMISSION] grants
 * are honored; persistable grants taken when the provider allows.
 *
 * Multi-share is capped at [MAX_URIS] (20). Scheme / list resolution is pure string
 * logic so JVM unit tests do not need a working [Uri.parse] mock.
 */
object ImportIntentUris {
    /** Same key as [SearchActivity.EXTRA_IMPORT] / [AppIntents.importLocal]. */
    const val EXTRA_IMPORT: String = "open_import"

    /** Max content/file URIs accepted from one SEND_MULTIPLE (or ClipData) share. */
    const val MAX_URIS: Int = 20

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

    /** Keep only content/file URIs, drop empties/duplicates, cap at [maxUris]. */
    fun filterSupportedUriStrings(
        candidates: List<String?>,
        maxUris: Int = MAX_URIS,
    ): List<String> {
        if (maxUris <= 0) return emptyList()
        val out = ArrayList<String>(minOf(candidates.size, maxUris))
        val seen = HashSet<String>()
        for (raw in candidates) {
            val uri = raw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (!isSupportedScheme(schemeOf(uri))) continue
            if (!seen.add(uri)) continue
            out.add(uri)
            if (out.size >= maxUris) break
        }
        return out
    }

    /**
     * Resolve book URI string(s) from VIEW / SEND / SEND_MULTIPLE extras (string form).
     * [streamUris] is EXTRA_STREAM as zero-or-more URI strings (single or ArrayList).
     * [clipUris] is ClipData item URIs in order.
     */
    fun resolveUriStrings(
        action: String?,
        dataUri: String?,
        streamUris: List<String?> = emptyList(),
        clipUris: List<String?> = emptyList(),
        maxUris: Int = MAX_URIS,
    ): List<String> {
        val streams = streamUris.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        val clips = clipUris.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        val data = dataUri?.trim()?.takeIf { it.isNotEmpty() }

        val candidates: List<String?> = when (action) {
            Intent.ACTION_VIEW -> listOf(data)
            Intent.ACTION_SEND -> listOf(streams.firstOrNull() ?: clips.firstOrNull() ?: data)
            Intent.ACTION_SEND_MULTIPLE -> {
                when {
                    streams.isNotEmpty() -> streams
                    clips.isNotEmpty() -> clips
                    else -> listOf(data)
                }
            }
            else -> {
                if (streams.size > 1) streams
                else listOf(streams.firstOrNull() ?: clips.firstOrNull() ?: data)
            }
        }
        return filterSupportedUriStrings(candidates, maxUris)
    }

    /**
     * Single-URI convenience (VIEW / SEND). Prefer [resolveUriStrings] for multi.
     */
    fun resolveUriString(
        action: String?,
        dataUri: String?,
        streamUri: String?,
        clipUri: String? = null,
    ): String? = resolveUriStrings(
        action = action,
        dataUri = dataUri,
        streamUris = listOfNotNull(streamUri),
        clipUris = listOfNotNull(clipUri),
        maxUris = 1,
    ).firstOrNull()

    fun wantsOpenImportPicker(openImportExtra: Boolean): Boolean = openImportExtra

    /**
     * Extract content/file [Uri]s from VIEW, SEND, or SEND_MULTIPLE.
     * SEND_MULTIPLE: [Intent.EXTRA_STREAM] as [ArrayList] of [Uri], else ClipData.
     * Capped at [MAX_URIS].
     */
    fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val streams = streamExtras(intent).map { it.toString() }
        val clips = clipUris(intent).map { it.toString() }
        val data = intent.dataString
        val resolved = resolveUriStrings(intent.action, data, streams, clips, MAX_URIS)
        return resolved.map { Uri.parse(it) }
    }

    /** First URI only (back-compat for single open-with). */
    fun extractUri(intent: Intent?): Uri? = extractUris(intent).firstOrNull()

    fun takeReadPermissionIfPossible(context: Context, intent: Intent?, uri: Uri?) {
        if (uri == null || intent == null) return
        if (!uri.scheme.equals("content", ignoreCase = true)) return
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

    fun takeReadPermissionsIfPossible(context: Context, intent: Intent?, uris: List<Uri>) {
        for (uri in uris) {
            takeReadPermissionIfPossible(context, intent, uri)
        }
    }

    /**
     * EXTRA_STREAM: single Uri (SEND) or ArrayList&lt;Uri&gt; (SEND_MULTIPLE).
     * API 33+ typed getters; older SDKs use deprecated Parcelable APIs (minSdk 23).
     */
    private fun streamExtras(intent: Intent): List<Uri> {
        val action = intent.action
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (list.isNullOrEmpty()) return emptyList()
            return list.filterIsInstance<Uri>()
        }
        // SEND or unknown: single EXTRA_STREAM Uri
        val one = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        // Some multi-shares still use SEND_MULTIPLE list under SEND — try ArrayList too.
        if (one == null && action == Intent.ACTION_SEND) {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!list.isNullOrEmpty()) return list.filterIsInstance<Uri>()
        }
        return listOfNotNull(one)
    }

    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        if (clip.itemCount <= 0) return emptyList()
        val out = ArrayList<Uri>(clip.itemCount)
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i)?.uri ?: continue
            out.add(uri)
        }
        return out
    }
}
