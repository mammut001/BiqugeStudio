package app.maoyankanshu.novel.selfuse

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for TXT/EPUB import Intent resolution (VIEW / SEND / SEND_MULTIPLE).
 * String helpers only — no Uri.parse (unmocked on pure JVM android.jar).
 */
class ImportIntentUrisTest {

    @Test
    fun extraImport_matchesSearchActivity() {
        assertEquals("open_import", ImportIntentUris.EXTRA_IMPORT)
        assertEquals(ImportIntentUris.EXTRA_IMPORT, SearchActivity.EXTRA_IMPORT)
    }

    @Test
    fun maxUris_isTwenty() {
        assertEquals(20, ImportIntentUris.MAX_URIS)
    }

    @Test
    fun isSupportedScheme_contentAndFileOnly() {
        assertTrue(ImportIntentUris.isSupportedScheme("content"))
        assertTrue(ImportIntentUris.isSupportedScheme("CONTENT"))
        assertTrue(ImportIntentUris.isSupportedScheme("file"))
        assertTrue(ImportIntentUris.isSupportedScheme("File"))
        assertFalse(ImportIntentUris.isSupportedScheme("http"))
        assertFalse(ImportIntentUris.isSupportedScheme("https"))
        assertFalse(ImportIntentUris.isSupportedScheme(null))
        assertFalse(ImportIntentUris.isSupportedScheme(""))
    }

    @Test
    fun schemeOf_parsesWithoutAndroidUri() {
        assertEquals("content", ImportIntentUris.schemeOf("content://com.example/doc/1.txt"))
        assertEquals("file", ImportIntentUris.schemeOf("file:///sdcard/book.epub"))
        assertEquals("https", ImportIntentUris.schemeOf("https://example.com/a.epub"))
        assertNull(ImportIntentUris.schemeOf(null))
        assertNull(ImportIntentUris.schemeOf(""))
        assertNull(ImportIntentUris.schemeOf("not-a-uri"))
    }

    @Test
    fun resolveUriString_actionView_usesData() {
        val data = "content://com.android.providers.downloads.documents/document/42"
        assertEquals(
            data,
            ImportIntentUris.resolveUriString(Intent.ACTION_VIEW, data, streamUri = null),
        )
        assertEquals(
            "file:///storage/emulated/0/book.epub",
            ImportIntentUris.resolveUriString(
                Intent.ACTION_VIEW,
                "file:///storage/emulated/0/book.epub",
                null,
            ),
        )
    }

    @Test
    fun resolveUriString_actionSend_prefersExtraStream() {
        val stream = "content://media/external/file/99"
        val data = "content://ignored/data"
        assertEquals(
            stream,
            ImportIntentUris.resolveUriString(Intent.ACTION_SEND, data, stream),
        )
    }

    @Test
    fun resolveUriString_actionSend_fallsBackToClipThenData() {
        assertEquals(
            "content://clip/1",
            ImportIntentUris.resolveUriString(
                Intent.ACTION_SEND,
                dataUri = "content://data/1",
                streamUri = null,
                clipUri = "content://clip/1",
            ),
        )
        assertEquals(
            "content://data/1",
            ImportIntentUris.resolveUriString(
                Intent.ACTION_SEND,
                dataUri = "content://data/1",
                streamUri = null,
                clipUri = null,
            ),
        )
    }

    @Test
    fun resolveUriString_rejectsUnsupportedSchemes() {
        assertNull(
            ImportIntentUris.resolveUriString(
                Intent.ACTION_VIEW,
                "https://example.com/book.epub",
                null,
            ),
        )
        assertNull(
            ImportIntentUris.resolveUriString(
                Intent.ACTION_SEND,
                null,
                "http://evil.example/x.txt",
            ),
        )
    }

    @Test
    fun resolveUriStrings_sendMultiple_arrayListOfContentUris() {
        val streams = listOf(
            "content://a/1.txt",
            "content://b/2.epub",
            "file:///sdcard/3.txt",
            "https://skip.me/x.txt",
            "content://a/1.txt", // duplicate
        )
        val out = ImportIntentUris.resolveUriStrings(
            action = Intent.ACTION_SEND_MULTIPLE,
            dataUri = null,
            streamUris = streams,
        )
        assertEquals(
            listOf(
                "content://a/1.txt",
                "content://b/2.epub",
                "file:///sdcard/3.txt",
            ),
            out,
        )
    }

    @Test
    fun resolveUriStrings_sendMultiple_fallsBackToClipData() {
        val out = ImportIntentUris.resolveUriStrings(
            action = Intent.ACTION_SEND_MULTIPLE,
            dataUri = null,
            streamUris = emptyList(),
            clipUris = listOf(
                "content://clip/a.txt",
                "file:///tmp/b.epub",
                "http://nope",
            ),
        )
        assertEquals(
            listOf("content://clip/a.txt", "file:///tmp/b.epub"),
            out,
        )
    }

    @Test
    fun resolveUriStrings_capsAtMaxUris20() {
        val many = (1..30).map { "content://docs/book$it.txt" }
        val out = ImportIntentUris.resolveUriStrings(
            action = Intent.ACTION_SEND_MULTIPLE,
            dataUri = null,
            streamUris = many,
            maxUris = ImportIntentUris.MAX_URIS,
        )
        assertEquals(20, out.size)
        assertEquals("content://docs/book1.txt", out.first())
        assertEquals("content://docs/book20.txt", out.last())
    }

    @Test
    fun filterSupportedUriStrings_emptyAndHttpDropped() {
        assertTrue(
            ImportIntentUris.filterSupportedUriStrings(
                listOf(null, "", "  ", "https://x", "ftp://y"),
            ).isEmpty(),
        )
    }

    @Test
    fun wantsOpenImportPicker_fromExtra() {
        assertTrue(ImportIntentUris.wantsOpenImportPicker(true))
        assertFalse(ImportIntentUris.wantsOpenImportPicker(false))
    }

    @Test
    fun actionConstants_viewSendAndSendMultiple() {
        assertEquals("android.intent.action.VIEW", Intent.ACTION_VIEW)
        assertEquals("android.intent.action.SEND", Intent.ACTION_SEND)
        assertEquals("android.intent.action.SEND_MULTIPLE", Intent.ACTION_SEND_MULTIPLE)
        assertEquals("android.intent.extra.STREAM", Intent.EXTRA_STREAM)
    }

    @Test
    fun openImportExtra_keyIsStable() {
        assertEquals("open_import", SearchActivity.EXTRA_IMPORT)
        assertTrue(ImportIntentUris.wantsOpenImportPicker(true))
    }
}
