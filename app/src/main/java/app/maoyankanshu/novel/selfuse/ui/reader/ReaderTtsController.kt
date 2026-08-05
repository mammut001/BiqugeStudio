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
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
 * Compatibility notes (ColorOS / OnePlus / many OEMs):
 * - Create and call TTS **on the main thread** — HandlerThread init often returns
 *   [TextToSpeech.ERROR] onInit and surfaces as “语音朗读服务不可用”.
 * - Pin user-selected engine package when set (Google / 讯飞 / …).
 * - [AudioAttributes] + audio focus so speech is not routed to a silent stream.
 */
class ReaderTtsController(
    context: Context,
    private val onState: (ReaderTtsState) -> Unit,
    private val onChunkStart: (offset: Int) -> Unit = {},
    /** Fired on main when engine list is refreshed via [TextToSpeech.getEngines]. */
    private val onEnginesDiscovered: (List<TtsEngineOption>) -> Unit = {},
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean(false)
    private val initGeneration = AtomicInteger(0)

    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    @Volatile private var speaking = false
    @Volatile private var body: String = ""
    @Volatile private var offset: Int = 0
    @Volatile private var speechRate: Float = 1f
    @Volatile private var engineReady = false
    @Volatile private var preferredEnginePackage: String = ""

    fun prepare(rate: Float, enginePackage: String = "") {
        speechRate = TtsRate.clamp(rate)
        preferredEnginePackage = TtsEngineCatalog.normalizePackage(enginePackage)
        postState(ReaderTtsState.Preparing)
        // Main thread: required for reliable onInit on ColorOS / OxygenOS.
        main.post { createEngineOnMain() }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = TtsRate.clamp(rate)
        main.post {
            try {
                tts?.setSpeechRate(speechRate)
            } catch (e: Exception) {
                Log.w(TAG, "setSpeechRate failed", e)
            }
        }
    }

    fun switchEngine(enginePackage: String, rate: Float = speechRate) {
        speaking = false
        preferredEnginePackage = TtsEngineCatalog.normalizePackage(enginePackage)
        speechRate = TtsRate.clamp(rate)
        postState(ReaderTtsState.Preparing)
        main.post {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            abandonAudioFocus()
            createEngineOnMain()
        }
    }

    fun start(fullText: String, startOffset: Int): Boolean {
        if (destroyed.get()) return false
        if (fullText.isEmpty()) return false
        if (!engineReady || tts == null) {
            main.post { if (tts == null) createEngineOnMain() }
            return false
        }
        body = fullText
        offset = startOffset.coerceIn(0, fullText.length)
        speaking = true
        postState(ReaderTtsState.Speaking)
        main.post { speakNext() }
        return true
    }

    fun stop() {
        speaking = false
        main.post {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            abandonAudioFocus()
            if (!destroyed.get()) {
                postState(if (engineReady) ReaderTtsState.Ready else ReaderTtsState.Unavailable)
            }
        }
    }

    fun shutdown() {
        destroyed.set(true)
        speaking = false
        main.post {
            abandonAudioFocus()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = null
            engineReady = false
        }
    }

    private fun createEngineOnMain() {
        if (destroyed.get()) return
        check(Looper.myLooper() == Looper.getMainLooper()) { "TTS must init on main" }

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        engineReady = false

        val packages = buildEngineTryOrder()
        openEngineWithRetry(packages, initGeneration.incrementAndGet())
    }

    private fun buildEngineTryOrder(): List<String?> {
        val order = ArrayList<String?>()
        val user = preferredEnginePackage.trim()
        if (user.isNotEmpty() && TtsEngineCatalog.isPackageInstalled(app, user)) {
            order.add(user)
        }
        // System default (respects phone Settings → 文字转语音).
        order.add(null)
        // Every installed engine as fallback (Google, 讯飞, …).
        for (opt in TtsEngineCatalog.listInstalled(app)) {
            val pkg = opt.packageName
            if (pkg.isEmpty()) continue
            if (pkg == user) continue
            order.add(pkg)
        }
        if (TtsEngineCatalog.isPackageInstalled(app, "com.google.android.tts") &&
            !order.contains("com.google.android.tts")
        ) {
            order.add("com.google.android.tts")
        }
        return order.distinct()
    }

    private fun openEngineWithRetry(packages: List<String?>, generation: Int) {
        if (destroyed.get() || generation != initGeneration.get()) return
        if (packages.isEmpty()) {
            engineReady = false
            postState(ReaderTtsState.Unavailable)
            Log.e(TAG, "all TTS engines failed to init")
            return
        }
        val enginePackage = packages.first()
        val rest = packages.drop(1)
        Log.i(TAG, "trying TTS engine=${enginePackage ?: "SYSTEM_DEFAULT"} remaining=${rest.size}")

        val listener = TextToSpeech.OnInitListener { status ->
            main.post {
                if (destroyed.get() || generation != initGeneration.get()) return@post
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "onInit ERROR status=$status engine=$enginePackage")
                    try {
                        tts?.shutdown()
                    } catch (_: Exception) {
                    }
                    tts = null
                    openEngineWithRetry(rest, generation)
                    return@post
                }
                val engine = tts
                if (engine == null) {
                    openEngineWithRetry(rest, generation)
                    return@post
                }
                if (!configureEngine(engine)) {
                    Log.w(TAG, "configure failed, try next engine")
                    try {
                        engine.shutdown()
                    } catch (_: Exception) {
                    }
                    tts = null
                    openEngineWithRetry(rest, generation)
                    return@post
                }
                engineReady = true
                postState(ReaderTtsState.Ready)
                Log.i(
                    TAG,
                    "TTS ready pinned=$enginePackage defaultEngine=${
                        try {
                            engine.defaultEngine
                        } catch (_: Exception) {
                            "?"
                        }
                    }",
                )
            }
        }

        try {
            tts = if (enginePackage.isNullOrEmpty()) {
                TextToSpeech(app, listener)
            } else {
                TextToSpeech(app, listener, enginePackage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "create failed engine=$enginePackage", e)
            tts = null
            openEngineWithRetry(rest, generation)
        }
    }

    /**
     * @return false only if the engine is completely unusable for any locale
     * (we still return true when Chinese is missing but English works — user may
     * switch engine / install voice data).
     */
    private fun configureEngine(engine: TextToSpeech): Boolean {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(attrs)
        } catch (e: Exception) {
            Log.w(TAG, "setAudioAttributes failed", e)
        }

        // Always try to set a language; do not fail init if Chinese data is missing.
        val langOk = applyPreferredLanguage(engine)
        if (!langOk) {
            Log.w(TAG, "no preferred locale applied; engine may still speak default voice")
        }
        try {
            engine.setSpeechRate(speechRate)
            engine.setPitch(1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "rate/pitch failed", e)
        }
        attachListener(engine)
        // Probe with a silent empty speak? Skip — some engines error on empty.
        // Consider engine usable if onInit succeeded.
        return true
    }

    private fun applyPreferredLanguage(engine: TextToSpeech): Boolean {
        for (locale in TtsLanguagePicker.preferredLocales()) {
            try {
                val avail = engine.isLanguageAvailable(locale)
                Log.d(TAG, "isLanguageAvailable($locale)=$avail")
                if (avail == TextToSpeech.LANG_NOT_SUPPORTED) continue
                val set = engine.setLanguage(locale)
                Log.d(TAG, "setLanguage($locale)=$set")
                if (set >= TextToSpeech.LANG_AVAILABLE) return true
                // Some Chinese engines return MISSING_DATA but still speak with network/offline packs.
                if (set == TextToSpeech.LANG_MISSING_DATA) {
                    // Keep as candidate but continue looking for a better match.
                    continue
                }
            } catch (e: Exception) {
                Log.w(TAG, "locale $locale failed", e)
            }
        }
        // Last resort: device default locale.
        return try {
            val def = Locale.getDefault()
            engine.setLanguage(def) >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    private fun speakNext() {
        if (!speaking || destroyed.get()) return
        check(Looper.myLooper() == Looper.getMainLooper())
        val engine = tts
        if (engine == null) {
            speaking = false
            postState(ReaderTtsState.Unavailable)
            return
        }
        if (offset >= body.length) {
            speaking = false
            abandonAudioFocus()
            postState(ReaderTtsState.Ready)
            return
        }
        val start = offset
        val end = TtsSpeechChunks.nextChunkEnd(body, start)
        if (end <= start) {
            speaking = false
            abandonAudioFocus()
            postState(ReaderTtsState.Ready)
            return
        }
        val chunk = body.substring(start, end).trim()
        offset = end
        if (chunk.isEmpty()) {
            speakNext()
            return
        }

        if (!destroyed.get()) onChunkStart(start)
        requestAudioFocus()

        try {
            val utteranceId = "yuejian-$end"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                // Ensure music stream routing on older engines.
                putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
            }
            val result = engine.speak(chunk, TextToSpeech.QUEUE_ADD, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "speak ERROR, retry FLUSH once")
                val retry = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (retry == TextToSpeech.ERROR) {
                    speaking = false
                    abandonAudioFocus()
                    postState(ReaderTtsState.Unavailable)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
            speaking = false
            abandonAudioFocus()
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
                        speakNext()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post {
                        Log.w(TAG, "utterance error id=$utteranceId")
                        speaking = false
                        abandonAudioFocus()
                        if (!destroyed.get()) postState(ReaderTtsState.Ready)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post {
                        Log.w(TAG, "utterance error id=$utteranceId code=$errorCode")
                        speaking = false
                        abandonAudioFocus()
                        if (!destroyed.get()) postState(ReaderTtsState.Ready)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "listener failed", e)
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
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
                hasAudioFocus =
                    audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                hasAudioFocus = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestAudioFocus failed", e)
        }
    }

    private fun abandonAudioFocus() {
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

        fun preferredEnginePackage(context: Context): String? {
            try {
                val secure = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "tts_default_synth",
                )
                if (!secure.isNullOrBlank() && TtsEngineCatalog.isPackageInstalled(context, secure)) {
                    return secure
                }
            } catch (_: Exception) {
            }
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
                    val pkg = info.serviceInfo?.packageName
                    if (!pkg.isNullOrBlank()) return pkg
                }
            } catch (_: Exception) {
            }
            return null
        }
    }
}
