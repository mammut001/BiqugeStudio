package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
        val book = bookId?.let { LibraryStore.get(this).byId(it) }
        if (book == null) {
            finish()
            return
        }

        setContent {
            // Reader uses its own paper/night/eye-care palette inside ReaderScreen;
            // shell Material theme is only a host for dialogs/sheets.
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
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

    companion object {
        /** Must match the historical Java constant so existing callers keep working. */
        const val EXTRA_ID: String = "book_id"
    }
}
