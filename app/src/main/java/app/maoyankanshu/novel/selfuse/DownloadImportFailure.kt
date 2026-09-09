package app.maoyankanshu.novel.selfuse

import android.content.Context
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** User-actionable classification shared by direct-link and in-browser downloads. */
internal object DownloadImportFailure {
    enum class Kind {
        TOO_LARGE,
        UNSUPPORTED_OR_EMPTY,
        /** Login page because the session cookie stayed on the original host. */
        LOGIN_CROSS_ORIGIN,
        AUTH_REQUIRED,
        LINK_EXPIRED,
        SECURE_CONNECTION,
        RATE_LIMITED,
        SERVER_UNAVAILABLE,
        HTTP,
        NETWORK,
        UNKNOWN,
    }

    data class Detail(
        val kind: Kind,
        val httpStatus: Int? = null,
        val retryAfterSeconds: Long? = null,
    )

    private val httpStatusPattern = Regex("\\bHTTP\\s+(\\d{3})\\b", RegexOption.IGNORE_CASE)

    fun classify(error: Throwable): Detail {
        val causes = causeChain(error)
        for (cause in causes) {
            if (cause is HttpStatusException) {
                return Detail(
                    kind = kindForHttpStatus(cause.statusCode),
                    httpStatus = cause.statusCode,
                    retryAfterSeconds = cause.retryAfterSeconds,
                )
            }
            val message = cause.message.orEmpty()
            val lower = message.lowercase()
            if (lower.contains("too large") || lower.contains("32mb") || lower.contains("50mb")) {
                return Detail(Kind.TOO_LARGE)
            }
            httpStatusPattern.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                return Detail(kindForHttpStatus(it), httpStatus = it)
            }
            if (lower.contains("login cookie not sent cross-origin")) {
                return Detail(Kind.LOGIN_CROSS_ORIGIN)
            }
            if (
                lower == "empty" ||
                lower.contains("not txt or epub") ||
                lower.contains("invalid epub") ||
                lower.contains("zip error")
            ) {
                return Detail(Kind.UNSUPPORTED_OR_EMPTY)
            }
        }
        if (
            causes.any {
                it is SSLHandshakeException ||
                    it is SSLPeerUnverifiedException ||
                    it is CertificateException
            }
        ) {
            return Detail(Kind.SECURE_CONNECTION)
        }
        if (causes.any { it is IOException }) return Detail(Kind.NETWORK)
        return Detail(Kind.UNKNOWN)
    }

    /**
     * Whether immediately issuing the same request can reasonably recover.
     * Authentication, expired links, bad formats, and size limits require a
     * different user action; offering a retry there only repeats a known failure.
     */
    fun isRetryUseful(detail: Detail): Boolean = when (detail.kind) {
        Kind.RATE_LIMITED,
        Kind.SERVER_UNAVAILABLE,
        Kind.HTTP,
        Kind.NETWORK,
        Kind.UNKNOWN,
        -> true

        Kind.TOO_LARGE,
        Kind.UNSUPPORTED_OR_EMPTY,
        Kind.LOGIN_CROSS_ORIGIN,
        Kind.AUTH_REQUIRED,
        Kind.LINK_EXPIRED,
        Kind.SECURE_CONNECTION,
        -> false
    }

    /**
     * Import/Download button enabled state. `null` means a fresh attempt (no
     * failure yet, or the user edited/pasted a new URL).
     */
    fun isRetryEnabled(detail: Detail?): Boolean = detail?.let(::isRetryUseful) ?: true

    /**
     * URL field edited or pasted: drop the previous classified failure so a
     * non-retryable 401/403, 404/410, or oversize does not keep the button
     * disabled after the address changes. 429 cooldown still lives in
     * [RateLimitThrottle] and does not depend on this field.
     */
    @Suppress("UNUSED_PARAMETER")
    fun detailAfterUrlEdited(previous: Detail?): Detail? = null

    /** At least 60 seconds for 429; respect a longer server delay up to 15 minutes. */
    fun rateLimitCooldownMs(detail: Detail): Long {
        val defaultMs = 60_000L
        if (detail.kind != Kind.RATE_LIMITED) return defaultMs
        val serverMs = detail.retryAfterSeconds
            ?.coerceAtMost(15L * 60L)
            ?.times(1_000L)
            ?: return defaultMs
        return maxOf(defaultMs, serverMs)
    }

    private fun kindForHttpStatus(status: Int): Kind = when (status) {
        401, 403 -> Kind.AUTH_REQUIRED
        404, 410 -> Kind.LINK_EXPIRED
        429 -> Kind.RATE_LIMITED
        in 500..599 -> Kind.SERVER_UNAVAILABLE
        else -> Kind.HTTP
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val result = ArrayList<Throwable>(4)
        var current: Throwable? = error
        repeat(12) {
            val value = current ?: return result
            if (result.any { it === value }) return result
            result += value
            current = value.cause
        }
        return result
    }
}

