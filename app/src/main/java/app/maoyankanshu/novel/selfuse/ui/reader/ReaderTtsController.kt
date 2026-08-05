package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** UI-facing TTS lifecycle for the Compose reader (in-page). */
enum class ReaderTtsState {
    Preparing,
    Ready,
    Speaking,
    Unavailable,
}

/**
 * System [TextToSpeech] for continuous reading.
 *
 * Critical OEM constraints:
 * - All TTS API calls must run on the **same** thread that owns the engine
 *   (cross-thread setLanguage/speak often yields silence or ERROR).
 * - Use a dedicated [HandlerThread] for create / language / speak / stop / shutdown.
 * - Set [AudioAttributes] + request audio focus so speech is audible under media routing.
 */
class ReaderTtsController(
    context: Context,
    private val onState: (ReaderTtsState) -> Unit,
    private val onChunkStart: (offset: Int) -> Unit = {},
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean(false)

    private val ttsThread = HandlerThread("yuejian-tts").apply { start() }
    private val ttsHandler = Handler(ttsThread.looper)

    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    @Volatile private var speaking = false
    @Volatile private var body: String = ""
    @Volatile private var offset: Int = 0
    @Volatile private var speechRate: Float = 1f
    @Volatile private var engineReady = false

    fun prepare(rate: Float) {
        speechRate = TtsRate.clamp(rate)
        postState(ReaderTtsState.Preparing)
        ttsHandler.post { createEngineLocked() }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = TtsRate.clamp(rate)
        ttsHandler.post {
            try {
                tts?.setSpeechRate(speechRate)
            } catch (e: Exception) {
                Log.w(TAG, "setSpeechRate failed", e)
            }
        }
    }

    /**
     * Start continuous reading of [fullText] from [startOffset].
     * Returns immediately; actual speak runs on the TTS thread.
     */
    fun start(fullText: String, startOffset: Int): Boolean {
        if (destroyed.get()) return false
        if (fullText.isEmpty()) return false
        if (!engineReady || tts == null) {
            // Kick prepare again in case first init failed or is still pending.
            ttsHandler.post { if (tts == null) createEngineLocked() }
            return false
        }
        body = fullText
        offset = startOffset.coerceIn(0, fullText.length)
        speaking = true
        postState(ReaderTtsState.Speaking)
        ttsHandler.post { speakNextLocked() }
        return true
    }

    fun stop() {
        speaking = false
        ttsHandler.post {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            abandonAudioFocusLocked()
            if (!destroyed.get()) {
                postState(if (engineReady) ReaderTtsState.Ready else ReaderTtsState.Unavailable)
            }
        }
    }

    fun shutdown() {
        destroyed.set(true)
        speaking = false
        ttsHandler.post {
            abandonAudioFocusLocked()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = null
            engineReady = false
            ttsThread.quitSafely()
        }
    }

    private fun createEngineLocked() {
        if (destroyed.get()) return
        // Tear down a half-open instance.
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        engineReady = false

        val preferred = preferredEnginePackage(app)
        // Prefer default system engine first (most reliable); then preferred package.
        // Some OEMs break when a package is forced incorrectly.
        val packages = ArrayList<String?>(3)
        packages.add(null) // system default constructor
        if (!preferred.isNullOrBlank()) packages.add(preferred)
        if (preferred != "com.google.android.tts" &&
            isPackageInstalled(app, "com.google.android.tts")
        ) {
            packages.add("com.google.android.tts")
        }
        openEngineWithRetry(packages)
    }

    /**
     * Try engine packages in order. [null] means system default constructor.
     * All callbacks are re-posted onto [ttsHandler] so speak/setLanguage stay single-threaded.
     */
    private fun openEngineWithRetry(packages: List<String?>) {
        if (destroyed.get() || packages.isEmpty()) {
            engineReady = false
            postState(ReaderTtsState.Unavailable)
            return
        }
        val enginePackage = packages.first()
        val rest = packages.drop(1)
        val listener = TextToSpeech.OnInitListener { status ->
            ttsHandler.post {
                if (destroyed.get()) return@post
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "onInit status=$status engine=$enginePackage")
                    try {
                        tts?.shutdown()
                    } catch (_: Exception) {
                    }
                    tts = null
                    openEngineWithRetry(rest)
                    return@post
                }
                val engine = tts
                if (engine == null) {
                    openEngineWithRetry(rest)
                    return@post
                }
                configureEngineLocked(engine)
                engineReady = true
                postState(ReaderTtsState.Ready)
                Log.i(TAG, "TTS ready defaultEngine=${engine.defaultEngine} pinned=$enginePackage")
            }
        }
        try {
            tts = if (enginePackage.isNullOrEmpty()) {
                TextToSpeech(app, listener)
            } else {
                TextToSpeech(app, listener, enginePackage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech create failed package=$enginePackage", e)
            tts = null
            openEngineWithRetry(rest)
        }
    }

    private fun configureEngineLocked(engine: TextToSpeech) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                engine.setAudioAttributes(attrs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "setAudioAttributes failed", e)
        }

        val applied = applyPreferredLanguageLocked(engine)
        if (!applied) {
            Log.w(TAG, "no preferred locale; keeping engine default")
        }
        try {
            engine.setSpeechRate(speechRate)
            engine.setPitch(1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "setSpeechRate/pitch failed", e)
        }
        attachListenerLocked(engine)
    }

    private fun applyPreferredLanguageLocked(engine: TextToSpeech): Boolean {
        for (locale in TtsLanguagePicker.preferredLocales()) {
            try {
                val avail = engine.isLanguageAvailable(locale)
                Log.d(TAG, "isLanguageAvailable($locale)=$avail")
                // LANG_MISSING_DATA (-1) / NOT_SUPPORTED (-2) are unusable.
                // Some engines return LANG_COUNTRY_AVAILABLE without voice packs but still speak.
                if (avail < TextToSpeech.LANG_AVAILABLE && avail != TextToSpeech.LANG_MISSING_DATA) {
                    // still try setLanguage for MISSING_DATA? No — skip truly unsupported.
                }
                if (avail == TextToSpeech.LANG_NOT_SUPPORTED) continue
                // Try set even for MISSING_DATA on some Chinese engines that report wrong codes.
                val set = engine.setLanguage(locale)
                Log.d(TAG, "setLanguage($locale)=$set")
                if (set >= TextToSpeech.LANG_AVAILABLE) {
                    return true
                }
                // MISSING_DATA: still accept if engine claims success path later via speak.
                if (set == TextToSpeech.LANG_MISSING_DATA) {
                    // Keep trying other locales; if none work, default remains.
                    continue
                }
            } catch (e: Exception) {
                Log.w(TAG, "locale $locale failed", e)
            }
        }
        return false
    }

    private fun speakNextLocked() {
        if (!speaking || destroyed.get()) return
        val engine = tts
        if (engine == null) {
            speaking = false
            postState(ReaderTtsState.Unavailable)
            return
        }
        if (offset >= body.length) {
            speaking = false
            abandonAudioFocusLocked()
            postState(ReaderTtsState.Ready)
            return
        }
        val start = offset
        val end = TtsSpeechChunks.nextChunkEnd(body, start)
        if (end <= start) {
            speaking = false
            abandonAudioFocusLocked()
            postState(ReaderTtsState.Ready)
            return
        }
        val chunk = body.substring(start, end).trim()
        offset = end
        if (chunk.isEmpty()) {
            // Skip whitespace-only slices.
            speakNextLocked()
            return
        }

        main.post {
            if (!destroyed.get()) onChunkStart(start)
        }

        if (!requestAudioFocusLocked()) {
            Log.w(TAG, "audio focus denied — still attempting speak")
        }

        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "yuejian-$end")
            }
            @Suppress("DEPRECATION")
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, "yuejian-$end")
            } else {
                val map = HashMap<String, String>()
                map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "yuejian-$end"
                engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, map)
            }
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "speak ERROR len=${chunk.length}")
                // Retry once after re-configure language.
                val retry = trySpeakFallbackLocked(engine, chunk, end)
                if (!retry) {
                    speaking = false
                    abandonAudioFocusLocked()
                    postState(ReaderTtsState.Unavailable)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
            speaking = false
            abandonAudioFocusLocked()
            postState(ReaderTtsState.Unavailable)
        }
    }

    private fun trySpeakFallbackLocked(engine: TextToSpeech, chunk: String, end: Int): Boolean {
        return try {
            applyPreferredLanguageLocked(engine)
            engine.setSpeechRate(speechRate)
            val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "yuejian-retry-$end")
            result == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "fallback speak failed", e)
            false
        }
    }

    private fun attachListenerLocked(engine: TextToSpeech) {
        try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    ttsHandler.post {
                        if (!speaking || destroyed.get()) return@post
                        speakNextLocked()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    ttsHandler.post {
                        Log.w(TAG, "utterance error id=$utteranceId")
                        speaking = false
                        abandonAudioFocusLocked()
                        if (!destroyed.get()) {
                            postState(ReaderTtsState.Ready)
                        }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    ttsHandler.post {
                        Log.w(TAG, "utterance error id=$utteranceId code=$errorCode")
                        speaking = false
                        abandonAudioFocusLocked()
                        if (!destroyed.get()) {
                            // Don't mark Unavailable on a single chunk error — allow retry.
                            postState(ReaderTtsState.Ready)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "listener failed", e)
        }
    }

    private fun requestAudioFocusLocked(): Boolean {
        if (hasAudioFocus) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                val r = audioManager.requestAudioFocus(req)
                hasAudioFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                hasAudioFocus
            } else {
                @Suppress("DEPRECATION")
                val r = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
                hasAudioFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                hasAudioFocus
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestAudioFocus failed", e)
            false
        }
    }

    private fun abandonAudioFocusLocked() {
        if (!hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        hasAudioFocus = false
        focusRequest = null
    }

    private fun postState(state: ReaderTtsState) {
        main.post {
            if (!destroyed.get()) onState(state)
        }
    }

    companion object {
        private const val TAG = "YueJianReaderTts"

        /**
         * Prefer a known working TTS package when the system default is missing/broken.
         * Pure package-name resolution — no speak calls.
         */
        fun preferredEnginePackage(context: Context): String? {
            // Secure setting (may be null).
            try {
                val secure = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "tts_default_synth",
                )
                if (!secure.isNullOrBlank() && isPackageInstalled(context, secure)) {
                    return secure
                }
            } catch (_: Exception) {
            }
            // Query engines via intent.
            try {
                val pm = context.packageManager
                val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(intent, 0)
                }
                for (info in services) {
                    val pkg = info.serviceInfo?.packageName ?: continue
                    if (pkg.isNotBlank()) return pkg
                }
            } catch (e: Exception) {
                Log.w(TAG, "query TTS engines failed", e)
            }
            // Common packages.
            val candidates = listOf(
                "com.google.android.tts",
                "com.samsung.SMT",
                "com.iflytek.speechsuite",
                "com.iflytek.inputmethod.tts",
                "com.huawei.voiceservice",
                "com.github.olga_yakovleva.rhvoice.android",
            )
            for (pkg in candidates) {
                if (isPackageInstalled(context, pkg)) return pkg
            }
            return null
        }

        fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
