package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.maoyankanshu.novel.selfuse.ui.reader.ReaderScreen
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme

/**
 * Primary Jetpack Compose reader. Public [EXTRA_ID] stays `"book_id"` so all
 * existing Intents continue to work.
 */
class ReaderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_ID)
        // Render a lightweight shell first. Loading a large imported TXT synchronously here
        // blocks the main thread before Compose can draw, which appears as a black screen.
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text("正在打开…")
                    }
                }
            }
        }

        lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                bookId?.let { LibraryStore.getForReading(this@ReaderActivity).byId(it) }
            }
            if (book == null || isFinishing || isDestroyed) {
                finish()
                return@launch
            }
            setContent {
                // Reader uses its own paper/night/eye-care palette inside ReaderScreen;
                // shell Material theme is only a host for dialogs/sheets.
                BiqugeTheme(darkTheme = ReaderPreferences.get(this@ReaderActivity).nightMode()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ReaderScreen(
                            book = book,
                            activity = this@ReaderActivity,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Must match the historical Java constant so existing callers keep working. */
        const val EXTRA_ID: String = "book_id"
    }
}
