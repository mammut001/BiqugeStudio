package app.maoyankanshu.novel.selfuse

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkStoreTest {
    private class TestPreferences : SharedPreferences {
        val values = HashMap<String, String>()
        override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val target: TestPreferences) : SharedPreferences.Editor {
            private val writes = HashMap<String, String?>()
            override fun putString(key: String?, value: String?) = apply { if (key != null) writes[key] = value }
            override fun remove(key: String?) = apply { if (key != null) writes[key] = null }
            override fun clear() = apply { target.values.clear(); writes.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { writes.forEach { (key, value) -> if (value == null) target.values.remove(key) else target.values[key] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this
        }
    }

    @Test
    fun clearRemovesOnlyDeletedBooksBookmarks() {
        val prefs = TestPreferences()
        val store = BookmarkStore(prefs)
        store.add("deleted", 120, "第一章")
        store.add("kept", 880, "结尾")

        store.clear("deleted")

        assertTrue(store.list("deleted").isEmpty())
        assertEquals(1, store.list("kept").size)
        assertEquals("结尾", store.list("kept").first().label)
        assertFalse(prefs.contains("deleted"))
        assertTrue(prefs.contains("kept"))
    }

    @Test
    fun addWithOffset_roundTripsAndLegacyRowsStayReadable() {
        val prefs = TestPreferences()
        val store = BookmarkStore(prefs)
        store.add("book", 500, "第五章", 1234)

        val marks = store.list("book")
        assertEquals(1, marks.size)
        assertEquals(500, marks.first().progress)
        assertEquals("第五章", marks.first().label)
        assertEquals(1234, marks.first().offset)

        // Legacy two-field row (no offset) still parses with offset = -1.
        prefs.values["legacy"] = "300|" + java.util.Base64.getEncoder().encodeToString(
            "旧书签".toByteArray(Charsets.UTF_8),
        )
        val legacy = store.list("legacy")
        assertEquals(1, legacy.size)
        assertEquals(-1, legacy.first().offset)
    }

    @Test
    fun excerpt_windowAndBounds() {
        val body = "第一章 内容开始，这里是一段正文，用来测试摘要截取是否正常工作，后面还有更多文字。"
        val mid = BookmarkStore.excerpt(body, 10)
        assertTrue(mid.contains("第一章"))
        assertTrue(mid.length <= 82)
        assertEquals("", BookmarkStore.excerpt(body, -1))
        assertEquals("", BookmarkStore.excerpt("", 5))
        val tail = BookmarkStore.excerpt("短", 0)
        assertEquals("短", tail)
    }

    @Test
    fun removingLastBookmarkDropsPreferenceKeyAndInvalidIdsAreSafe() {
        val prefs = TestPreferences()
        val store = BookmarkStore(prefs)
        store.add("book", 500, "中间")

        store.remove("book", 0)
        store.clear("")
        store.clear(null)

        assertTrue(store.list("book").isEmpty())
        assertFalse(prefs.contains("book"))
        assertTrue(store.list(null).isEmpty())
    }
}
