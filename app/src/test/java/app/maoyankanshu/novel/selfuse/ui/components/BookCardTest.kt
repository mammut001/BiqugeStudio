package app.maoyankanshu.novel.selfuse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCardTest {

    @Test
    fun coverPaletteIndex_isAlwaysInBounds() {
        val seeds = listOf(
            "",
            "普通书名",
            "book-id-123",
            "polygenelubricants", // Java/Kotlin hashCode == Int.MIN_VALUE
        )
        seeds.forEach { seed ->
            val index = coverPaletteIndex(seed, 8)
            assertTrue("$seed -> $index", index in 0 until 8)
        }
    }

    @Test
    fun coverPaletteIndex_handlesIntMinHashWithoutNegativeIndex() {
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        assertEquals(0, coverPaletteIndex("polygenelubricants", 8))
    }
}
