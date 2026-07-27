package app.maoyankanshu.novel.selfuse.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose smoke test (API 35/36 needs Espresso 3.7+).
 * Run on a connected emulator: `./gradlew :app:connectedDebugAndroidTest`
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BiqugeThemeSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun biqugeTheme_rendersProductName_forTalkBackAndDisplay() {
        composeRule.setContent {
            BiqugeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "阅笺",
                        modifier = Modifier.semantics {
                            contentDescription = "应用名称 阅笺"
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithText("阅笺").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("应用名称 阅笺").assertExists()
    }
}
