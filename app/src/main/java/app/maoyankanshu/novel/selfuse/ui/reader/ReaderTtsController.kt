package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** UI-facing TTS lifecycle for the Compose reader (in-page, no legacy jump). */
enum class ReaderTtsState {
    /** Engine not yet connected. */
    Preparing,
    /** Connected and may speak. */
    Ready,
    /** Actively speaking chunks. */
    Speaking,
    /** Engine missing or failed hard. */
    Unavailable,
}

/**
 * System [TextToSpeech] controller (same engine stack Accessibility uses for speech).
 *
 * - Dedicated TTS thread for [TextToSpeech.setLanguage] (never blocks open-book IO).
 * - Chinese-first locale pick; still marks Ready when only default locale works.
 * - Short chunks via [TtsSpeechChunks] to avoid OEM jank.
 */
class ReaderTtsController(
    context: Context,
    private val onState: (ReaderTtsState) -> Unit,
    /** Called on main when a chunk starts; [offset] is the global char start of that chunk. */
    private val onChunkStart: (offset: Int) -> Unit = {},
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean(false)
    private val ttsExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "yuejian-reader-tts").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private var tts: TextToSpeech? = null
    @Volatile private var speaking = false
    @Volatile private var body: String = ""
    @Volatile private var offset: Int = 0
    @Volatile private var speechRate: Float = 1f

    fun prepare(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2f)
        postState(ReaderTtsState.Preparing)
        try {
            tts = TextToSpeech(app) { status ->
                if (destroyed.get()) return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS onInit status=$status")
                    postState(ReaderTtsState.Unavailable)
                    return@TextToSpeech
                }
                val engine = tts
                if (engine == null) {
                    postState(ReaderTtsState.Unavailable)
                    return@TextToSpeech
                }
                // setLanguage off the main thread and off the book-open thread.
                ttsExecutor.execute {
                    val applied = applyPreferredLanguage(engine)
                    try {
                        engine.setSpeechRate(speechRate)
                    } catch (e: Exception) {
                        Log.w(TAG, "setSpeechRate failed", e)
                    }
                    main.post {
                        if (destroyed.get() || tts !== engine) return@post
                        attachListener(engine)
                        // Even if no preferred locale matched, still Ready: default voice
                        // may still speak (better than stuck “准备中”).
                        if (!applied) {
                            Log.w(TAG, "no preferred locale; using engine default")
                        }
                        postState(ReaderTtsState.Ready)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS create failed", e)
            tts = null
            postState(ReaderTtsState.Unavailable)
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2f)
        try {
            tts?.setSpeechRate(speechRate)
        } catch (_: Exception) {
        }
    }

    /**
     * Start continuous reading of [fullText] from [startOffset].
     * No-op if engine not ready.
     */
    fun start(fullText: String, startOffset: Int): Boolean {
        if (destroyed.get()) return false
        val engine = tts ?: return false
        if (fullText.isEmpty()) return false
        body = fullText
        offset = startOffset.coerceIn(0, fullText.length)
        speaking = true
        postState(ReaderTtsState.Speaking)
        speakNext(engine)
        return true
    }

    fun stop() {
        speaking = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        if (!destroyed.get()) {
            postState(if (tts != null) ReaderTtsState.Ready else ReaderTtsState.Unavailable)
        }
    }

    fun shutdown() {
        destroyed.set(true)
        speaking = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsExecutor.shutdownNow()
    }

    private fun speakNext(engine: TextToSpeech) {
        if (!speaking || destroyed.get()) return
        if (offset >= body.length) {
            speaking = false
            postState(ReaderTtsState.Ready)
            return
        }
        val start = offset
        val end = TtsSpeechChunks.nextChunkEnd(body, start)
        if (end <= start) {
            speaking = false
            postState(ReaderTtsState.Ready)
            return
        }
        val chunk = body.substring(start, end)
        offset = end
        onChunkStart(start)
        try {
            val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "yuejian-$end")
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "speak ERROR")
                speaking = false
                postState(ReaderTtsState.Unavailable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
            speaking = false
            postState(ReaderTtsState.Unavailable)
        }
    }

    private fun attachListener(engine: TextToSpeech) {
        try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    main.post {
                        if (!speaking || destroyed.get()) return@post
                        val eng = tts ?: return@post
                        speakNext(eng)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { stop() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { stop() }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "listener failed", e)
        }
    }

    private fun applyPreferredLanguage(engine: TextToSpeech): Boolean {
        for (locale in TtsLanguagePicker.preferredLocales()) {
            try {
                val avail = engine.isLanguageAvailable(locale)
                if (!TtsLanguagePicker.isUsable(avail)) continue
                val set = engine.setLanguage(locale)
                if (TtsLanguagePicker.isUsable(set)) {
                    Log.i(TAG, "TTS language=$locale result=$set")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "locale $locale failed", e)
            }
        }
        return false
    }

    private fun postState(state: ReaderTtsState) {
        main.post {
            if (!destroyed.get()) onState(state)
        }
    }

    companion object {
        private const val TAG = "YueJianReaderTts"
    }
}
