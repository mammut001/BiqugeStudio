package app.maoyankanshu.novel.selfuse

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.format.Formatter
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import app.maoyankanshu.novel.selfuse.ui.theme.appTopBarColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Small in-app HTTPS browser whose downloads return directly to the new book.
 *
 * The WebView never receives file-system access. A download click is intercepted,
 * fetched into memory with the page's session cookie/referer, decoded by the same
 * bounded TXT/EPUB importer as direct links, and persisted to the private library.
 * On success the browser hands off to the book detail screen so the user lands on
 * the just-imported book without a filesystem step or a shelf hunt.
 */
class BrowserImportActivity : ComponentActivity() {
    // Manifest configChanges covers rotation, so the Activity + WebView survive it
    // without recreation. Last page URL survives process death via saved state below.
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Deep link from direct-link / article pages: open the tab at that page so the
        // download can be tapped in place and returns straight to the shelf.
        val seedPage = intent?.getStringExtra(EXTRA_URL)?.let {
            BrowserDownloadPolicy.normalizeHttpsAddress(it)
        }
        // Seed 是直链文件：建好 WebView 即开下载，不 load 渲染碰运气。
        // 普通页面 seed 照旧打开。进程重建以上次页面为准，不重放下载。
        val seedDownload = seedPage
            ?.takeIf { savedInstanceState == null }
            ?.takeIf { !BrowserDownloadPolicy.isInlineReadablePage(it) }
            ?.takeIf { BrowserDownloadPolicy.isDirectBookUrl(it) }
        val lastPage = savedInstanceState?.getString(KEY_LAST_PAGE)
            ?: seedDownload ?: seedPage
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                BrowserImportScreen(
                    onClose = { finish() },
                    onImported = { bookId, added ->
                        startActivity(AppIntents.bookDetailJustImported(this, bookId, added))
                        finish()
                    },
                    initialLastPageUrl = lastPage,
                    initialDirectDownloadUrl = seedDownload,
                    onSaveLastPageUrl = { url ->
                        lastSavedPageUrl = url
                    },
                )
            }
        }
    }

    private var lastSavedPageUrl: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastSavedPageUrl?.let { outState.putString(KEY_LAST_PAGE, it) }
    }

    companion object {
        private const val KEY_LAST_PAGE: String = "browser_last_page"
        /** Optional HTTPS page to open the in-app tab at (see [AppIntents.browserImport]). */
        const val EXTRA_URL: String = "url"
    }
}

private data class BrowserDownload(
    val url: String,
    val userAgent: String,
    val cookie: String?,
    val referer: String?,
    val suggestedFilename: String? = null,
    val mimeType: String? = null,
    val contentLength: Long = -1L,
)

private data class FailedBrowserDownload(
    val request: BrowserDownload,
    val failure: DownloadImportFailure.Detail,
)

/**
 * Pure download-speed / ETA math for the in-app browser progress overlay.
 * Keeps wall-clock sampling in the UI layer; this object only folds samples into
 * an EMA rate and formats the remaining time. JVM-testable, no Android types.
 */
internal object BrowserDownloadEta {
    /** Min bytes before a rate is trusted (avoids wild early estimates). */
    const val MIN_BYTES_FOR_ETA: Long = 32 * 1024L
    /** EMA weight for each new sample (0..1); higher = more reactive. */
    const val EMA_ALPHA: Double = 0.35

    /**
     * Fold one progress sample into the running EMA rate (bytes/sec).
     * Returns the updated rate; non-positive deltas keep the previous rate.
     */
    fun updateRate(previousRateBps: Double, bytesDelta: Long, elapsedMs: Long): Double {
        if (bytesDelta <= 0L || elapsedMs <= 0L) return previousRateBps
        val sample = bytesDelta.toDouble() / (elapsedMs.toDouble() / 1000.0)
        if (sample <= 0.0 || !sample.isFinite()) return previousRateBps
        if (previousRateBps <= 0.0 || !previousRateBps.isFinite()) return sample
        return previousRateBps + EMA_ALPHA * (sample - previousRateBps)
    }

    /**
     * Remaining seconds, or null when it cannot be estimated yet (unknown total,
     * too few bytes, or no trusted rate). Pure for JVM tests.
     */
    fun remainingSeconds(bytesRead: Long, totalBytes: Long, rateBps: Double): Long? {
        if (totalBytes <= 0L || bytesRead < MIN_BYTES_FOR_ETA) return null
        if (rateBps <= 0.0 || !rateBps.isFinite()) return null
        val remaining = totalBytes - bytesRead
        if (remaining <= 0L) return 0L
        val seconds = (remaining.toDouble() / rateBps).toLong()
        return seconds.coerceAtLeast(0L)
    }

    /**
     * User-facing ETA fragment: "还剩约 X 秒/分钟" or "" when unknown.
     * [formatSeconds] / [formatMinutes] shape the number (e.g. "还剩约%1$d秒").
     */
    fun etaFragment(
        bytesRead: Long,
        totalBytes: Long,
        rateBps: Double,
        formatSeconds: (Long) -> String,
        formatMinutes: (Long) -> String,
    ): String {
        val seconds = remainingSeconds(bytesRead, totalBytes, rateBps) ?: return ""
        if (seconds < 60L) return formatSeconds(seconds.coerceAtLeast(1L))
        return formatMinutes((seconds + 30L) / 60L)
    }
}

/**
 * Pure navigation policy so direct file links are covered by JVM tests.
 *
 * Two entry points, one shared rule. [isDirectBookUrl] decides whether a tapped link in
 * the in-app tab is really a TXT/EPUB download (intercept and import). [isInlineReadablePage]
 * is the mirror: a TXT-looking URL whose response is actually an HTML article — in that
 * case the tab keeps rendering instead of firing a doomed download. Both stay pure string
 * logic so unit tests never need a WebView or network.
 */
