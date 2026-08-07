package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressiveTextOpen
import app.maoyankanshu.novel.selfuse.ui.reader.ReaderScreen
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Primary Jetpack Compose reader. Public [EXTRA_ID] stays `"book_id"` so all
 * existing Intents continue to work.
 *
 * Large TXT open path:
 * 1. Load metadata + file bytes (target book only).
 * 2. Decode a bounded window around saved progress → first readable body (秒开).
 * 3. Decode the full string in the background and swap in without re-reading the shelf.
 */
class ReaderActivity : ComponentActivity() {

    private var openState by mutableStateOf<OpenState>(OpenState.Loading)

    /**
     * Optional volume-key page-turn hook set by [ReaderScreen] while composed.
     * Returns true when the key was consumed (suppresses system volume UI).
     */
    var volumePageTurnHandler: ((keyCode: Int) -> Boolean)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handled = volumePageTurnHandler?.invoke(keyCode) == true
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_ID)
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val state = openState) {
                        OpenState.Loading -> LoadingShell()
                        OpenState.Missing -> {
                            // finish() is requested from the loader; keep a blank frame.
                        }
                        is OpenState.Ready -> {
                            ReaderScreen(
                                book = state.book,
                                activity = this@ReaderActivity,
                                onClose = { finish() },
                                textFullyLoaded = state.textFullyLoaded,
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            openBook(bookId)
        }
    }

    private suspend fun openBook(bookId: String?) {
        if (bookId.isNullOrEmpty()) {
            openState = OpenState.Missing
            if (!isFinishing && !isDestroyed) finish()
            return
        }
        val store = LibraryStore.getForReading(this@ReaderActivity)
        val prepared = withContext(Dispatchers.IO) {
            val record = store.recordById(bookId) ?: return@withContext null
            val bytes = store.readBookBytes(bookId) ?: return@withContext null
            PreparedOpen(record, bytes)
        }
        if (prepared == null || isFinishing || isDestroyed) {
            openState = OpenState.Missing
            if (!isFinishing && !isDestroyed) finish()
            return
        }
        val record = prepared.record
        val bytes = prepared.bytes

        if (ProgressiveTextOpen.shouldOpenProgressively(bytes.size.toLong())) {
            // Bounded window decode first — O(window), not O(file) string alloc.
            val windowText = withContext(Dispatchers.Default) {
                ProgressiveTextOpen.firstWindowText(bytes, record.position)
            }
            if (isFinishing || isDestroyed) return
            openState = OpenState.Ready(
                book = Book(
                    record.id,
                    record.title,
                    record.author,
                    windowText,
                    record.position,
                    record.coverPath,
                ),
                textFullyLoaded = false,
            )
            val fullText = withContext(Dispatchers.Default) {
                ProgressiveTextOpen.decodeFullText(bytes)
            }
            if (isFinishing || isDestroyed) return
            // Prefer latest disk progress (debounce may have written during the window).
            val latestPosition = withContext(Dispatchers.IO) {
                store.recordById(bookId)?.position ?: record.position
            }
            if (isFinishing || isDestroyed) return
            openState = OpenState.Ready(
                book = Book(
                    record.id,
                    record.title,
                    record.author,
                    fullText,
                    latestPosition,
                    record.coverPath,
                ),
                textFullyLoaded = true,
            )
        } else {
            val fullText = withContext(Dispatchers.Default) {
                ProgressiveTextOpen.decodeFullText(bytes)
            }
            if (isFinishing || isDestroyed) return
            openState = OpenState.Ready(
                book = Book(
                    record.id,
                    record.title,
                    record.author,
                    fullText,
                    record.position,
                    record.coverPath,
                ),
                textFullyLoaded = true,
            )
        }
    }

    private sealed class OpenState {
        data object Loading : OpenState()
        data object Missing : OpenState()
        data class Ready(
            val book: Book,
            val textFullyLoaded: Boolean,
        ) : OpenState()
    }

    private data class PreparedOpen(
        val record: LibraryStore.BookRecord,
        val bytes: ByteArray,
    )

    companion object {
        /** Must match the historical Java constant so existing callers keep working. */
        const val EXTRA_ID: String = "book_id"
    }
}

@androidx.compose.runtime.Composable
private fun LoadingShell() {
    val context = LocalContext.current
    val label = stringResource(R.string.reader_open_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = context.getString(R.string.reader_open_loading)
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
