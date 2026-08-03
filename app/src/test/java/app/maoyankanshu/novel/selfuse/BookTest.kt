package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookTest {

    @Test
    fun fiveArgConstructor_coverPathIsNull() {
        val book = Book("id", "t", "a", "text", 0)
        assertNull(book.coverPath)
    }

    @Test
    fun sixArgConstructor_preservesCoverPath() {
        val book = Book("id", "t", "a", "text", 10, "/tmp/x.cover")
        assertEquals("/tmp/x.cover", book.coverPath)
        assertEquals(10, book.position)
        assertEquals(4, book.bodyLength())
    }

    @Test
    fun listRow_emptyTextUsesExplicitTextLength() {
        val row = Book("id", "t", "a", "", 500, null, 1_500_000)
        assertEquals("", row.text)
        assertEquals(1_500_000, row.textLength)
        assertEquals(1_500_000, row.bodyLength())
        assertEquals("已读 50%", row.progressLabel())
    }

    @Test
    fun testProgressLabelUnstarted() {
        val book0 = Book("id-1", "测试书名", "测试作者", "文本", 0)
        assertEquals("未开始", book0.progressLabel())

        val bookNeg = Book("id-2", "测试书名", "测试作者", "文本", -10)
        assertEquals("未开始", bookNeg.progressLabel())
    }

    @Test
    fun testProgressLabelInProgress() {
        val book50Percent = Book("id-3", "测试书名", "测试作者", "文本", 500)
        assertEquals("已读 50%", book50Percent.progressLabel())

        val book3Percent = Book("id-4", "测试书名", "测试作者", "文本", 25)
        assertEquals("已读 3%", book3Percent.progressLabel())
    }

    @Test
    fun testProgressLabelFinished() {
        val book100Percent = Book("id-5", "测试书名", "测试作者", "文本", 1000)
        assertEquals("已读完", book100Percent.progressLabel())

        val bookOver100 = Book("id-6", "测试书名", "测试作者", "文本", 1200)
        assertEquals("已读完", bookOver100.progressLabel())
    }
}
