package app.maoyankanshu.novel.selfuse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = AmberLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E6D3),
    onPrimaryContainer = Color(0xFF3A2208),
    inversePrimary = AmberDark,
    secondary = Color(0xFF6F5E4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE5DA),
    onSecondaryContainer = Color(0xFF2A2017),
    tertiary = Color(0xFF4E6357),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8DF),
    onTertiaryContainer = Color(0xFF0D1F16),
    background = PaperBackground,
    onBackground = InkText,
    surface = PaperBackground,
    onSurface = InkText,
    surfaceVariant = PaperContainerHigh,
    onSurfaceVariant = InkTextMuted,
    surfaceTint = AmberLight,
    inverseSurface = Color(0xFF2E2A26),
    inverseOnSurface = Color(0xFFF6F0E8),
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    surfaceBright = PaperCard,
    surfaceDim = Color(0xFFE2DCD3),
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
)

private val DarkColors = darkColorScheme(
    primary = AmberDark,
    onPrimary = Color(0xFF3F2400),
    primaryContainer = Color(0xFF4F3417),
    onPrimaryContainer = Color(0xFFFCDDBC),
    inversePrimary = AmberLight,
    secondary = Color(0xFFD8C4B0),
    onSecondary = Color(0xFF3B2E22),
    secondaryContainer = Color(0xFF39312A),
    onSecondaryContainer = Color(0xFFF0E0CF),
    tertiary = Color(0xFFB3CCBD),
    onTertiary = Color(0xFF1F352A),
    tertiaryContainer = Color(0xFF354B3F),
    onTertiaryContainer = Color(0xFFCFE9D9),
    background = NightBackground,
    onBackground = NightText,
    surface = NightBackground,
    onSurface = NightText,
    surfaceVariant = NightContainerHigh,
    onSurfaceVariant = NightTextMuted,
    surfaceTint = AmberDark,
    inverseSurface = Color(0xFFECE5DC),
    inverseOnSurface = Color(0xFF2E2A26),
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    surfaceBright = Color(0xFF3B3632),
    surfaceDim = NightBackground,
    // Cards sit one step *above* the page in dark mode, so “lowest” is lifted too.
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = NightContainerLow,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightContainerHigh,
    surfaceContainerHighest = NightContainerHighest,
)

private val BiqugeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
    /** Match status/nav bar icon tint to the in-app night mode (not the system theme). */
    syncSystemBars: Boolean = true,
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

    val view = LocalView.current
    if (syncSystemBars && !view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BiqugeTypography,
        shapes = BiqugeShapes,
        content = content,
    )
}

/** Card surface that lifts off the paper background (white by day, raised by night). */
val cardContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

/**
 * Shared top bar colors: blends into the page, gains a quiet tonal fill once content
 * scrolls underneath. Replaces the old saturated yellow bar across every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
)

/** Borderless chips: soft tonal fill, ink fill when selected. Pair with `border = null`. */
@Composable
fun appFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
)
