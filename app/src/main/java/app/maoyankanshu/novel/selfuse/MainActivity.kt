package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.maoyankanshu.novel.selfuse.ui.BiqugeApp
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme

/**
 * Main shell rewritten with Jetpack Compose + Material 3.
 *
 * Secondary flows (reader, search/import, detail, remote/web import) stay as
 * existing Java Activities and are opened via Intent from the Compose UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BiqugeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BiqugeApp()
                }
            }
        }
    }
}
