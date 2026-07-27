package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.maoyankanshu.novel.selfuse.ui.BiqugeApp
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme

/**
 * Main shell rewritten with Jetpack Compose + Material 3.
 *
 * Shell light/dark follows [ReaderPreferences.nightMode] (same toggle as “夜间阅读”).
 * [ReaderActivity] keeps its own Java-side themes and is not rewritten here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var preferencesVersion by remember { mutableIntStateOf(0) }
            val darkTheme = remember(preferencesVersion) {
                ReaderPreferences.get(this).nightMode()
            }
            BiqugeTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BiqugeApp(
                        onPreferencesChanged = { preferencesVersion++ },
                    )
                }
            }
        }
    }
}
