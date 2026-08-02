package app.maoyankanshu.novel.selfuse.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.maoyankanshu.novel.selfuse.ReaderPreferences

data class ReaderPalette(
    val background: Color,
    val onBackground: Color,
    val muted: Color,
    val bar: Color,
    val onBar: Color,
)

/**
 * Paper / theme chips for the appearance sheet.
 * Ids match [ReaderPreferences] theme constants (stable for persistence).
 */
data class PaperThemeOption(
    val id: Int,
    /** Label string resource is resolved in Compose; pure id list for tests. */
    val swatch: Color,
    val ink: Color,
)

/** All selectable paper themes in stable UI order. */
fun paperThemeOptions(): List<PaperThemeOption> = listOf(
    PaperThemeOption(
        id = ReaderPreferences.THEME_PAPER,
        swatch = Color(0xFFFAF7F0),
        ink = Color(0xFF372D23),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_WHITE,
        swatch = Color(0xFFFFFFFF),
        ink = Color(0xFF222222),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_PARCHMENT,
        swatch = Color(0xFFF3E5C8),
        ink = Color(0xFF3C2F1E),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_EYE_CARE,
        swatch = Color(0xFFECE8C9),
        ink = Color(0xFF372D23),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_GREEN,
        swatch = Color(0xFFC7EDCC),
        ink = Color(0xFF1E3A24),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_PINK,
        swatch = Color(0xFFF8E8EE),
        ink = Color(0xFF3A2430),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_GRAY,
        swatch = Color(0xFFE8E8E8),
        ink = Color(0xFF2A2A2A),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_NIGHT,
        swatch = Color(0xFF232323),
        ink = Color(0xFFE1E1E1),
    ),
    PaperThemeOption(
        id = ReaderPreferences.THEME_SOFT_NIGHT,
        swatch = Color(0xFF1A2332),
        ink = Color(0xFFD0D8E8),
    ),
)

fun readerPalette(theme: Int): ReaderPalette = when (theme) {
    ReaderPreferences.THEME_NIGHT -> ReaderPalette(
        background = Color(0xFF232323),
        onBackground = Color(0xFFE1E1E1),
        muted = Color(0xFF9B9B9B),
        bar = Color(0xFF262626),
        onBar = Color(0xFFDDDDDD),
    )
    ReaderPreferences.THEME_SOFT_NIGHT -> ReaderPalette(
        background = Color(0xFF1A2332),
        onBackground = Color(0xFFD0D8E8),
        muted = Color(0xFF8A96A8),
        bar = Color(0xFF141C28),
        onBar = Color(0xFFE0E6F0),
    )
    ReaderPreferences.THEME_EYE_CARE -> ReaderPalette(
        background = Color(0xFFECE8C9),
        onBackground = Color(0xFF372D23),
        muted = Color(0xFF6B6258),
        bar = Color(0xFF3A342C),
        onBar = Color(0xFFF5F0E6),
    )
    ReaderPreferences.THEME_WHITE -> ReaderPalette(
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF222222),
        muted = Color(0xFF757575),
        bar = Color(0xFF2A2A2A),
        onBar = Color(0xFFEEEEEE),
    )
    ReaderPreferences.THEME_PARCHMENT -> ReaderPalette(
        background = Color(0xFFF3E5C8),
        onBackground = Color(0xFF3C2F1E),
        muted = Color(0xFF7A6A52),
        bar = Color(0xFF3A3024),
        onBar = Color(0xFFF5EBDA),
    )
    ReaderPreferences.THEME_GREEN -> ReaderPalette(
        background = Color(0xFFC7EDCC),
        onBackground = Color(0xFF1E3A24),
        muted = Color(0xFF4A6B52),
        bar = Color(0xFF1E3324),
        onBar = Color(0xFFE8F5EA),
    )
    ReaderPreferences.THEME_PINK -> ReaderPalette(
        background = Color(0xFFF8E8EE),
        onBackground = Color(0xFF3A2430),
        muted = Color(0xFF7A5A68),
        bar = Color(0xFF3A2430),
        onBar = Color(0xFFFCEEF3),
    )
    ReaderPreferences.THEME_GRAY -> ReaderPalette(
        background = Color(0xFFE8E8E8),
        onBackground = Color(0xFF2A2A2A),
        muted = Color(0xFF6A6A6A),
        bar = Color(0xFF2C2C2C),
        onBar = Color(0xFFEDEDED),
    )
    else -> ReaderPalette(
        // THEME_PAPER and unknown → warm cream paper
        background = Color(0xFFFAF7F0),
        onBackground = Color(0xFF372D23),
        muted = Color(0xFF757575),
        bar = Color(0xFF262626),
        onBar = Color(0xFFDDDDDD),
    )
}

/** Resolve body typeface from built-in font-family id (not custom file). */
fun readerFontFamily(fontFamilyId: Int): FontFamily = when (fontFamilyId) {
    ReaderPreferences.FONT_SANS -> FontFamily.SansSerif
    ReaderPreferences.FONT_DEFAULT -> FontFamily.Default
    else -> FontFamily.Serif
}

/** Clamp theme id into the supported set (unknown → paper). */
fun clampReaderTheme(theme: Int): Int =
    if (theme in ReaderPreferences.THEME_MIN..ReaderPreferences.THEME_MAX) {
        theme
    } else {
        ReaderPreferences.THEME_PAPER
    }

/** Clamp font-family id including custom. */
fun clampFontFamily(id: Int): Int =
    id.coerceIn(ReaderPreferences.FONT_SERIF, ReaderPreferences.FONT_CUSTOM)
