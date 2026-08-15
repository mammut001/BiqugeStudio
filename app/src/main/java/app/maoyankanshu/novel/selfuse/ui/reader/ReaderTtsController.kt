package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
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
 * ColorOS / OnePlus (PKG110) ships `com.oplus.ttsaccessibilityengine`. Direct
 * [TextToSpeech.speak] is often fully muted by OEM **AudioHardening** because
 * synthesis/playback runs in the TTS package process (treated as background).
 * We therefore **synthesizeToFile** then play with [MediaPlayer] in *our*
 * process (foreground), which is not muted.
 *
 * Other notes:
 * - Create TTS on the **main thread** (ColorOS onInit is unreliable off-main).
 * - Prefer system-default constructor; pin package only when user picks one.
 */
class ReaderTtsController(
    context: Context,
    private val onState: (ReaderTtsState) -> Unit,
    /**
     * Fired on the main thread when a chunk **actually starts playing**
     * (not when synthesis is queued). `[start, endExclusive)` are offsets in the
     * full body string passed to [start].
     */
    private val onChunkRange: (start: Int, endExclusive: Int) -> Unit = { _, _ -> },
    /**
     * Fired on main when app-owned synthesized playback starts and its duration is known.
     * Direct engine.speak fallback has no reliable duration and intentionally does not fire this.
     */
    private val onChunkPlayback: (start: Int, endExclusive: Int, durationMs: Long) -> Unit =
        { _, _, _ -> },
    /** Fired on main when engine list is refreshed via [TextToSpeech.getEngines]. */
    private val onEnginesDiscovered: (List<TtsEngineOption>) -> Unit = {},
    /** Fired on main after an engine is ready with its available voices. */
    private val onVoicesDiscovered: (List<TtsVoiceOption>, String) -> Unit = { _, _ -> },
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean(false)
    private val initGeneration = AtomicInteger(0)
    private val speakGeneration = AtomicInteger(0)

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var engineTryOrder: List<String?> = emptyList()
    private var engineTryIndex: Int = 0
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    /** Pending wav for the current synthesizeToFile utterance. */
    private var pendingSynthFile: File? = null
    private var pendingSynthOffset: Int = 0
    private var pendingChunkStart: Int = 0
    private var pendingChunkEnd: Int = 0
    private var synthTimeout: Runnable? = null
    private var playbackTimeout: Runnable? = null

    @Volatile private var speaking = false
    @Volatile private var body: String = ""
    @Volatile private var offset: Int = 0
    @Volatile private var speechRate: Float = 1f
    @Volatile private var engineReady = false
    @Volatile private var preferredEnginePackage: String = ""
    @Volatile private var preferredVoiceName: String = ""
    /** When true, next Ready state will auto-call [speakNext] (start waited for init). */
    @Volatile private var pendingStart = false
    @Volatile private var previewing = false

    private val cacheDir: File
        get() = File(app.cacheDir, "reader_tts").also { it.mkdirs() }

    fun prepare(
        rate: Float,
        enginePackage: String = "",
        voiceName: String = "",
    ) {
        speechRate = TtsRate.clamp(rate)
        preferredEnginePackage = TtsEngineCatalog.normalizePackage(enginePackage)
        preferredVoiceName = voiceName.trim()
        postState(ReaderTtsState.Preparing)
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

    fun switchEngine(
        enginePackage: String,
        rate: Float = speechRate,
        voiceName: String = preferredVoiceName,
    ) {
        speaking = false
        previewing = false
        pendingStart = false
        speakGeneration.incrementAndGet()
        preferredEnginePackage = TtsEngineCatalog.normalizePackage(enginePackage)
        preferredVoiceName = voiceName.trim()
        speechRate = TtsRate.clamp(rate)
        postState(ReaderTtsState.Preparing)
        main.post {
            stopPlaybackInternal()
            createEngineOnMain()
        }
    }

    /** Rebinds the current engine with a selected voice, without changing the book anchor. */
    fun switchVoice(voiceName: String, rate: Float = speechRate) {
        speaking = false
        previewing = false
        pendingStart = false
        speakGeneration.incrementAndGet()
        preferredVoiceName = voiceName.trim()
        speechRate = TtsRate.clamp(rate)
        postState(ReaderTtsState.Preparing)
        main.post {
            stopPlaybackInternal()
            createEngineOnMain()
        }
    }

    /** Starts a short engine-only test without changing the reader's page anchor. */
    fun preview(): Boolean {
        stop()
        previewing = true
        return start(PREVIEW_TEXT, 0, isPreview = true)
    }

    fun start(fullText: String, startOffset: Int, isPreview: Boolean = false): Boolean {
        if (destroyed.get()) {
            Log.w(TAG, "start rejected: destroyed")
            return false
        }
        if (fullText.isEmpty()) {
            Log.w(TAG, "start rejected: empty body")
            return false
        }
        previewing = isPreview
        body = fullText
        offset = startOffset.coerceIn(0, fullText.length)
        speaking = true
        speakGeneration.incrementAndGet()
        if (!engineReady || tts == null) {
            Log.i(TAG, "start queued: engineReady=$engineReady hasTts=${tts != null}")
            pendingStart = true
            main.post { if (tts == null) createEngineOnMain() }
            postState(ReaderTtsState.Preparing)
            return false
        }
        pendingStart = false
        Log.i(TAG, "start accepted offset=$offset len=${fullText.length}")
        postState(ReaderTtsState.Speaking)
        // Tear down any leftover playback from a prior session before the new chunk.
        main.post {
            stopPlaybackInternal()
            if (speaking && !destroyed.get()) speakNext()
        }
        return true
    }

    fun stop() {
        speaking = false
        previewing = false
        pendingStart = false
        val stopGeneration = speakGeneration.incrementAndGet()
        main.post {
            // A newer start() may have already advanced speakGeneration — do not
            // tear down that session's player/synth.
            if (speakGeneration.get() != stopGeneration) return@post
            stopPlaybackInternal()
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            abandonAudioFocus()
            if (!destroyed.get() && speakGeneration.get() == stopGeneration) {
                postState(if (engineReady) ReaderTtsState.Ready else ReaderTtsState.Unavailable)
            }
        }
    }

    fun shutdown() {
        destroyed.set(true)
        speaking = false
        previewing = false
        pendingStart = false
        speakGeneration.incrementAndGet()
        main.post {
            stopPlaybackInternal()
            abandonAudioFocus()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = null
            engineReady = false
            clearCacheQuietly()
        }
    }

    private fun createEngineOnMain() {
        if (destroyed.get()) return
        check(Looper.myLooper() == Looper.getMainLooper()) { "TTS must init on main" }

        stopPlaybackInternal()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        engineReady = false

        val packages = buildEngineTryOrder()
        engineTryOrder = packages
        engineTryIndex = 0
        openEngineWithRetry(packages, initGeneration.incrementAndGet())
    }

    private fun buildEngineTryOrder(): List<String?> {
        val order = ArrayList<String?>()
        val user = preferredEnginePackage.trim()
        // Empty / invalid (e.g. 小布助手) → system default only.
        if (user.isEmpty() || !TtsEngineCatalog.isBindableEngine(app, user)) {
            if (user.isNotEmpty()) {
                Log.w(TAG, "ignoring non-TTS package pin=$user")
            }
            order.add(null)
        } else {
            order.add(user)
            // Always keep system default as fallback (pin can fail/mute on OEMs).
            order.add(null)
        }
        for (opt in TtsEngineCatalog.listInstalled(app)) {
            val pkg = opt.packageName
            if (pkg.isEmpty()) continue
            if (pkg == user) continue
            order.add(pkg)
        }
        return order.distinct()
    }

    private fun openEngineWithRetry(packages: List<String?>, generation: Int) {
        if (destroyed.get() || generation != initGeneration.get()) return
        if (packages.isEmpty()) {
            engineReady = false
            pendingStart = false
            postState(ReaderTtsState.Unavailable)
            Log.e(TAG, "all TTS engines failed to init")
            return
        }
        val enginePackage = packages.first()
        val rest = packages.drop(1)
        engineTryIndex = engineTryOrder.indexOf(enginePackage).coerceAtLeast(0)
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
                if (!configureEngine(engine, generation)) {
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
                try {
                    val merged = TtsEngineCatalog.mergeFromTextToSpeech(
                        TtsEngineCatalog.listInstalled(app),
                        engine,
                    )
                    onEnginesDiscovered(merged)
                } catch (e: Exception) {
                    Log.w(TAG, "engines discover failed", e)
                }
                try {
                    val voices = TtsVoiceCatalog.fromTextToSpeech(engine)
                    onVoicesDiscovered(voices, engine.voice?.name.orEmpty())
                } catch (e: Exception) {
                    Log.w(TAG, "voices discover failed", e)
                    onVoicesDiscovered(emptyList(), "")
                }
                Log.i(
                    TAG,
                    "TTS ready pinned=$enginePackage defaultEngine=${
                        try {
                            engine.defaultEngine
                        } catch (_: Exception) {
                            "?"
                        }
                    } musicVol=${
                        try {
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        } catch (_: Exception) {
                            -1
                        }
                    }",
                )
                if (pendingStart && speaking && !destroyed.get()) {
                    pendingStart = false
                    postState(ReaderTtsState.Speaking)
                    speakNext()
                } else {
                    postState(ReaderTtsState.Ready)
                }
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

    private fun configureEngine(engine: TextToSpeech, generation: Int): Boolean {
        // Do NOT set AudioAttributes on the TTS engine for direct speak — ColorOS
        // accessibility TTS is often muted under MEDIA usage when playing in the
        // engine process. We play generated audio in our process instead.
        val langOk = applyPreferredLanguage(engine)
        if (!langOk) {
            // OEM engines (Oplus/讯飞/…) often report LANG_MISSING_DATA for zh yet
            // still synthesize. Keep the engine instead of rejecting the whole candidate.
            Log.w(TAG, "no preferred locale confirmed; keep engine and try speak")
        }
        applyPreferredVoice(engine)
        try {
            engine.setSpeechRate(speechRate)
            engine.setPitch(1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "rate/pitch failed", e)
        }
        attachListener(engine, generation)
        return true
    }

    private fun applyPreferredVoice(engine: TextToSpeech) {
        val requested = preferredVoiceName.trim()
        if (requested.isEmpty()) return
        try {
            val voice = engine.voices.orEmpty().firstOrNull { it.name == requested }
            if (voice == null) {
                Log.w(TAG, "voice not found=$requested; use engine default")
                preferredVoiceName = ""
                return
            }
            engine.voice = voice
            Log.i(TAG, "voice selected=${voice.name} locale=${voice.locale}")
        } catch (e: Exception) {
            Log.w(TAG, "voice selection failed=$requested", e)
            preferredVoiceName = ""
        }
    }

    private fun applyPreferredLanguage(engine: TextToSpeech): Boolean {
        var acceptedMissingData = false
        for (locale in TtsLanguagePicker.preferredLocales()) {
            try {
                val avail = engine.isLanguageAvailable(locale)
                Log.d(TAG, "isLanguageAvailable($locale)=$avail")
                if (avail == TextToSpeech.LANG_NOT_SUPPORTED) continue
                val set = engine.setLanguage(locale)
                Log.d(TAG, "setLanguage($locale)=$set")
                if (set >= TextToSpeech.LANG_AVAILABLE) return true
                if (set == TextToSpeech.LANG_MISSING_DATA) {
                    // Bind the locale anyway — many OEMs still speak with MISSING_DATA.
                    acceptedMissingData = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "locale $locale failed", e)
            }
        }
        return try {
            val def = Locale.getDefault()
            val set = engine.setLanguage(def)
            set >= TextToSpeech.LANG_AVAILABLE ||
                set == TextToSpeech.LANG_MISSING_DATA ||
                acceptedMissingData
        } catch (_: Exception) {
            acceptedMissingData
        }
    }

    private fun speakNext() {
        if (!speaking || destroyed.get()) return
        check(Looper.myLooper() == Looper.getMainLooper())
        val gen = speakGeneration.get()
        val engine = tts
        if (engine == null) {
            speaking = false
            postState(ReaderTtsState.Unavailable)
            return
        }

        var speakStart = -1
        var speakEnd = -1
        var chunk = ""
        // Skip blank / whitespace-only paragraphs without deep recursion.
        while (speaking && !destroyed.get()) {
            if (offset >= body.length) {
                speaking = false
                previewing = false
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
            offset = end
            val trimmed = TtsSpeechChunks.trimmedChunkRange(body, start, end)
            if (trimmed.isEmpty()) continue
            speakStart = trimmed.first
            speakEnd = trimmed.last + 1
            chunk = body.substring(speakStart, speakEnd)
            break
        }
        if (!speaking || destroyed.get() || chunk.isEmpty() || speakStart < 0) return

        pendingChunkStart = speakStart
        pendingChunkEnd = speakEnd
        pendingSynthOffset = speakStart

        val file = File(cacheDir, "chunk_${gen}_$speakEnd.wav")
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
        pendingSynthFile = file
        scheduleSynthTimeout(file, gen)

        val utteranceId = TtsUtteranceIds.synth(gen, speakEnd)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        try {
            // Bake rate into the wav.
            try {
                engine.setSpeechRate(speechRate)
            } catch (_: Exception) {
            }
            val result = engine.synthesizeToFile(chunk, params, file, utteranceId)
            Log.i(TAG, "synthesizeToFile result=$result len=${chunk.length} file=${file.name}")
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "synthesizeToFile ERROR, try next engine")
                retryCurrentChunkOrDirect(gen, "synthesizeToFile returned ERROR")
            }
        } catch (e: Exception) {
            Log.w(TAG, "synthesize failed", e)
            retryCurrentChunkOrDirect(gen, "synthesize exception")
        }
    }

    /**
     * Last resort: engine.speak in TTS process. Often muted on ColorOS AudioHardening;
     * still try so devices without file synth keep a path.
     */
    private fun fallbackDirectSpeak(
        engine: TextToSpeech,
        chunk: String,
        utteranceId: String,
        gen: Int,
    ) {
        clearSynthTimeout()
        pendingSynthFile = null
        requestAudioFocus()
        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putString(
                    TextToSpeech.Engine.KEY_PARAM_STREAM,
                    AudioManager.STREAM_MUSIC.toString(),
                )
            }
            val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            Log.i(TAG, "fallback speak result=$result")
            if (result == TextToSpeech.ERROR) {
                if (!tryNextEngine(gen, "direct speak returned ERROR")) {
                    speaking = false
                    abandonAudioFocus()
                    postState(ReaderTtsState.Unavailable)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fallback speak failed", e)
            if (!tryNextEngine(gen, "direct speak exception")) {
                speaking = false
                abandonAudioFocus()
                postState(ReaderTtsState.Unavailable)
            }
        }
    }

    private fun playSynthesizedFile(file: File, gen: Int) {
        if (!speaking || destroyed.get() || gen != speakGeneration.get()) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
            return
        }
        if (!file.exists() || file.length() < 44L) {
            Log.w(TAG, "synth file missing/empty size=${file.length()} path=${file.absolutePath}")
            fallbackCurrentChunk(gen, "synth file missing")
            return
        }
        stopMediaPlayerOnly()
        stopAudioTrackOnly()
        if (isColorOsFamily() && playPcmWithAudioTrack(file, gen)) return
        requestAudioFocus()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(playbackAudioAttributes())
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                main.post {
                    clearPlaybackTimeout()
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === mp) {
                        stopMediaPlayerOnly()
                    }
                    if (!speaking || destroyed.get() || gen != speakGeneration.get()) return@post
                    speakNext()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                main.post {
                    clearPlaybackTimeout()
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === mp) {
                        stopMediaPlayerOnly()
                    }
                    if (!speaking || destroyed.get() || gen != speakGeneration.get()) return@post
                    fallbackCurrentChunk(gen, "MediaPlayer error")
                }
                true
            }
            mp.prepare()
            val knownDurationMs = mp.duration.toLong().takeIf { it > 0L }
            mp.start()
            mediaPlayer = mp
            // Watchdog must never race ahead of audible playback. Prefer the actual synthesized
            // media duration; only fall back to a deliberately generous character estimate.
            val playTimeoutMs = TtsPlaybackWatchdog.timeoutMs(
                knownDurationMs = knownDurationMs,
                chars = pendingChunkEnd - pendingChunkStart,
            )
            schedulePlaybackTimeout(gen, playTimeoutMs)
            emitChunkRangePlaying(gen)
            emitChunkPlaybackInfo(gen, knownDurationMs)
            Log.i(
                TAG,
                "MediaPlayer started size=${file.length()} durationMs=${knownDurationMs ?: -1} watchdogMs=$playTimeoutMs",
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer play failed", e)
            try {
                file.delete()
            } catch (_: Exception) {
            }
            if (speaking && !destroyed.get() && gen == speakGeneration.get()) {
                fallbackCurrentChunk(gen, "MediaPlayer start failed")
            }
        }
    }

    /** Retries the current chunk with another engine, then falls back to direct speak. */
    private fun retryCurrentChunkOrDirect(gen: Int, reason: String) {
        if (!speaking || destroyed.get() || gen != speakGeneration.get()) return
        val engine = tts
        val rawStart = pendingSynthOffset.coerceIn(0, body.length)
        val rawEnd = offset.coerceIn(rawStart, body.length)
        val trimmed = TtsSpeechChunks.trimmedChunkRange(body, rawStart, rawEnd)
        clearSynthTimeout()
        pendingSynthFile = null
        if (tryNextEngine(gen, reason)) return
        if (engine == null || trimmed.isEmpty()) {
            speakNext()
            return
        }
        val start = trimmed.first
        val end = trimmed.last + 1
        val chunk = body.substring(start, end)
        Log.w(TAG, "$reason; fallback direct speak start=$start end=$end")
        pendingChunkStart = start
        pendingChunkEnd = end
        fallbackDirectSpeak(engine, chunk, TtsUtteranceIds.speak(gen, end), gen)
    }

    /** Keeps playback failures on the same chunk while moving to the next candidate engine. */
    private fun tryNextEngine(gen: Int, reason: String): Boolean {
        if (!speaking || destroyed.get() || gen != speakGeneration.get()) return false
        val nextIndex = engineTryIndex + 1
        if (nextIndex >= engineTryOrder.size) return false

        val retryOffset = pendingSynthOffset.coerceIn(0, body.length)
        offset = retryOffset
        pendingSynthFile = null
        clearSynthTimeout()
        stopMediaPlayerOnly()
        stopAudioTrackOnly()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        engineReady = false
        pendingStart = true
        engineTryIndex = nextIndex
        val nextGeneration = initGeneration.incrementAndGet()
        postState(ReaderTtsState.Preparing)
        Log.w(
            TAG,
            "$reason; switching engine=${engineTryOrder[nextIndex] ?: "SYSTEM_DEFAULT"}",
        )
        openEngineWithRetry(engineTryOrder.drop(nextIndex), nextGeneration)
        return true
    }

    /** Replays the chunk through the engine when file playback is unsupported or broken. */
    private fun fallbackCurrentChunk(gen: Int, reason: String) {
        retryCurrentChunkOrDirect(gen, reason)
    }

    private fun attachListener(engine: TextToSpeech, generation: Int) {
        try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "utterance start id=$utteranceId")
                    val id = utteranceId.orEmpty()
                    val utterGen = TtsUtteranceIds.parseGeneration(id) ?: return
                    // Direct speak path: audio begins in the TTS engine process here.
                    if (TtsUtteranceIds.isSpeak(id)) {
                        main.post { emitChunkRangePlaying(utterGen) }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val id = utteranceId.orEmpty()
                    val utterGen = TtsUtteranceIds.parseGeneration(id)
                    main.post {
                        if (destroyed.get()) return@post
                        if (generation != initGeneration.get()) return@post
                        if (utterGen == null || utterGen != speakGeneration.get()) return@post
                        if (TtsUtteranceIds.isSynth(id)) {
                            // synthesizeToFile finished → play in our process.
                            val file = pendingSynthFile
                            clearSynthTimeout()
                            pendingSynthFile = null
                            if (file != null && speaking) {
                                playSynthesizedFile(file, utterGen)
                            }
                            return@post
                        }
                        // fallback direct speak completed → next chunk.
                        if (!speaking) return@post
                        speakNext()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onUtteranceError(utteranceId, -1, generation)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onUtteranceError(utteranceId, errorCode, generation)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "listener failed", e)
        }
    }

    private fun onUtteranceError(utteranceId: String?, errorCode: Int, generation: Int) {
        main.post {
            if (generation != initGeneration.get()) return@post
            val id = utteranceId.orEmpty()
            val utterGen = TtsUtteranceIds.parseGeneration(id)
            if (utterGen != null && utterGen != speakGeneration.get()) return@post
            Log.w(TAG, "utterance error id=$utteranceId code=$errorCode")
            val file = pendingSynthFile
            clearSynthTimeout()
            pendingSynthFile = null
            try {
                file?.delete()
            } catch (_: Exception) {
            }
            if (!speaking || destroyed.get()) return@post
            val gen = utterGen ?: speakGeneration.get()
            if (TtsUtteranceIds.isSynth(id) && tts != null) {
                fallbackCurrentChunk(gen, "TTS synthesis error")
            } else if (!tryNextEngine(gen, "direct speak error")) {
                // Same chunk exhausted every engine — skip forward rather than hang.
                speakNext()
            }
        }
    }

    private fun stopPlaybackInternal() {
        stopMediaPlayerOnly()
        stopAudioTrackOnly()
        clearSynthTimeout()
        clearPlaybackTimeout()
        pendingSynthFile = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun stopMediaPlayerOnly() {
        try {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    /**
     * ColorOS can report a MediaPlayer as started while its app-owned route is
     * silent. Decode the PCM WAV directly into an AudioTrack first on those
     * devices; if the format is unsupported, the MediaPlayer path remains below.
     */
    private fun playPcmWithAudioTrack(file: File, gen: Int): Boolean {
        val wav = readPcmWav(file) ?: return false
        val channelMask = when (wav.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return false
        }
        val frameSize = wav.channels * (wav.bitsPerSample / 8)
        if (frameSize <= 0 || wav.pcm.size < frameSize) return false
        val frameCount = wav.pcm.size / frameSize
        val minBuffer = AudioTrack.getMinBufferSize(
            wav.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(playbackAudioAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(wav.sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, wav.pcm.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack create failed", e)
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return false
        }

        return try {
            requestAudioFocus()
            val written = track.write(wav.pcm, 0, wav.pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written < wav.pcm.size) {
                track.release()
                false
            } else {
                track.setNotificationMarkerPosition(frameCount)
                track.setPlaybackPositionUpdateListener(
                    object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(player: AudioTrack?) {
                            main.post {
                                clearPlaybackTimeout()
                                if (audioTrack !== track) return@post
                                audioTrack = null
                                try {
                                    track.stop()
                                } catch (_: Exception) {
                                }
                                track.release()
                                try {
                                    file.delete()
                                } catch (_: Exception) {
                                }
                                if (speaking && !destroyed.get() && gen == speakGeneration.get()) {
                                    speakNext()
                                }
                            }
                        }

                        override fun onPeriodicNotification(player: AudioTrack?) = Unit
                    },
                )
                audioTrack = track
                track.play()
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.w(TAG, "AudioTrack playState=${track.playState}; falling back")
                    audioTrack = null
                    try {
                        track.stop()
                    } catch (_: Exception) {
                    }
                    track.release()
                    return false
                }
                val knownDurationMs =
                    (frameCount * 1000L) / wav.sampleRate.coerceAtLeast(1)
                val playTimeoutMs = TtsPlaybackWatchdog.timeoutMs(
                    knownDurationMs = knownDurationMs,
                    chars = pendingChunkEnd - pendingChunkStart,
                )
                schedulePlaybackTimeout(gen, playTimeoutMs)
                emitChunkRangePlaying(gen)
                emitChunkPlaybackInfo(gen, knownDurationMs)
                Log.i(
                    TAG,
                    "AudioTrack started size=${file.length()} rate=${wav.sampleRate} channels=${wav.channels} durationMs=$knownDurationMs watchdogMs=$playTimeoutMs",
                )
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack play failed", e)
            try {
                track.release()
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun stopAudioTrackOnly() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.stop()
        } catch (_: Exception) {
        }
        try {
            track.release()
        } catch (_: Exception) {
        }
    }

    private data class PcmWav(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcm: ByteArray,
    )

    private fun readPcmWav(file: File): PcmWav? {
        if (file.length() < 44L || file.length() > MAX_WAV_BYTES) return null
        return try {
            BufferedInputStream(file.inputStream()).use { input ->
                val riff = ByteArray(12)
                if (!readFully(input, riff)) return null
                if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(riff, 8, 4, Charsets.US_ASCII) != "WAVE"
                ) return null

                var formatCode = 0
                var channels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var pcm: ByteArray? = null

                while (true) {
                    val chunkHeader = ByteArray(8)
                    if (!readFully(input, chunkHeader)) break
                    val chunkSize = readLe32(chunkHeader, 4)
                    if (chunkSize < 0) return null
                    when (String(chunkHeader, 0, 4, Charsets.US_ASCII)) {
                        "fmt " -> {
                            if (chunkSize < 16 || chunkSize > 4096) return null
                            val fmt = ByteArray(chunkSize)
                            if (!readFully(input, fmt)) return null
                            formatCode = readU16(fmt, 0)
                            channels = readU16(fmt, 2)
                            sampleRate = readLe32(fmt, 4)
                            bitsPerSample = readU16(fmt, 14)
                        }
                        "data" -> {
                            if (chunkSize > MAX_PCM_BYTES) return null
                            val data = ByteArray(chunkSize)
                            if (!readFully(input, data)) return null
                            pcm = data
                        }
                        else -> {
                            if (!skipFully(input, chunkSize.toLong())) return null
                        }
                    }
                    if ((chunkSize and 1) != 0 && !skipFully(input, 1L)) return null
                    if (formatCode != 0 && pcm != null) break
                }

                val data = pcm ?: return null
                if (formatCode != 1 || channels !in 1..2 || sampleRate <= 0 ||
                    bitsPerSample != 16
                ) return null
                PcmWav(sampleRate, channels, bitsPerSample, data)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WAV parse failed", e)
            null
        }
    }

    private fun readFully(input: BufferedInputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count <= 0) return false
            offset += count
        }
        return true
    }

    private fun skipFully(input: BufferedInputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() >= 0) {
                remaining--
            } else {
                return false
            }
        }
        return true
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun clearCacheQuietly() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    /**
     * A few OEM engines can leave synthesizeToFile pending without sending either
     * onDone or onError. Keep the reader recoverable instead of leaving it stuck.
     */
    private fun scheduleSynthTimeout(file: File, gen: Int) {
        clearSynthTimeout()
        synthTimeout = Runnable {
            if (pendingSynthFile == file && speaking && !destroyed.get() && gen == speakGeneration.get()) {
                Log.w(TAG, "synthesize timeout file=${file.name}")
                try {
                    file.delete()
                } catch (_: Exception) {
                }
                fallbackCurrentChunk(gen, "synthesize timeout")
            }
        }.also { main.postDelayed(it, SYNTH_TIMEOUT_MS) }
    }

    private fun clearSynthTimeout() {
        synthTimeout?.let(main::removeCallbacks)
        synthTimeout = null
    }

    /**
     * ColorOS AudioTrack markers (and some MediaPlayer completions) can stall forever.
     * Advance the queue so the reader does not stay stuck in Speaking.
     */
    private fun schedulePlaybackTimeout(gen: Int, timeoutMs: Long) {
        clearPlaybackTimeout()
        playbackTimeout = Runnable {
            if (!speaking || destroyed.get() || gen != speakGeneration.get()) return@Runnable
            Log.w(TAG, "playback timeout gen=$gen after=${timeoutMs}ms; advancing")
            stopMediaPlayerOnly()
            stopAudioTrackOnly()
            speakNext()
        }.also { main.postDelayed(it, timeoutMs) }
    }

    private fun clearPlaybackTimeout() {
        playbackTimeout?.let(main::removeCallbacks)
        playbackTimeout = null
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAudioAttributes())
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
            Log.d(TAG, "audioFocus granted=$hasAudioFocus")
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

    /**
     * ColorOS-family builds may silence app-owned MEDIA playback while allowing the
     * accessibility speech route. Other devices keep the conventional music route.
     */
    private fun playbackAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(if (isColorOsFamily()) {
                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
            } else {
                AudioAttributes.USAGE_MEDIA
            })
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun isColorOsFamily(): Boolean {
        val identity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.PRODUCT,
            Build.DEVICE,
            Build.FINGERPRINT,
        )
            .joinToString(" ")
            .lowercase()
        return listOf("oneplus", "oppo", "realme", "oplus", "coloros", "heytap")
            .any(identity::contains)
    }

    /** Notify UI of the chunk that just began audible playback (main-thread callback). */
    private fun emitChunkRangePlaying(gen: Int) {
        if (destroyed.get() || previewing || !speaking) return
        if (gen != speakGeneration.get()) return
        val start = pendingChunkStart
        val end = pendingChunkEnd
        if (end <= start) return
        fun fire() {
            if (destroyed.get() || previewing || !speaking) return
            if (gen != speakGeneration.get()) return
            onChunkRange(start, end)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            fire()
        } else {
            main.post { fire() }
        }
    }

    /** Notify UI when app-owned playback has a trustworthy media duration. */
    private fun emitChunkPlaybackInfo(gen: Int, durationMs: Long?) {
        val duration = durationMs?.takeIf { it > 1L } ?: return
        if (destroyed.get() || previewing || !speaking) return
        if (gen != speakGeneration.get()) return
        val start = pendingChunkStart
        val end = pendingChunkEnd
        if (end <= start) return
        fun fire() {
            if (destroyed.get() || previewing || !speaking) return
            if (gen != speakGeneration.get()) return
            onChunkPlayback(start, end, duration)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            fire()
        } else {
            main.post { fire() }
        }
    }

    private fun postState(state: ReaderTtsState) {
        main.post {
            if (!destroyed.get()) {
                Log.i(
                    TAG,
                    "state=$state ready=$engineReady speaking=$speaking pendingStart=$pendingStart",
                )
                onState(state)
            }
        }
    }

    companion object {
        private const val TAG = "YueJianReaderTts"
        private const val PREVIEW_TEXT = "这是一段朗读测试。如果你能听到这句话，说明当前语音引擎和应用音频输出正常。"
        private const val SYNTH_TIMEOUT_MS = 12_000L
        private const val MAX_WAV_BYTES = 16L * 1024L * 1024L
        private const val MAX_PCM_BYTES = 15 * 1024 * 1024

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
