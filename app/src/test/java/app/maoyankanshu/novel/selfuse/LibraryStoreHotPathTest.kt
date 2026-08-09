package app.maoyankanshu.novel.selfuse

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/** Regression coverage for library operations that must not materialize every saved book body. */
class LibraryStoreHotPathTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestSharedPreferences : SharedPreferences {
        private val values = HashMap<String, String>()

        override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val prefs: TestSharedPreferences) : SharedPreferences.Editor {
            private val pending = HashMap<String, String?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                pending.clear()
                return this
            }
            override fun commit(): Boolean {
                applyChanges()
                return true
            }
            override fun apply() = applyChanges()

            private fun applyChanges() {
                if (clearRequested) prefs.values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) prefs.values.remove(key) else prefs.values[key] = value
                }
                pending.clear()
                clearRequested = false
            }
        }
    }

    private fun createStore(): LibraryStore = LibraryStore(
        TestSharedPreferences(),
        tempFolder.newFolder(),
        "App",
        "Welcome",
        "Welcome body",
    )

    @Test
    fun add_doesNotDecodeExistingBookBodies() {
        val store = createStore()
        store.add("Existing large book", "Author", "甲".repeat(200_000))
        store.fullBodyDecodeCount = 0

        store.add("New book", "Author", "new body")

        assertEquals(0, store.fullBodyDecodeCount)
        assertTrue(store.booksForListing().any { it.title == "New book" })
    }

    @Test
    fun updateMetadata_doesNotDecodeOrRewriteBody() {
        val store = createStore()
        val body = "正文".repeat(20_000)
        store.add("Before", "Author", body)
        val id = store.booksForListing().first { it.title == "Before" }.id
        store.fullBodyDecodeCount = 0

        store.updateMetadata(id, "After", "New author")

        assertEquals(0, store.fullBodyDecodeCount)
        val record = store.recordById(id)!!
        assertEquals("After", record.title)
        assertEquals("New author", record.author)
        assertEquals(body, String(store.readBookBytes(id)!!, StandardCharsets.UTF_8))
    }

    @Test
    fun exports_useStoredBytesWithoutFullBodyStringDecode() {
        val store = createStore()
        val body = "导出正文😀".repeat(5_000)
        store.add("Export", "Author", body)
        val id = store.booksForListing().first { it.title == "Export" }.id

        store.fullBodyDecodeCount = 0
        val single = ByteArrayOutputStream()
        store.exportBook(id, single)
        assertEquals(0, store.fullBodyDecodeCount)
        assertEquals(body, single.toString(StandardCharsets.UTF_8.name()))

        val backup = ByteArrayOutputStream()
        store.exportTo(backup)
        assertEquals(0, store.fullBodyDecodeCount)

        // ZIP compression may legitimately make a repetitive text backup smaller than the raw
        // TXT. Validate the archived payload itself instead of comparing compressed byte sizes.
        val archivedBody = ZipInputStream(ByteArrayInputStream(backup.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            var extracted: String? = null
            while (entry != null) {
                if (entry.name == "books/$id.txt") {
                    extracted = zip.readBytes().toString(StandardCharsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            extracted
        }
        assertEquals(body, archivedBody)
    }

    @Test
    fun restore_doesNotDecodeExistingLibraryBodies() {
        val source = createStore()
        source.add("From backup", "Remote author", "backup body")
        val backup = ByteArrayOutputStream().also(source::exportTo).toByteArray()

        val target = createStore()
        target.add("Existing large book", "Local author", "乙".repeat(200_000))
        target.fullBodyDecodeCount = 0

        val imported = target.importFrom(ByteArrayInputStream(backup))

        assertTrue(imported >= 1)
        assertEquals(0, target.fullBodyDecodeCount)
        assertTrue(target.booksForListing().any { it.title == "From backup" })
    }

    @Test
    fun restore_propagatesBookWriteFailure() {
        val source = createStore()
        source.add("From backup", "Remote author", "backup body")
        val backup = ByteArrayOutputStream().also(source::exportTo).toByteArray()

        val blockedFilesDir = tempFolder.newFolder()
        assertTrue(File(blockedFilesDir, "books").createNewFile())
        val target = LibraryStore(
            TestSharedPreferences(),
            blockedFilesDir,
            "App",
            "Welcome",
            "Welcome body",
        )

        assertThrows(IOException::class.java) {
            target.importFrom(ByteArrayInputStream(backup))
        }
    }
}
