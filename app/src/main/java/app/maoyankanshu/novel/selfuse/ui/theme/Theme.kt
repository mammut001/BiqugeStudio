package app.maoyankanshu.novel.selfuse.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0A3),
    onPrimaryContainer = Color(0xFF3D2A00),
    secondary = BrandOrangeDark,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFF79747E),
)

private val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color(0xFF3D2A00),
    primaryContainer = Color(0xFF5C4100),
    onPrimaryContainer = Color(0xFFFFE0A3),
    secondary = BrandOrange,
    onSecondary = Color(0xFF3D2A00),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF938F99),
)

/**
 * Material 3 theme for the Compose shell.
 *
 * [darkTheme] is driven by [app.maoyankanshu.novel.selfuse.ReaderPreferences.nightMode]
 * from MainActivity so “夜间阅读” updates the shell immediately. ReaderActivity is separate.
 */
@Composable
fun BiqugeTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BiqugeTypography,
        content = content,
    )
}