internal object BrowserDownloadPolicy {
    private val sensitiveHistoryParameterNames = setOf(
        "access_token",
        "authorization",
        "auth",
        "code",
        "credential",
        "id_token",
        "jwt",
        "password",
        "passwd",
        "refresh_token",
        "secret",
        "session",
        "sessionid",
        "sig",
        "signature",
        "token",
        "x-amz-credential",
        "x-amz-signature",
        "x-goog-credential",
        "x-goog-signature",
    )

    fun normalizeHttpsAddress(rawAddress: String?): String? {
        val raw = rawAddress?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        val parsed = runCatching { URL(candidate) }.getOrNull() ?: return null
        if (!parsed.protocol.equals("https", ignoreCase = true) || parsed.host.isBlank()) return null
        if (parsed.host.any { it.isWhitespace() || it.isISOControl() }) return null
        if (parsed.port != -1 && parsed.port !in 1..65535) return null
        return parsed.toExternalForm()
    }

    /** True when the address is a real `https://` target we may load or download. */
    fun allowsHttpsNavigation(rawUrl: String?): Boolean = normalizeHttpsAddress(rawUrl) != null

    /**
     * Sub-frame navigations: HTTPS may continue; `about:` is required for blank
     * iframes. `http://` and `javascript:` are consumed so they never navigate.
     */
    fun allowsSubFrameNavigation(rawUrl: String?): Boolean {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val scheme = raw.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme == "about") return true
        return allowsHttpsNavigation(raw)
    }

    /** DownloadListener / startImport: never fetch `http://` or `javascript:`. */
    fun allowsDownload(rawUrl: String?): Boolean = allowsHttpsNavigation(rawUrl)

    fun isDirectBookUrl(rawUrl: String?): Boolean {
        val normalized = normalizeHttpsAddress(rawUrl) ?: return false
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return false
        val path = parsed.path.orEmpty()
        if (hasBookExtension(path)) return true
        // A TXT-looking path that continues into a sub-page (…/book.txt/preview) is the
        // site's article, not the file — keep rendering instead of downloading.
        if (looksLikeNestedBookPath(path)) return false
        return RemoteImportDownloader.rawFilenameFromUrlQuery(normalized)
            ?.let(::hasBookExtension) == true
    }

    /**
     * True when a book-suffixed URL is really an inline HTML page: the TXT/EPUB name is
     * followed by more path segments or by a non-download query key. Callers use this to
     * avoid starting a download that would only fail as “not TXT or EPUB”.
     */
    fun isInlineReadablePage(rawUrl: String?): Boolean {
        val normalized = normalizeHttpsAddress(rawUrl) ?: return false
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return false
        // A definite file target wins over incidental book names in query values
        // or parent directories. Navigation and download must never both match.
        if (isDirectBookUrl(normalized)) return false
        if (looksLikeNestedBookPath(parsed.path.orEmpty())) return true
        // “?preview=book.txt”这类非下载键：站内正文页，继续渲染而不是下载。
        // 值里嵌着完整 URL（`?redirect=https://other/x.txt`）的是跳转目标，
        // 不是本站正文——放行给导航/下载，不在这里截留。
        if (RemoteImportDownloader.rawFilenameFromUrlQuery(normalized) != null) return false
        val query = parsed.query.orEmpty()
        if (query.isEmpty()) return false
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            val value = if (separator < 0) pair else pair.substring(separator + 1)
            val lower = value.lowercase()
            if (!lower.contains(".txt") && !lower.contains(".epub")) continue
            if (value.contains("://")) continue
            return true
        }
        return false
    }

    private fun looksLikeNestedBookPath(path: String): Boolean {
        val lower = path.lowercase().trimEnd('/')
        // “…/x.txt” / “…/x.epub” itself is a file; “…/x.txt/…” / “…/x.epub/…” is a
        // page that happens to mention it — keep rendering instead of downloading.
        for (suffix in listOf(".txt/", ".epub/")) {
            val marker = lower.indexOf(suffix)
            if (marker >= 0 && marker + suffix.length <= lower.length) return true
        }
        return false
    }

    private fun hasBookExtension(value: String): Boolean {
        val path = value.lowercase().trimEnd('/')
        if (path.endsWith(".txt") || path.endsWith(".epub")) return true
        if (value.contains('%')) {
            val decoded = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
                .getOrDefault(value).lowercase().trimEnd('/')
            if (decoded.endsWith(".txt") || decoded.endsWith(".epub")) return true
        }
        return false
    }

    /**
     * Same-host check for the address-bar referer guard: a pasted direct link only
     * inherits the showing page as its source when both live on one host.
     * Pure for JVM tests.
     */
    fun sameHost(firstUrl: String?, secondUrl: String?): Boolean {
        val first = firstUrl?.let { runCatching { URL(it) }.getOrNull() } ?: return false
        val second = secondUrl?.let { runCatching { URL(it) }.getOrNull() } ?: return false
        if (first.host.isBlank() || second.host.isBlank()) return false
        return first.host.equals(second.host, ignoreCase = true)
    }

    /**
     * Normalize a page URL for local recent-history storage, or return null when the
     * authority/query/fragment visibly carries credentials. Navigation is unaffected:
     * sensitive URLs can still be opened, but are not persisted or read aloud later.
     */
    fun historyUrl(rawUrl: String?): String? {
        val normalized = normalizeHttpsAddress(rawUrl) ?: return null
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return null
        if (!parsed.userInfo.isNullOrBlank()) return null
        val parameterBlocks = listOfNotNull(parsed.query, parsed.ref)
        val hasSensitiveParameter = parameterBlocks.any { block ->
            block.split('&', ';').any { field ->
                val encodedName = field.substringBefore('=').trim()
                val decodedName = runCatching {
                    URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
                }.getOrDefault(encodedName)
                decodedName.lowercase() in sensitiveHistoryParameterNames
            }
        }
        return normalized.takeUnless { hasSensitiveParameter }
    }

    /**
     * [IntRange] of the host inside the original address, so the UI can
     * emphasize the domain (anti-phishing for long signed URLs). Null when the
     * address is not a valid HTTPS URL. Pure for JVM tests.
     */
    fun hostRange(rawAddress: String?): IntRange? {
        val normalized = normalizeHttpsAddress(rawAddress) ?: return null
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return null
        val host = parsed.host.ifEmpty { return null }
        val raw = rawAddress ?: return null
        val trimmed = raw.trim()
        val leadingSpace = raw.length - raw.trimStart().length
        val authorityStart = if (trimmed.contains("://")) trimmed.indexOf("://") + 3 else 0
        // Search by authority structure, never by the first matching hostname:
        // user-info can contain exactly the same text as the actual host.
        val start = leadingSpace + authorityStart + (parsed.userInfo?.length?.plus(1) ?: 0)
        return start until start + host.length
    }

    /** Short display model for recent-visit chips. Pure for JVM tests. */
    data class RecentDisplay(val host: String, val path: String)

    /**
     * Split a history URL into host + remainder for compact chips
     * ("example.com" + "/books/1"). Falls back to the raw URL as host
     * when unparseable (never blank — callers can always render something).
     */
    fun recentDisplay(rawUrl: String): RecentDisplay {
        val normalized = normalizeHttpsAddress(rawUrl)
        val parsed = normalized?.let { runCatching { URL(it) }.getOrNull() }
        val host = parsed?.host?.takeIf { it.isNotBlank() }
        if (host == null) {
            val fallback = rawUrl.trim().ifEmpty { normalized.orEmpty() }
            return RecentDisplay(host = fallback, path = "")
        }
        val range = hostRange(normalized)!!
        val rest = normalized.substring(range.last + 1)
        return RecentDisplay(host = host, path = rest)
    }
}

