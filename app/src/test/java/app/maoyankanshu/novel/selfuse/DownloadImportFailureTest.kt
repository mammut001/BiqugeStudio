package app.maoyankanshu.novel.selfuse

import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadImportFailureTest {
    @Test
    fun oversizedResponseIsRecognizedThroughWrappedCause() {
        val detail = DownloadImportFailure.classify(
            RuntimeException("wrapper", IllegalStateException("too large")),
        )

        assertEquals(DownloadImportFailure.Kind.TOO_LARGE, detail.kind)
        assertNull(detail.httpStatus)
    }

    @Test
    fun httpStatusIsReturnedForActionableMessage() {
        val detail = DownloadImportFailure.classify(IllegalStateException("HTTP 418"))

        assertEquals(DownloadImportFailure.Kind.HTTP, detail.kind)
        assertEquals(418, detail.httpStatus)
    }

    @Test
    fun authenticationAndExpiredLinksHaveDistinctGuidance() {
        for (status in listOf(401, 403)) {
            val detail = DownloadImportFailure.classify(IllegalStateException("HTTP $status"))
            assertEquals(DownloadImportFailure.Kind.AUTH_REQUIRED, detail.kind)
            assertEquals(status, detail.httpStatus)
        }
        for (status in listOf(404, 410)) {
            val detail = DownloadImportFailure.classify(IllegalStateException("HTTP $status"))
            assertEquals(DownloadImportFailure.Kind.LINK_EXPIRED, detail.kind)
            assertEquals(status, detail.httpStatus)
        }
    }

    @Test
    fun rateLimitingAndServerFailuresDoNotSuggestSigningInAgain() {
        val limited = DownloadImportFailure.classify(
            RuntimeException("download failed", IllegalStateException("HTTP 429")),
        )
        assertEquals(DownloadImportFailure.Kind.RATE_LIMITED, limited.kind)
        assertEquals(429, limited.httpStatus)
        for (status in listOf(500, 502, 503, 504)) {
            val unavailable = DownloadImportFailure.classify(IllegalStateException("HTTP $status"))
            assertEquals(DownloadImportFailure.Kind.SERVER_UNAVAILABLE, unavailable.kind)
            assertEquals(status, unavailable.httpStatus)
        }
        assertEquals(
            DownloadImportFailure.Kind.HTTP,
            DownloadImportFailure.classify(IllegalStateException("HTTP 418")).kind,
        )
    }

    @Test
    fun rateLimitCarriesRetryAfterAndChoosesSafeCooldown() {
        val detail = DownloadImportFailure.classify(
            HttpStatusException(statusCode = 429, retryAfterSeconds = 120L),
        )
        assertEquals(DownloadImportFailure.Kind.RATE_LIMITED, detail.kind)
        assertEquals(429, detail.httpStatus)
        assertEquals(120L, detail.retryAfterSeconds)
        assertEquals(120_000L, DownloadImportFailure.rateLimitCooldownMs(detail))

        assertEquals(
            60_000L,
            DownloadImportFailure.rateLimitCooldownMs(
                DownloadImportFailure.Detail(
                    DownloadImportFailure.Kind.RATE_LIMITED,
                    retryAfterSeconds = 10L,
                ),
            ),
        )
        assertEquals(
            15L * 60_000L,
            DownloadImportFailure.rateLimitCooldownMs(
                DownloadImportFailure.Detail(
                    DownloadImportFailure.Kind.RATE_LIMITED,
                    retryAfterSeconds = Long.MAX_VALUE,
                ),
            ),
        )
    }

    @Test
    fun unsupportedAndEmptyBodiesShareFormatGuidance() {
        assertEquals(
            DownloadImportFailure.Kind.UNSUPPORTED_OR_EMPTY,
            DownloadImportFailure.classify(IllegalArgumentException("response is not TXT or EPUB")).kind,
        )
        assertEquals(
            DownloadImportFailure.Kind.UNSUPPORTED_OR_EMPTY,
            DownloadImportFailure.classify(IllegalStateException("empty")).kind,
        )
    }

    @Test
    fun crossOriginLoginWallHasOwnGuidance() {
        val detail = DownloadImportFailure.classify(
            IllegalArgumentException("response is not TXT or EPUB (login cookie not sent cross-origin)"),
        )

        assertEquals(DownloadImportFailure.Kind.LOGIN_CROSS_ORIGIN, detail.kind)
        assertNull(detail.httpStatus)
    }

    @Test
    fun ioAndUnknownFailuresRemainDistinct() {
        assertEquals(
            DownloadImportFailure.Kind.NETWORK,
            DownloadImportFailure.classify(SocketTimeoutException("timed out")).kind,
        )
        assertEquals(
            DownloadImportFailure.Kind.NETWORK,
            DownloadImportFailure.classify(java.net.ConnectException("Failed to connect")).kind,
        )
        assertEquals(
            DownloadImportFailure.Kind.UNKNOWN,
            DownloadImportFailure.classify(IllegalStateException("disk unavailable")).kind,
        )
    }

    @Test
    fun certificateFailureIsNotMisreportedAsOrdinaryNetworkLoss() {
        val detail = DownloadImportFailure.classify(
            RuntimeException(
                "wrapped",
                SSLHandshakeException("certificate expired"),
            ),
        )

        assertEquals(DownloadImportFailure.Kind.SECURE_CONNECTION, detail.kind)
        assertNull(detail.httpStatus)
        assertFalse(DownloadImportFailure.isRetryUseful(detail))
    }

    @Test
    fun retryIsOnlyOfferedWhenTheSameRequestCanRecover() {
        for (
            kind in listOf(
                DownloadImportFailure.Kind.RATE_LIMITED,
                DownloadImportFailure.Kind.SERVER_UNAVAILABLE,
                DownloadImportFailure.Kind.HTTP,
                DownloadImportFailure.Kind.NETWORK,
                DownloadImportFailure.Kind.UNKNOWN,
            )
        ) {
            assertTrue(DownloadImportFailure.isRetryUseful(DownloadImportFailure.Detail(kind)))
        }
        for (
            kind in listOf(
                DownloadImportFailure.Kind.TOO_LARGE,
                DownloadImportFailure.Kind.UNSUPPORTED_OR_EMPTY,
                DownloadImportFailure.Kind.LOGIN_CROSS_ORIGIN,
                DownloadImportFailure.Kind.AUTH_REQUIRED,
                DownloadImportFailure.Kind.LINK_EXPIRED,
                DownloadImportFailure.Kind.SECURE_CONNECTION,
            )
        ) {
            assertFalse(DownloadImportFailure.isRetryUseful(DownloadImportFailure.Detail(kind)))
        }
        assertTrue(DownloadImportFailure.isRetryEnabled(null))
    }

    @Test
    fun urlEditClearsNonRetryableFailureSoImportReenables() {
        for (
            kind in listOf(
                DownloadImportFailure.Kind.AUTH_REQUIRED,
                DownloadImportFailure.Kind.LINK_EXPIRED,
                DownloadImportFailure.Kind.TOO_LARGE,
            )
        ) {
            val stale = DownloadImportFailure.Detail(kind, httpStatus = 401)
            assertFalse(kind.name, DownloadImportFailure.isRetryEnabled(stale))
            val afterEdit = DownloadImportFailure.detailAfterUrlEdited(stale)
            assertNull(kind.name, afterEdit)
            assertTrue(kind.name, DownloadImportFailure.isRetryEnabled(afterEdit))
        }
        val limited = DownloadImportFailure.Detail(
            DownloadImportFailure.Kind.RATE_LIMITED,
            httpStatus = 429,
        )
        assertTrue(DownloadImportFailure.isRetryEnabled(limited))
        assertNull(DownloadImportFailure.detailAfterUrlEdited(limited))
        assertTrue(DownloadImportFailure.isRetryEnabled(DownloadImportFailure.detailAfterUrlEdited(limited)))
    }

    @Test
    fun rateLimitThrottleOnlyHoldsTheSameUrlInsideWindow() {
        val throttle = RateLimitThrottle(windowMs = 60_000)
        throttle.record("https://example.com/book.epub", 1_000L)

        assertTrue(throttle.shouldThrottle("https://example.com/book.epub", 30_000L))
        assertEquals(31_000L, throttle.remainingMs("https://example.com/book.epub", 30_000L))
        assertFalse(throttle.shouldThrottle("https://example.com/other.epub", 30_000L))
        assertEquals(0L, throttle.remainingMs("https://example.com/other.epub", 30_000L))
        assertFalse(throttle.shouldThrottle("https://example.com/book.epub", 61_000L))
        assertEquals(0L, throttle.remainingMs("https://example.com/book.epub", 61_000L))
        assertFalse(throttle.shouldThrottle("", 30_000L))
    }

    @Test
    fun blockedRetryDoesNotExtendCooldownAndClearReleases() {
        val throttle = RateLimitThrottle(windowMs = 60_000)
        throttle.record("https://example.com/book.epub", 1_000L)

        // Merely checking a blocked retry cannot move the original 429 timestamp.
        assertTrue(throttle.shouldThrottle("https://example.com/book.epub", 50_000L))
        assertFalse(throttle.shouldThrottle("https://example.com/book.epub", 61_000L))

        throttle.record("https://example.com/book.epub", 70_000L)
        throttle.clear("https://example.com/book.epub")
        assertFalse(throttle.shouldThrottle("https://example.com/book.epub", 100_000L))
    }

    @Test
    fun rateLimitThrottleAcceptsLongerServerCooldown() {
        val throttle = RateLimitThrottle(windowMs = 60_000L)
        throttle.record(
            "https://example.com/book.epub",
            nowMs = 1_000L,
            cooldownMs = 120_000L,
        )

        assertTrue(throttle.shouldThrottle("https://example.com/book.epub", 61_000L))
        assertEquals(60_000L, throttle.remainingMs("https://example.com/book.epub", 61_000L))
        assertFalse(throttle.shouldThrottle("https://example.com/book.epub", 121_000L))
    }
}
