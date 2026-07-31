package app.maoyankanshu.novel.selfuse

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for TXT/EPUB import Intent resolution (VIEW / SEND / schemes).
 * Uses string helpers only — no Uri.parse (unmocked on pure JVM android.jar).
 */
class ImportIntentUrisTest {

    @Test
    fun extraImport_matchesSearchActivity() {
        assertEquals("open_import", ImportIntentUris.EXTRA_IMPORT)
        assertEquals(ImportIntentUris.EXTRA_IMPORT, SearchActivity.EXTRA_IMPORT)
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
    fun wantsOpenImportPicker_fromExtra() {
        assertTrue(ImportIntentUris.wantsOpenImportPicker(true))
        assertFalse(ImportIntentUris.wantsOpenImportPicker(false))
    }

    @Test
    fun actionConstants_viewAndSend() {
        // Document Manifest / SearchActivity contract for tests without building Intents.
        assertEquals("android.intent.action.VIEW", Intent.ACTION_VIEW)
        assertEquals("android.intent.action.SEND", Intent.ACTION_SEND)
        assertEquals("android.intent.extra.STREAM", Intent.EXTRA_STREAM)
    }

    @Test
    fun openImportExtra_keyIsStable() {
        assertEquals("open_import", SearchActivity.EXTRA_IMPORT)
        assertTrue(ImportIntentUris.wantsOpenImportPicker(true))
    }
}
