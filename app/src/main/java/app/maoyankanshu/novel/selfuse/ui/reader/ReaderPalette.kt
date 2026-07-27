package app.maoyankanshu.novel.selfuse.ui.reader

import androidx.compose.ui.graphics.Color
import app.maoyankanshu.novel.selfuse.ReaderPreferences

data class ReaderPalette(
    val background: Color,
    val onBackground: Color,
    val muted: Color,
    val bar: Color,
    val onBar: Color,
)

fun readerPalette(theme: Int): ReaderPalette = when (theme) {
    ReaderPreferences.THEME_NIGHT -> ReaderPalette(
        background = Color(0xFF232323),
        onBackground = Color(0xFFE1E1E1),
        muted = Color(0xFF9B9B9B),
        bar = Color(0xFF262626),
        onBar = Color(0xFFDDDDDD),
    )
    ReaderPreferences.THEME_EYE_CARE -> ReaderPalette(
        background = Color(0xFFECE8C9),
        onBackground = Color(0xFF372D23),
        muted = Color(0xFF6B6258),
        bar = Color(0xFF3A342C),
        onBar = Color(0xFFF5F0E6),
    )
    else -> ReaderPalette(
        background = Color(0xFFFAF7F0),
        onBackground = Color(0xFF372D23),
        muted = Color(0xFF757575),
        bar = Color(0xFF262626),
        onBar = Color(0xFFDDDDDD),
    )
}