/** Same-URL 429 throttle shared by browser-tab and direct-link imports.
 *
 * A 429 means the site is already asking us to slow down; hammering retry would only
 * extend the ban. [windowMs] is the default; [record] may accept a longer server-provided
 * cooldown. During it [shouldThrottle] stays true for the same URL so callers replay
 * guidance instead of real requests. Success or a different URL is not throttled.
 * [nowMs] is injectable so the window math stays JVM-testable without clocks.
 */
internal class RateLimitThrottle(val windowMs: Long = 60_000) {
    private var url: String = ""
    private var blockedUntilMs: Long = 0L

    fun record(url: String, nowMs: Long, cooldownMs: Long = windowMs) {
        if (url.isBlank()) return
        this.url = url
        blockedUntilMs = nowMs + cooldownMs.coerceAtLeast(0L)
    }

    fun shouldThrottle(url: String, nowMs: Long): Boolean {
        return remainingMs(url, nowMs) > 0L
    }

    fun remainingMs(url: String, nowMs: Long): Long {
        if (url.isBlank() || url != this.url) return 0L
        return (blockedUntilMs - nowMs).coerceAtLeast(0L)
    }

    fun clear(url: String) {
        if (url == this.url) {
            this.url = ""
            blockedUntilMs = 0L
        }
    }
}

/** Resolve localized guidance while keeping exception classification JVM-testable. */
internal fun Context.downloadImportFailureMessage(error: Throwable, unknownMessageRes: Int): String =
    downloadImportFailureMessage(DownloadImportFailure.classify(error), unknownMessageRes)

/** Same as above but reuses an already-classified result (e.g. retry throttle checks). */
internal fun Context.downloadImportFailureMessage(
    detail: DownloadImportFailure.Detail,
    unknownMessageRes: Int,
): String {
    return when (detail.kind) {
        DownloadImportFailure.Kind.TOO_LARGE -> getString(R.string.download_import_too_large)
        DownloadImportFailure.Kind.UNSUPPORTED_OR_EMPTY -> getString(R.string.download_import_unsupported)
        DownloadImportFailure.Kind.LOGIN_CROSS_ORIGIN -> getString(R.string.download_import_login_cross_origin)
        DownloadImportFailure.Kind.AUTH_REQUIRED -> getString(
            R.string.download_import_auth_required,
            detail.httpStatus ?: 0,
        )
        DownloadImportFailure.Kind.LINK_EXPIRED -> getString(
            R.string.download_import_link_expired,
            detail.httpStatus ?: 0,
        )
        DownloadImportFailure.Kind.SECURE_CONNECTION -> getString(
            R.string.download_import_secure_connection,
        )
        DownloadImportFailure.Kind.RATE_LIMITED -> {
            if (detail.retryAfterSeconds != null) {
                getString(
                    R.string.download_import_rate_limited_wait,
                    DownloadImportFailure.rateLimitCooldownMs(detail) / 1_000L,
                )
            } else {
                getString(R.string.download_import_rate_limited)
            }
        }
        DownloadImportFailure.Kind.SERVER_UNAVAILABLE -> getString(
            R.string.download_import_server_unavailable,
            detail.httpStatus ?: 0,
        )
        DownloadImportFailure.Kind.HTTP -> getString(
            R.string.download_import_http,
            detail.httpStatus ?: 0,
        )
        DownloadImportFailure.Kind.NETWORK -> getString(R.string.download_import_network)
        DownloadImportFailure.Kind.UNKNOWN -> getString(unknownMessageRes)
    }
}