/**
 * Address-bar highlight: the host renders bold so a long signed URL's
 * real domain stands out (anti-phishing). Identity mapping — display text == input.
 */
private class BrowserHostHighlight(
    private val hostRange: IntRange?,
    private val highlight: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (hostRange == null) return TransformedText(text, OffsetMapping.Identity)
        val safe = hostRange.first.coerceIn(0, text.length) until
            (hostRange.last + 1).coerceIn(0, text.length)
        if (safe.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val annotated = AnnotatedString(
            text = text.text,
            spanStyles = listOf(AnnotatedString.Range(highlight, safe.first, safe.last + 1)),
        )
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BrowserImportScreen(
    onClose: () -> Unit,
    onImported: (String, Boolean) -> Unit,
    initialLastPageUrl: String?,
    initialDirectDownloadUrl: String?,
    onSaveLastPageUrl: (String?) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var lastPageUrl by rememberSaveable(initialLastPageUrl ?: "") { mutableStateOf(initialLastPageUrl ?: "") }
    // Keep the activity's process-death snapshot fresh as navigation proceeds.
    LaunchedEffect(lastPageUrl) {
        onSaveLastPageUrl(lastPageUrl.takeIf { it.isNotBlank() })
    }
    var pageStarted by rememberSaveable { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    // SSL cancellation can be followed by a generic WebView error callback. Keep the
    // actionable certificate message instead of letting that second callback erase it.
    var securePageFailure by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var lastFailedDownload by remember { mutableStateOf<FailedBrowserDownload?>(null) }
    // 刚吃过 429 的链接：默认 60 秒（网站要求更久则延长）内再点重试
    // 不发真实请求，只重播限流提示。
    // 成功、换链、别的失败原因都不受影响；时间一到自动恢复。
    val rateLimitThrottle = remember { RateLimitThrottle() }
    // 上次主动取消的下载：留在提示行给一个“继续下载”入口。请求里自带页面
    // Cookie/Referer，重试即续跑同一任务；新开下载或输入新网址时失效。
    var lastCancelledDownload by remember { mutableStateOf<BrowserDownload?>(null) }
    var cancellationSignal by remember { mutableStateOf<RemoteImportCancellationSignal?>(null) }
    val downloadSessions = remember { ImportSessionTracker() }
    var bytesRead by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }
    // EMA download rate for the ETA fragment. Plain vars (not Compose state): they are
    // only read when composing the progress label alongside bytesRead/totalBytes updates.
    var etaRateBps by remember { mutableDoubleStateOf(0.0) }
    var etaLastBytes by remember { mutableLongStateOf(0L) }
    var etaLastMs by remember { mutableLongStateOf(0L) }
    fun noteDownloadSample(downloaded: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (etaLastMs > 0L && downloaded > etaLastBytes) {
            etaRateBps = BrowserDownloadEta.updateRate(etaRateBps, downloaded - etaLastBytes, now - etaLastMs)
        }
        etaLastBytes = downloaded
        etaLastMs = now
    }
    fun resetEta() {
        etaRateBps = 0.0
        etaLastBytes = 0L
        etaLastMs = 0L
    }
    val preferences = remember { ReaderPreferences.get(context) }
    val savedBrowserHistory = remember { preferences.browserHistory().toList() }
    var browserHistory: List<String> by remember {
        mutableStateOf(savedBrowserHistory.mapNotNull(BrowserDownloadPolicy::historyUrl).distinct())
    }
    LaunchedEffect(preferences) {
        // Remove legacy entries that older versions stored before the sensitive-URL guard.
        savedBrowserHistory
            .filter { BrowserDownloadPolicy.historyUrl(it) == null }
            .forEach(preferences::removeBrowserHistory)
    }
    fun recordHistory(url: String) {
        val safeUrl = BrowserDownloadPolicy.historyUrl(url) ?: return
        preferences.pushBrowserHistory(safeUrl)
        browserHistory = preferences.browserHistory().toList()
    }

    val httpsOnly = stringResource(R.string.browser_https_only)
    val pageLoadFailed = stringResource(R.string.browser_page_load_fail)
    val downloading = stringResource(R.string.browser_downloading)
    val defaultEpub = stringResource(R.string.remote_default_epub)
    val defaultTxt = stringResource(R.string.remote_default_txt)
    val authorEpub = stringResource(R.string.remote_author_epub)
    val authorTxt = stringResource(R.string.remote_author_txt)
    val fallbackUserAgent = stringResource(R.string.http_user_agent)

    var savedDownloadPageUrl by rememberSaveable { mutableStateOf("") }
    // WebView 建好前用户就点了前往：先记下，建好即按正常流程走
    // （正文页记历史、直链开下载、普通 load），不丢用户输入。
    var pendingAddress by rememberSaveable { mutableStateOf("") }

    fun startImport(original: BrowserDownload) {
        if (downloadJob != null) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val cooldownRemainingMs = rateLimitThrottle.remainingMs(original.url, nowMs)
        if (cooldownRemainingMs > 0L) {
            // 节流窗内连点：不发请求，只重播剩余时间。等待从网站真正
            // 返回 429 时计算；误点“重试”不会把冷却重新续满。
            error = context.getString(
                R.string.download_import_rate_limited_wait,
                (cooldownRemainingMs + 999L) / 1_000L,
            )
            return
        }
        if (!BrowserDownloadPolicy.allowsDownload(original.url)) {
            error = httpsOnly
            return
        }
        // 重试/继续下载复用的是旧请求快照：出发前用当前 CookieManager 刷新同站
        // Cookie（用户可能刚回来源页重登过）。跨站 withholding 语义由下载器按
        // 同源判定，与这里无关，只刷新、不放宽。
        val request = original.copy(
            cookie = CookieManager.getInstance().getCookie(original.url)
                // No current cookie also means the user may have signed out. Do not
                // resurrect credentials captured by an earlier download attempt.
                ?.takeIf { it.isNotBlank() },
        )
        if (BrowserDownloadPolicy.isInlineReadablePage(request.url)) {
            // 站内 TXT 正文页：留在 tab 里继续看，不进下载。
            error = null
            webView?.loadUrl(request.url)
            return
        }
        error = null
        lastFailedDownload = null
        lastCancelledDownload = request
        // 记住下载是从哪个页面触发的：成功后我们要关浏览器跳书籍详情，
        // 万一 Activity 被系统回收，这里还能回到原页面而不是白屏。
        savedDownloadPageUrl = webView?.url?.takeIf { ProgressMath.isHttpsUrl(it) }.orEmpty()
        val sessionToken = downloadSessions.start()
        val signal = RemoteImportCancellationSignal()
        cancellationSignal = signal
        bytesRead = 0L
        totalBytes = request.contentLength.takeIf { it > 0L } ?: -1L
        resetEta()
        downloadJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    RemoteImportDownloader.download(
                        rawUrl = request.url,
                        preferredTitle = request.suggestedFilename.orEmpty(),
                        userAgent = request.userAgent.ifBlank { fallbackUserAgent },
                        defaultEpubTitle = defaultEpub,
                        defaultTxtTitle = defaultTxt,
                        authorEpub = authorEpub,
                        authorTxt = authorTxt,
                        cookie = request.cookie,
                        referer = request.referer,
                        cancellationSignal = signal,
                        fallbackContentType = request.mimeType,
                        fallbackContentDisposition = request.suggestedFilename?.let { "attachment; filename=\"$it\"" },
                        onProgress = { downloaded, total ->
                            activity?.runOnUiThread {
                                if (activity.canAcceptUi() && cancellationSignal === signal) {
                                    bytesRead = downloaded
                                    totalBytes = total
                                    noteDownloadSample(downloaded)
                                }
                            }
                        },
                    )
                }
                ensureActive()
                val addResult = withContext(Dispatchers.IO) {
                    LibraryStore.get(context).addOrGetExisting(
                        result.title,
                        result.author,
                        result.text,
                        result.coverBytes,
                        true,
                    )
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                // 来源页才是下次想回的地方（下载链接本身不是可浏览页面）。
                request.referer
                    ?.takeIf { ProgressMath.isHttpsUrl(it) }
                    ?.let { recordHistory(it) }
                savedDownloadPageUrl = ""
                lastFailedDownload = null
                lastCancelledDownload = null
                rateLimitThrottle.clear(request.url)
                onImported(addResult.id, addResult.added)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!activity.canAcceptUi()) return@launch
                Log.e("YueJianBrowserImport", "Unable to import browser download", cause)
                val failure = DownloadImportFailure.classify(cause)
                error = context.downloadImportFailureMessage(failure, R.string.browser_import_fail)
                lastFailedDownload = FailedBrowserDownload(request, failure)
                if (failure.kind == DownloadImportFailure.Kind.RATE_LIMITED) {
                    rateLimitThrottle.record(
                        request.url,
                        android.os.SystemClock.elapsedRealtime(),
                        DownloadImportFailure.rateLimitCooldownMs(failure),
                    )
                }
            } finally {
                // A cancelled job may finish after a replacement job has started. Only the
                // current owner may reset shared Compose state.
                if (activity.canAcceptUi() && downloadSessions.owns(sessionToken)) {
                    downloadJob = null
                    cancellationSignal = null
                    bytesRead = 0L
                    totalBytes = -1L
                }
            }
        }
    }

    /**
     * Mirror of [interceptBookUrl] for the address bar: a pasted TXT-looking article URL
     * loads as a page instead of firing a download that could only fail as unsupported.
     */
    fun openInlineReadablePage(browser: WebView, normalized: String): Boolean {
        if (!BrowserDownloadPolicy.isInlineReadablePage(normalized)) return false
        error = null
        browser.loadUrl(normalized)
        return true
    }

    fun openAddress() {
        val normalized = BrowserDownloadPolicy.normalizeHttpsAddress(address)
        if (normalized == null) {
            error = httpsOnly
            return
        }
        error = null
        address = normalized
        focusManager.clearFocus()
        keyboard?.hide()
        val browser = webView ?: run {
            // 首帧 WebView 还没建好：记下用户显式输入，建好即走，不静默吞掉。
            pendingAddress = normalized
            return
        }
        pendingAddress = ""
        if (openInlineReadablePage(browser, normalized)) {
            // 站内正文页：记历史，下次一点即达。
            recordHistory(normalized)
            return
        }
        if (BrowserDownloadPolicy.isDirectBookUrl(normalized)) {
            // 地址栏直链：referer 只认同站正在显示的页。刚进 tab 还没加载完、
            // 或跨站粘了新链时置空，串了会记错历史、显错来源。注意不用 lastPageUrl
            // 比对：跳转中 browser.url 已变但 lastPageUrl 还没跟上，会误杀同站来源。
            val currentPage = browser.url?.takeIf { ProgressMath.isHttpsUrl(it) }
            startImport(
                BrowserDownload(
                    url = normalized,
                    userAgent = browser.settings.userAgentString.orEmpty(),
                    cookie = CookieManager.getInstance().getCookie(normalized),
                    referer = currentPage?.takeIf {
                        BrowserDownloadPolicy.sameHost(it, normalized)
                    },
                ),
            )
        } else {
            browser.loadUrl(normalized)
        }
    }

    fun cancelDownload() {
        // 被取消的请求早在 startImport 时存入 lastCancelledDownload，这里只管
        // 停任务、不碰它，错误行即可给出“继续下载”。finally 同理不碰。
        // 先 cancel signal（立即断开底层 HttpURLConnection），再置空——否则
        // 大文件取消后仍在后台跑流量（直链/网页两页都是这个顺序）。
        cancellationSignal?.cancel()
        downloadJob?.cancel()
        downloadSessions.invalidate()
        downloadJob = null
        cancellationSignal = null
        bytesRead = 0L
        totalBytes = -1L
        resetEta()
    }

    fun goBackOrClose() {
        if (downloadJob != null) {
            cancelDownload()
            return
        }
        val browser = webView
        if (browser?.canGoBack() == true) browser.goBack() else onClose()
    }

    fun goForward() {
        if (downloadJob != null) return
        webView?.let { browser ->
            if (browser.canGoForward()) browser.goForward()
        }
    }

    fun refreshNavState(view: WebView?) {
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }

    /**
     * Non-HTTPS main-frame navigations (`intent:` / `market:` / `tel:` …):
     * hand to an external app instead of dead-ending on the HTTPS-only error.
     * `blob:` / `data:` / `about:` / `javascript:` stay blocked (no host app can
     * meaningfully open them, and firing intents at them is a known abuse vector).
     * Returns true when the navigation is consumed (opened or explained).
     */
    fun openExternalScheme(target: String): Boolean {
        val scheme = target.substringBefore(":", "").lowercase()
        if (scheme in setOf("blob", "data", "about", "javascript")) return false
        if (scheme.isEmpty() || scheme == "https" || scheme == "http") return false
        return try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(target),
            )
            context.startActivity(intent)
            Toast.makeText(
                context,
                context.getString(R.string.browser_external_opened),
                Toast.LENGTH_SHORT,
            ).show()
            true
        } catch (_: android.content.ActivityNotFoundException) {
            error = context.getString(R.string.browser_external_missing)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun interceptBookUrl(view: WebView, target: String): Boolean {
        if (BrowserDownloadPolicy.isInlineReadablePage(target)) {
            // “.../book.txt/preview”这类站内正文页：继续渲染，不弹下载。
            error = null
            return false
        }
        if (!BrowserDownloadPolicy.isDirectBookUrl(target)) return false
        startImport(
            BrowserDownload(
                url = target,
                userAgent = view.settings.userAgentString.orEmpty(),
                cookie = CookieManager.getInstance().getCookie(target),
                referer = view.url,
            ),
        )
        return true
    }

    /**
     * Fallback for downloads that never reach [android.webkit.DownloadListener]:
     * POST-form / JS-driven file responses. Intercepts only sub-frame navigations
     * whose URL already looks like a TXT/EPUB file; the main frame keeps rendering
     * so an inline-readable page is never blanked by a speculative download.
     */
    fun interceptSubFrameBookUrl(view: WebView, target: String): Boolean {
        if (!BrowserDownloadPolicy.isDirectBookUrl(target)) return false
        if (BrowserDownloadPolicy.isInlineReadablePage(target)) return false
        startImport(
            BrowserDownload(
                url = target,
                userAgent = view.settings.userAgentString.orEmpty(),
                cookie = CookieManager.getInstance().getCookie(target),
                referer = view.url,
            ),
        )
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun browserClient(): WebViewClient = object : WebViewClient() {
        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (!BrowserDownloadPolicy.allowsHttpsNavigation(url)) {
                if (openExternalScheme(url)) return true
                error = httpsOnly
                return true
            }
            if (interceptBookUrl(view, url)) return true
            error = null
            return false
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: android.webkit.WebResourceRequest,
        ): Boolean {
            val target = request.url.toString()
            if (!request.isForMainFrame) {
                // Sub-frame / attachment navigation: only act on file-looking URLs,
                // never surface HTTPS-only errors for background frames.
                // http:// and javascript: are consumed silently so they never navigate.
                if (!BrowserDownloadPolicy.allowsSubFrameNavigation(target)) return true
                if (interceptSubFrameBookUrl(view, target)) return true
                return false
            }
            if (!BrowserDownloadPolicy.allowsHttpsNavigation(target)) {
                if (openExternalScheme(target)) return true
                error = httpsOnly
                return true
            }
            if (interceptBookUrl(view, target)) return true
            error = null
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            pageStarted = true
            pageProgress = 0
            securePageFailure = false
            refreshNavState(view)
            // A reload after connectivity/login recovery must not keep
            // showing the previous request's error while the new page is
            // loading or after it succeeds. This request will report its
            // own error again if it actually fails.
            error = null
            // 切到新页面了：旧的“继续下载”已过时（Cookie/来源页都对不上新页），
            // 不清掉，用户一点就是给陌生站发旧 Cookie。失败重试同理。
            lastCancelledDownload = null
            lastFailedDownload = null
            url?.let {
                address = it
                lastPageUrl = it
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            pageProgress = 100
            refreshNavState(view)
            url?.let {
                address = it
                lastPageUrl = it
                // 站内点链接逛到的页面也记历史（之前只记地址栏正文页和下载来源页，
                // 普通浏览全丢，最近访问就断了）。去重限长由 prefs 负责。
                // 老 API 会对子 frame 也回调：只记主 frame 当前 URL，避免广告/
                // 统计 iframe 污染历史。
                if (ProgressMath.isHttpsUrl(it) && view?.url == it) recordHistory(it)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: android.webkit.WebResourceRequest,
            webError: android.webkit.WebResourceError,
        ) {
            if (request.isForMainFrame) {
                pageProgress = 100
                if (!securePageFailure) error = pageLoadFailed
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            errorValue: SslError,
        ) {
            // Never offer a proceed path. The download pipeline follows the same strict
            // certificate policy; the browser should explain why the page was stopped.
            handler.cancel()
            securePageFailure = true
            pageProgress = 100
            error = context.getString(R.string.download_import_secure_connection)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: android.webkit.WebResourceRequest,
            errorResponse: android.webkit.WebResourceResponse,
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                pageProgress = 100
                error = context.getString(
                    R.string.browser_http_error,
                    errorResponse.statusCode,
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applyBrowserSettings(browser: WebView) {
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        // Must stay true so target=_blank download buttons reach onCreateWindow and are
        // folded back into this single tab (transport is destroyed right after handoff).
        browser.settings.setSupportMultipleWindows(true)
        browser.settings.javaScriptCanOpenWindowsAutomatically = true
        browser.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            browser.settings.safeBrowsingEnabled = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attachBrowserCallbacks(browser: WebView) {
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pageProgress = newProgress
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                // _blank links (often "download in new tab" buttons): resolve the URL
                // back into this single tab — download it if file-looking, else load it.
                // Needs a transport WebView to complete the gesture; it is destroyed
                // immediately after the URL is handed to our own pipeline.
                if (!isUserGesture) return false
                val transport = WebView(view.context)
                var transportDestroyed = false
                fun destroyTransportOnce() {
                    if (transportDestroyed) return
                    transportDestroyed = true
                    transport.removeCallbacks(null)
                    runCatching { transport.destroy() }
                }
                // Safety: if the popup never commits a URL, drop the transport on the
                // next loop so a parked WebView cannot leak past this gesture.
                transport.postDelayed({ destroyTransportOnce() }, 10_000L)
                // Install both callbacks before handing the WebView to Chromium. An opaque
                // `/download?id=…` popup may go straight to DownloadListener based only on
                // Content-Disposition; if the listener is attached afterwards, that first
                // response can race past us and the tap appears to do nothing.
                transport.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean {
                        val target = request.url.toString()
                        destroyTransportOnce()
                        if (!BrowserDownloadPolicy.allowsHttpsNavigation(target)) {
                            if (openExternalScheme(target)) return true
                            error = httpsOnly
                            return true
                        }
                        if (interceptBookUrl(browser, target)) return true
                        address = target
                        error = null
                        browser.loadUrl(target)
                        return true
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        destroyTransportOnce()
                        if (!BrowserDownloadPolicy.allowsHttpsNavigation(url)) {
                            if (openExternalScheme(url)) return true
                            error = httpsOnly
                            return true
                        }
                        if (interceptBookUrl(browser, url)) return true
                        address = url
                        error = null
                        browser.loadUrl(url)
                        return true
                    }
                }
                transport.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    destroyTransportOnce()
                    val target = url.orEmpty()
                    if (!BrowserDownloadPolicy.allowsDownload(target)) {
                        error = httpsOnly
                        return@setDownloadListener
                    }
                    startImport(
                        BrowserDownload(
                            url = target,
                            userAgent = userAgent.orEmpty(),
                            cookie = url?.let { CookieManager.getInstance().getCookie(it) },
                            referer = browser.url,
                            suggestedFilename = RemoteImportDownloader.parseSafeFilenameFromContentDisposition(contentDisposition),
                            mimeType = mimetype?.takeIf { it.isNotBlank() },
                            contentLength = contentLength,
                        ),
                    )
                }
                (resultMsg.obj as? WebView.WebViewTransport)?.let {
                    it.webView = transport
                    resultMsg.sendToTarget()
                } ?: run {
                    destroyTransportOnce()
                    return false
                }
                return true
            }
        }
        browser.webViewClient = browserClient()
        browser.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val target = url.orEmpty()
            if (!BrowserDownloadPolicy.allowsDownload(target)) {
                error = httpsOnly
                return@setDownloadListener
            }
            val source = browser.url
            startImport(
                BrowserDownload(
                    url = target,
                    userAgent = userAgent.orEmpty(),
                    cookie = CookieManager.getInstance().getCookie(target),
                    referer = source,
                    suggestedFilename = RemoteImportDownloader.parseSafeFilenameFromContentDisposition(contentDisposition),
                    mimeType = mimetype?.takeIf { it.isNotBlank() },
                    contentLength = contentLength,
                ),
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createBrowser(viewContext: android.content.Context): WebView {
        val browser = WebView(viewContext)
        applyBrowserSettings(browser)
        attachBrowserCallbacks(browser)
        webView = browser
        // 用户在 WebView 建好前就点了前往：优先走用户显式输入。
        // 直链即开下载（无页面 referer，与地址栏粘直链一致）；正文页记历史后打开。
        pendingAddress.takeIf { it.isNotBlank() }?.let { pending ->
            pendingAddress = ""
            address = pending
            if (openInlineReadablePage(browser, pending)) {
                recordHistory(pending)
                lastPageUrl = pending
                pageStarted = true
                return browser
            }
            if (BrowserDownloadPolicy.isDirectBookUrl(pending)) {
                lastPageUrl = ""
                pageStarted = false
                startImport(
                    BrowserDownload(
                        url = pending,
                        userAgent = browser.settings.userAgentString.orEmpty(),
                        cookie = CookieManager.getInstance().getCookie(pending),
                        referer = null,
                    ),
                )
                return browser
            }
            lastPageUrl = pending
            pageStarted = true
            browser.loadUrl(pending)
            return browser
        }
        // Process-death return: prefer the page that triggered the download (so a
        // re-created tab after a kill lands back where the user tapped), else the
        // last browsed page. Rotation never reaches here (configChanges keeps the view).
        // 直链 seed：直接开下载（刚进来无页面 Cookie，与地址栏粘直链一致）。
        // referer 留空：本来就没有来源页，history 与来源行都不记不显。
        // onCreate 已归一化 + 判定过直链；这里 isDirectBookUrl 内含 normalize，
        // 顺带复核正文页镜像，避免脏参数绕过。
        initialDirectDownloadUrl
            ?.takeIf { !BrowserDownloadPolicy.isInlineReadablePage(it) }
            ?.takeIf { BrowserDownloadPolicy.isDirectBookUrl(it) }
            ?.let { direct ->
                address = direct
                lastPageUrl = ""
                pageStarted = false
                startImport(
                    BrowserDownload(
                        url = direct,
                        userAgent = browser.settings.userAgentString.orEmpty(),
                        cookie = CookieManager.getInstance().getCookie(direct),
                        referer = null,
                    ),
                )
                return browser
            }
        val resume = savedDownloadPageUrl
            .takeIf { it.isNotBlank() && BrowserDownloadPolicy.normalizeHttpsAddress(it) != null }
            ?: lastPageUrl.takeIf { BrowserDownloadPolicy.normalizeHttpsAddress(it) != null }
        if (resume != null) {
            browser.loadUrl(resume)
            address = resume
            pageStarted = true
        }
        return browser
    }

    BackHandler { goBackOrClose() }
    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal?.cancel()
            downloadJob?.cancel()
            // Rotation does not reach here (configChanges); this is the real finish path.
            webView?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browser_import_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { goBackOrClose() },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(
                                if (downloadJob != null) R.string.browser_back_cancel_cd
                                else R.string.browser_back_cd,
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { goForward() },
                        enabled = downloadJob == null && canGoForward,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.browser_forward_cd)
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                    IconButton(
                        onClick = { webView?.reload() },
                        enabled = downloadJob == null && pageStarted,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.browser_reload_cd)
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.browser_close_cd)
                        },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
                colors = appTopBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Android WebView can otherwise composite over earlier Compose siblings on
                    // some devices. Keep browser chrome in the foreground draw layer.
                    .zIndex(2f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hostHighlight = BrowserHostHighlight(
                    hostRange = BrowserDownloadPolicy.hostRange(address),
                    highlight = SpanStyle(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        error = null
                        lastFailedDownload = null
                        lastCancelledDownload = null
                    },
                    visualTransformation = hostHighlight,
                    enabled = downloadJob == null,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.browser_address_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { openAddress() }),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = context.getString(R.string.browser_address_cd)
                        },
                )
                Button(
                    onClick = { openAddress() },
                    enabled = downloadJob == null && address.isNotBlank(),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.browser_go_cd)
                        },
                ) {
                    Text(stringResource(R.string.browser_go))
                }
            }

            if (pageStarted && pageProgress in 0..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f),
                )
            }
            // 取消后无错误文案时，给一行中性提示 + 继续/回来源页（不算失败）。
            val cancelled = lastCancelledDownload
            if (error == null && cancelled != null && downloadJob == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.browser_cancelled_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { startImport(cancelled) },
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(R.string.browser_resume_download_cd)
                            },
                        ) {
                            Text(stringResource(R.string.browser_resume_download))
                        }
                        val source = cancelled.referer?.takeIf { ProgressMath.isHttpsUrl(it) }
                        if (source != null) {
                            TextButton(
                                onClick = {
                                    lastCancelledDownload = null
                                    address = source
                                    webView?.loadUrl(source)
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = context.getString(R.string.browser_back_to_source_cd)
                                },
                            ) {
                                Text(stringResource(R.string.browser_back_to_source))
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                    val failedDownload = lastFailedDownload
                    if (failedDownload != null && downloadJob == null) {
                        val retry = failedDownload.request
                        val retryUseful = DownloadImportFailure.isRetryUseful(failedDownload.failure)
                        val source = retry.referer?.takeIf { ProgressMath.isHttpsUrl(it) }
                        // Some failures need a different action, not the same request again.
                        // Still keep the source-page escape hatch when one exists.
                        if (retryUseful || source != null) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (retryUseful) {
                                    TextButton(
                                        onClick = { startImport(retry) },
                                        modifier = Modifier.semantics {
                                            contentDescription = context.getString(R.string.browser_retry_download_cd)
                                        },
                                    ) {
                                        Text(stringResource(R.string.browser_retry_download))
                                    }
                                }
                                // 回到触发下载的页面，便于重新登录或获取新链接。
                                if (source != null) {
                                    TextButton(
                                        onClick = {
                                            error = null
                                            lastFailedDownload = null
                                            lastCancelledDownload = null
                                            address = source
                                            webView?.loadUrl(source)
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = context.getString(R.string.browser_back_to_source_cd)
                                        },
                                    ) {
                                        Text(stringResource(R.string.browser_back_to_source))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { viewContext ->
                        // configChanges keeps this view across rotation; no retain juggling.
                        createBrowser(viewContext)
                    },
                    update = { view ->
                        webView = view
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!pageStarted && downloadJob == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.browser_start_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (browserHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.browser_recent_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(R.string.browser_recent_clear),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .semantics {
                                            contentDescription = context.getString(R.string.browser_recent_clear_cd)
                                        }
                                        .clickable(
                                            role = Role.Button,
                                            onClick = {
                                                preferences.clearBrowserHistory()
                                                browserHistory = emptyList()
                                            },
                                        ),
                                )
                            }
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                browserHistory.forEach { recentUrl ->
                                    // Host-first chips: long signed URLs collapse to a
                                    // recognizable domain + trimmed path instead of a wall
                                    // of query text. TalkBack still reads the full URL.
                                    // Long-press drops that one entry (a dead link no longer
                                    // forces clearing all 8).
                                    val display = BrowserDownloadPolicy.recentDisplay(recentUrl)
                                    @OptIn(ExperimentalFoundationApi::class)
                                    Box(
                                        modifier = Modifier
                                            .semantics {
                                                contentDescription = context.getString(
                                                    R.string.browser_recent_open_cd,
                                                    recentUrl,
                                                )
                                            }
                                            .combinedClickable(
                                                enabled = downloadJob == null,
                                                onClick = {
                                                    address = recentUrl
                                                    error = null
                                                    lastFailedDownload = null
                                                    lastCancelledDownload = null
                                                    openAddress()
                                                },
                                                onLongClick = {
                                                    preferences.removeBrowserHistory(recentUrl)
                                                    browserHistory = preferences.browserHistory().toList()
                                                },
                                                onLongClickLabel = context.getString(
                                                    R.string.browser_recent_remove_cd,
                                                    recentUrl,
                                                ),
                                            ),
                                    ) {
                                    SuggestionChip(
                                        // Click handled by the outer combinedClickable
                                        // (tap open + long-press delete share one node).
                                        // Inner chip exposes no gesture/semantics of its own
                                        // so TalkBack announces exactly one action target.
                                        // Enabled state still mirrors download state for visuals.
                                        onClick = {},
                                        enabled = downloadJob == null,
                                        label = {
                                            Column {
                                                Text(
                                                    text = display.host,
                                                    style = MaterialTheme.typography.labelLarge,
                                                )
                                                if (display.path.isNotEmpty()) {
                                                    Text(
                                                        text = display.path.take(48),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.clearAndSetSemantics {},
                                    )
                                    } // outer combinedClickable (tap open / long-press delete)
                                }
                            }
                        }
                    }
                }
                if (downloadJob != null) {
                    val eta = BrowserDownloadEta.etaFragment(
                        bytesRead,
                        totalBytes,
                        etaRateBps,
                        formatSeconds = { context.getString(R.string.browser_eta_seconds, it) },
                        formatMinutes = { context.getString(R.string.browser_eta_minutes, it) },
                    )
                    val progressLabel = if (totalBytes > 0L) {
                        val base = context.getString(
                            R.string.browser_downloading_progress,
                            ((bytesRead * 100L) / totalBytes).coerceIn(0L, 100L).toInt(),
                            Formatter.formatShortFileSize(context, bytesRead),
                            Formatter.formatShortFileSize(context, totalBytes),
                        )
                        if (eta.isNotEmpty()) "$base · $eta" else base
                    } else if (bytesRead > 0L) {
                        context.getString(
                            R.string.browser_downloading_bytes,
                            Formatter.formatShortFileSize(context, bytesRead),
                        )
                    } else {
                        downloading
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = {
                                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.width(220.dp),
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                            Text(
                                text = progressLabel,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                            )
                            val downloadingSource = lastCancelledDownload?.referer
                                ?.takeIf { ProgressMath.isHttpsUrl(it) }
                                ?.let { BrowserDownloadPolicy.recentDisplay(it).host }
                            if (downloadingSource != null) {
                                val sourceLabel = context.getString(
                                    R.string.browser_downloading_source,
                                    downloadingSource,
                                )
                                Text(
                                    text = sourceLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = { cancelDownload() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(stringResource(R.string.browser_cancel_download))
                            }
                        }
                    }
                }
            }
        }
    }
}
