package app.maoyankanshu.novel.selfuse

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestSharedPreferences : SharedPreferences {
        val map = HashMap<String, String>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = 0
        override fun getLong(key: String?, defValue: Long): Long = 0L
        override fun getFloat(key: String?, defValue: Float): Float = 0f
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = false
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = TestEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val prefs: TestSharedPreferences) : SharedPreferences.Editor {
            private val temp = HashMap<String, String>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null && value != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { prefs.map.remove(it) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                prefs.map.clear()
                return this
            }
            override fun commit(): Boolean {
                prefs.map.putAll(temp)
                return true
            }
            override fun apply() {
                prefs.map.putAll(temp)
            }
        }
    }

    private fun createStore(): LibraryStore {
        val prefs = TestSharedPreferences()
        val dir = tempFolder.newFolder()
        return LibraryStore(prefs, dir, "App", "Welcome Title", "Welcome Body")
    }

    @Test
    fun testExportAndImportValidZip() {
        val store1 = createStore()
        store1.add("三体", "刘慈欣", "地球往事第一部")
        val booksBefore = store1.books()
        assertEquals(2, booksBefore.size) // Welcome book + added book

        val baos = ByteArrayOutputStream()
        store1.exportTo(baos)
        val zipBytes = baos.toByteArray()

        val store2 = createStore()
        val importedCount = store2.importFrom(ByteArrayInputStream(zipBytes))
        assertEquals(2, importedCount)
        assertEquals(3, store2.books().size) // Welcome book (seed) + 2 imported
    }

    @Test
    fun fourFieldManifestRows_stillLoadWithoutCover() {
        val store = createStore()
        store.add("无封面", "作者", "正文")
        val book = store.books().first { it.title == "无封面" }
        assertNull(book.coverPath)
    }

    @Test
    fun addWithCover_persistsAndRemoveDeletesCover() {
        val store = createStore()
        val png = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        store.add("有封面", "作者", "正文", png)
        val book = store.books().first { it.title == "有封面" }
        assertNotNull(book.coverPath)
        assertTrue(java.io.File(book.coverPath!!).exists())
        val id = book.id
        val coversDir = java.io.File(book.coverPath!!).parentFile
        store.remove(id)
        assertTrue(store.books().none { it.id == id })
        // Cover file removed with book
        assertFalse(java.io.File(coversDir, "$id.cover").exists())
    }

    @Test
    fun exportImport_roundTripsCover() {
        val store1 = createStore()
        val png = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        store1.add("封面书", "甲", "乙", png)
        val baos = ByteArrayOutputStream()
        store1.exportTo(baos)

        val store2 = createStore()
        store2.importFrom(ByteArrayInputStream(baos.toByteArray()))
        val restored = store2.books().first { it.title == "封面书" }
        assertNotNull(restored.coverPath)
        assertTrue(java.io.File(restored.coverPath!!).exists())
        assertTrue(java.io.File(restored.coverPath!!).length() > 0)
    }

    @Test
    fun addOversizedCover_ignoredGracefully() {
        val store = createStore()
        val huge = ByteArray(LibraryStore.MAX_COVER_BYTES + 10) { 1 }
        store.add("超大封面", "作者", "正文", huge)
        val book = store.books().first { it.title == "超大封面" }
        assertNull(book.coverPath)
    }

    @Test
    fun testImportMissingManifestThrowsIOException() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("books/1.txt"))
            zip.write("some text".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val store = createStore()
        assertThrows(IOException::class.java) {
            store.importFrom(ByteArrayInputStream(baos.toByteArray()))
        }
    }

    @Test
    fun testImportSingleEntryExceeds32MbThrowsIllegalArgumentException() {
        val store = createStore()
        val zipStream = createOversizedSingleEntryZipStream(32 * 1024 * 1024 + 100)
        assertThrows(IllegalArgumentException::class.java) {
            store.importFrom(zipStream)
        }
    }

    @Test
    fun testImportTotalUncompressedSizeExceeds128MbThrowsIllegalArgumentException() {
        val store = createStore()
        // Create 5 entries, each 28 MiB -> total 140 MiB (> 128 MiB)
        val zipStream = createMultiEntryZipStream(count = 5, sizePerEntry = 28 * 1024 * 1024)
        assertThrows(IllegalArgumentException::class.java) {
            store.importFrom(zipStream)
        }
    }

    @Test
    fun testCorruptZipStreamThrowsIOException() {
        val store = createStore()
        val garbageBytes = "This is not a zip file at all".toByteArray(StandardCharsets.UTF_8)
        assertThrows(IOException::class.java) {
            store.importFrom(ByteArrayInputStream(garbageBytes))
        }
    }

    @Test
    fun testImportCorruptManifestRowIsSkippedAndValidRowIsImported() {
        val store = createStore()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("library.txt"))
            // Row 1: invalid pipe format; Row 2: invalid base64; Row 3: valid
            val validTitleEnc = java.util.Base64.getEncoder().encodeToString("有效书名".toByteArray(StandardCharsets.UTF_8))
            val validAuthorEnc = java.util.Base64.getEncoder().encodeToString("有效作者".toByteArray(StandardCharsets.UTF_8))
            val manifestContent = "corrupt_row_without_pipes\n" +
                "id1|invalid_base64_!@#\$|author|100\n" +
                "id2|$validTitleEnc|$validAuthorEnc|9999\n"
            zip.write(manifestContent.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("books/id2.txt"))
            zip.write("正文内容".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val importedCount = store.importFrom(ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, importedCount)
        val books = store.books()
        val importedBook = books.firstOrNull { it.title == "有效书名" }
        assertNotNull(importedBook)
        assertEquals(1000, importedBook?.position) // Position clamped to 1000
    }

    @Test
    fun testImportZipWithMissingBookTextIsSkipped() {
        val store = createStore()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("library.txt"))
            val titleEnc = java.util.Base64.getEncoder().encodeToString("测试".toByteArray(StandardCharsets.UTF_8))
            val authorEnc = java.util.Base64.getEncoder().encodeToString("作者".toByteArray(StandardCharsets.UTF_8))
            zip.write("missing_id|$titleEnc|$authorEnc|500\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            // Do not put books/missing_id.txt in zip
        }

        val importedCount = store.importFrom(ByteArrayInputStream(baos.toByteArray()))
        assertEquals(0, importedCount)
    }

    @Test
    fun testLibraryStoreMoveToTopAndRemoveOperations() {
        val store = createStore()
        store.add("书一", "作者一", "正文一")
        store.add("书二", "作者二", "正文二")

        val books = store.books()
        val bookTwo = books.first { it.title == "书二" }
        store.moveToTop(bookTwo.id)

        val updatedBooks = store.books()
        assertEquals(bookTwo.id, updatedBooks[0].id)

        store.remove(bookTwo.id)
        val finalBooks = store.books()
        assertEquals(books.size - 1, finalBooks.size)
        org.junit.Assert.assertTrue(finalBooks.none { it.id == bookTwo.id })
    }

    @Test
    fun recordById_readsMetadataWithoutLoadingOtherBooksText() {
        val store = createStore()
        store.add("目标书", "作者甲", "只有这一本需要被打开")
        store.add("其他大书", "作者乙", "x".repeat(50_000))
        val target = store.books().first { it.title == "目标书" }

        val record = store.recordById(target.id)
        assertNotNull(record)
        assertEquals(target.id, record!!.id)
        assertEquals("目标书", record.title)
        assertEquals("作者甲", record.author)
        assertEquals(0, record.position)

        // Missing id
        assertNull(store.recordById("no-such-id"))
        assertNull(store.recordById(null))
    }

    @Test
    fun readBookBytes_andById_onlyTouchTargetFile() {
        val store = createStore()
        val body = "大主宰正文抽样"
        store.add("单本", "天蚕土豆", body)
        val id = store.books().first { it.title == "单本" }.id

        val bytes = store.readBookBytes(id)
        assertNotNull(bytes)
        assertEquals(body, String(bytes!!, StandardCharsets.UTF_8))

        val book = store.byId(id)
        assertNotNull(book)
        assertEquals(body, book!!.text)
        assertEquals("单本", book.title)
    }

    @Test
    fun savePosition_doesNotRequireRereadingBookText() {
        val store = createStore()
        store.add("进度书", "作者", "正文内容足够用来存盘")
        val id = store.books().first { it.title == "进度书" }.id
        store.savePosition(id, 750)
        val record = store.recordById(id)
        assertNotNull(record)
        assertEquals(750, record!!.position)
        val book = store.byId(id)
        assertEquals(750, book!!.position)
        assertEquals("正文内容足够用来存盘", book.text)
    }

    @Test
    fun booksForListing_metadataOnly_noFullBodyPayload() {
        val store = createStore()
        // Multi‑MB class body must not appear in list-row Book.text.
        val largeBody = "章".repeat(200_000)
        store.add("大书", "作者", largeBody)
        store.add("小书", "乙", "短")
        store.savePosition(store.books().first { it.title == "大书" }.id, 420)

        store.fullBodyDecodeCount = 0
        val listing = store.booksForListing()
        val big = listing.first { it.title == "大书" }
        val small = listing.first { it.title == "小书" }

        assertEquals("", big.text)
        assertEquals("", small.text)
        assertEquals(largeBody.length, big.bodyLength())
        assertEquals(1, small.bodyLength())
        assertEquals(420, big.position)
        assertEquals("大书", big.title)
        // Listing must not full-decode any book body (char cache / stream count only).
        assertEquals(0, store.fullBodyDecodeCount)

        // Full path still loads body for reading/export.
        val full = store.byId(big.id)
        assertNotNull(full)
        assertEquals(largeBody.length, full!!.text.length)
        assertEquals(largeBody.length, full.bodyLength())
        assertTrue(store.fullBodyDecodeCount >= 1)
    }

    @Test
    fun seedMigrations_doNotFullDecodeUserMultiMbBooks() {
        val store = createStore()
        val largeBody = "文".repeat(250_000)
        store.add("用户大书", "用户作者", largeBody)
        // Seed-like row with legacy author (prefs only) + short body file already via add.
        store.add("使用说明", "笔趣阁（自用）", "旧的一页说明没有标记")

        val largeId = store.books().first { it.title == "用户大书" }.id
        store.fullBodyDecodeCount = 0

        // Production get() runs these migrations before booksForListing on main shell.
        store.migrateLegacySeedAuthor("阅笺", "使用说明")
        store.migrateWelcomeSeedBody(
            "阅笺",
            "使用说明",
            "【阅笺使用说明】\n\n加长的欢迎正文。",
        )
        store.fullBodyDecodeCount = 0
        val listing = store.booksForListing()
        assertEquals(0, store.fullBodyDecodeCount)

        val user = listing.first { it.title == "用户大书" }
        assertEquals("", user.text)
        assertEquals(largeBody.length, user.bodyLength())
        assertEquals(largeId, user.id)

        // Seed author upgraded without loading user multi‑MB body during migration listing.
        val seed = store.recordById(store.booksForListing().first { it.title == "使用说明" }.id)
        assertNotNull(seed)
        assertEquals("阅笺", seed!!.author)
        val seedBody = store.byId(seed.id)!!.text
        assertTrue(seedBody.contains("【阅笺使用说明】"))
    }

    @Test
    fun countUtf8Chars_matchesStringLength() {
        val dir = tempFolder.newFolder()
        val file = java.io.File(dir, "sample.txt")
        val body = "Hello中文😀"
        file.writeText(body, StandardCharsets.UTF_8)
        assertEquals(body.length, LibraryStore.countUtf8Chars(file))
    }

    private fun createOversizedSingleEntryZipStream(entrySize: Int): InputStream {
        val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("library.txt"))
            zip.write("id1|title|author|0\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("books/id1.txt"))
            var written = 0
            while (written < entrySize) {
                val toWrite = Math.min(chunk.size, entrySize - written)
                zip.write(chunk, 0, toWrite)
                written += toWrite
            }
            zip.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createMultiEntryZipStream(count: Int, sizePerEntry: Int): InputStream {
        val chunk = ByteArray(64 * 1024) { 'b'.code.toByte() }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            val manifestBuilder = StringBuilder()
            for (i in 1..count) {
                manifestBuilder.append("id$i|title$i|author$i|0\n")
            }
            zip.putNextEntry(ZipEntry("library.txt"))
            zip.write(manifestBuilder.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            for (i in 1..count) {
                zip.putNextEntry(ZipEntry("books/id$i.txt"))
                var written = 0
                while (written < sizePerEntry) {
                    val toWrite = Math.min(chunk.size, sizePerEntry - written)
                    zip.write(chunk, 0, toWrite)
                    written += toWrite
                }
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }
}
