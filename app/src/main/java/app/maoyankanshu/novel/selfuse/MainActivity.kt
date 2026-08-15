package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.maoyankanshu.novel.selfuse.ui.BiqugeApp
import app.maoyankanshu.novel.selfuse.ui.screens.PrivacyConsentGate
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme

/**
 * Main shell rewritten with Jetpack Compose + Material 3.
 *
 * Cold start uses AndroidX SplashScreen (Android 12+ system splash + back-port).
 * [installSplashScreen] must run before [setContent]; theme then becomes
 * [Theme.BiqugeStudio] via postSplashScreenTheme. Compose [ReaderActivity] keeps
 * the normal app theme without a second splash.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val consentStore = remember { PrivacyConsentStore.get(this@MainActivity) }
            var privacyAccepted by remember {
                mutableStateOf(consentStore.hasAcceptedCurrentPolicy())
            }
            var darkTheme by remember {
                mutableStateOf(
                    if (privacyAccepted) ReaderPreferences.get(this@MainActivity).nightMode() else false,
                )
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, privacyAccepted) {
                val observer = LifecycleEventObserver { _, event ->
                    if (privacyAccepted && event == Lifecycle.Event.ON_RESUME) {
                        darkTheme = ReaderPreferences.get(this@MainActivity).nightMode()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            BiqugeTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (privacyAccepted) {
                        BiqugeApp(
                            onDarkThemeChanged = { enabled -> darkTheme = enabled },
                        )
                    } else {
                        PrivacyConsentGate(
                            onAccept = {
                                consentStore.acceptCurrentPolicy()
                                darkTheme = ReaderPreferences.get(this@MainActivity).nightMode()
                                privacyAccepted = true
                            },
                            onDecline = {
                                finishAndRemoveTask()
                            },
                        )
                    }
                }
            }
        }
    }
}
