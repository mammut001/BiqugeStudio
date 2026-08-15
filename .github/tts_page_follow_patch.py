from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


controller = Path("app/src/main/java/app/maoyankanshu/novel/selfuse/ui/reader/ReaderTtsController.kt")
s = controller.read_text(encoding="utf-8")

old = '''    private val onChunkRange: (start: Int, endExclusive: Int) -> Unit = { _, _ -> },
    /** Fired on main when engine list is refreshed via [TextToSpeech.getEngines]. */
'''
new = '''    private val onChunkRange: (start: Int, endExclusive: Int) -> Unit = { _, _ -> },
    /**
     * Fired on main when app-owned synthesized playback starts and its duration is known.
     * Direct engine.speak fallback has no reliable duration and intentionally does not fire this.
     */
    private val onChunkPlayback: (start: Int, endExclusive: Int, durationMs: Long) -> Unit =
        { _, _, _ -> },
    /** Fired on main when engine list is refreshed via [TextToSpeech.getEngines]. */
'''
s = replace_once(s, old, new, "controller callback")

old = '''            schedulePlaybackTimeout(gen, playTimeoutMs)
            emitChunkRangePlaying(gen)
            Log.i(
'''
new = '''            schedulePlaybackTimeout(gen, playTimeoutMs)
            emitChunkRangePlaying(gen)
            emitChunkPlaybackInfo(gen, knownDurationMs)
            Log.i(
'''
s = replace_once(s, old, new, "media playback callback")

old = '''                schedulePlaybackTimeout(gen, playTimeoutMs)
                emitChunkRangePlaying(gen)
                Log.i(
'''
new = '''                schedulePlaybackTimeout(gen, playTimeoutMs)
                emitChunkRangePlaying(gen)
                emitChunkPlaybackInfo(gen, knownDurationMs)
                Log.i(
'''
s = replace_once(s, old, new, "audio track playback callback")

marker = '''    private fun postState(state: ReaderTtsState) {
'''
insert = '''    /** Notify UI when app-owned playback has a trustworthy media duration. */
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
'''
s = replace_once(s, marker, insert, "playback info helper")
controller.write_text(s, encoding="utf-8")

reader = Path("app/src/main/java/app/maoyankanshu/novel/selfuse/ui/reader/ReaderScreen.kt")
s = reader.read_text(encoding="utf-8")

old = '''    var ttsHighlightRange by remember { mutableStateOf<IntRange?>(null) }
    /** Body snapshot used by the active TTS session (offsets from controller.start). */
'''
new = '''    var ttsHighlightRange by remember { mutableStateOf<IntRange?>(null) }
    var ttsChunkPlayback by remember(book.id) { mutableStateOf<TtsChunkPlayback?>(null) }
    /** Body snapshot used by the active TTS session (offsets from controller.start). */
'''
s = replace_once(s, old, new, "reader playback state")

old = '''                onChunkRange = { start, _ ->
                    ttsChunkJump.value(start)
                    val body = ttsSpeakBodyRef.value
'''
new = '''                onChunkRange = { start, _ ->
                    // Cancel any delayed turns from the previous audio chunk immediately.
                    ttsChunkPlayback = null
                    ttsChunkJump.value(start)
                    val body = ttsSpeakBodyRef.value
'''
s = replace_once(s, old, new, "chunk range reset")

old = '''                    }
                },
                onEnginesDiscovered = { list ->
'''
new = '''                    }
                },
                onChunkPlayback = { start, endExclusive, durationMs ->
                    ttsChunkPlayback = TtsChunkPlayback(
                        start = start,
                        endExclusive = endExclusive,
                        durationMs = durationMs,
                        startedElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                },
                onEnginesDiscovered = { list ->
'''
s = replace_once(s, old, new, "reader playback callback")

old = '''                ttsHighlightRange = null
                ttsSpeakBodyRef.value = ""
                ttsState = ReaderTtsState.Ready
'''
new = '''                ttsHighlightRange = null
                ttsChunkPlayback = null
                ttsSpeakBodyRef.value = ""
                ttsState = ReaderTtsState.Ready
'''
s = replace_once(s, old, new, "dispose playback clear")

old = '''    LaunchedEffect(ttsState) {
        when (ttsState) {
            ReaderTtsState.Ready, ReaderTtsState.Unavailable -> {
                ttsHighlightRange = null
            }
            else -> Unit
        }
    }

    // Load installed engines (Oplus / Google / 讯飞 / …) for the picker.
'''
new = '''    LaunchedEffect(ttsState) {
        if (ttsState != ReaderTtsState.Speaking) {
            // Playback has stopped or the engine is rebinding; stale timed turns must die.
            ttsChunkPlayback = null
        }
        when (ttsState) {
            ReaderTtsState.Ready, ReaderTtsState.Unavailable -> {
                ttsHighlightRange = null
            }
            else -> Unit
        }
    }

    // App-owned WAV playback has a real duration even though Android gives us no character-level
    // range callbacks for it. Map page boundaries inside the active chunk onto that duration so a
    // paragraph can visually continue onto page 2 before the next paragraph/chunk begins.
    LaunchedEffect(
        ttsChunkPlayback,
        ttsState,
        useApproxPaging,
        approxCharsPerPage,
        pageStarts,
        book.text.length,
    ) {
        val playback = ttsChunkPlayback ?: return@LaunchedEffect
        if (ttsState != ReaderTtsState.Speaking) return@LaunchedEffect
        val cues = if (useApproxPaging) {
            TtsPageFollow.cuesForApproximatePages(
                textLength = book.text.length,
                charsPerPage = approxCharsPerPage,
                chunkStart = playback.start,
                chunkEndExclusive = playback.endExclusive,
                durationMs = playback.durationMs,
            )
        } else {
            TtsPageFollow.cuesForExactPages(
                pageStarts = pageStarts,
                textLength = book.text.length,
                chunkStart = playback.start,
                chunkEndExclusive = playback.endExclusive,
                durationMs = playback.durationMs,
            )
        }
        for (cue in cues) {
            val elapsed = (
                android.os.SystemClock.elapsedRealtime() - playback.startedElapsedRealtimeMs
                ).coerceAtLeast(0L)
            val waitMs = cue.atMillis - elapsed
            if (waitMs > 0L) delay(waitMs)
            if (ttsState != ReaderTtsState.Speaking || ttsChunkPlayback != playback) {
                return@LaunchedEffect
            }
            // Never fight manual navigation. Only advance if TTS still owns the page expected
            // immediately before this boundary; the next chunk can resume following naturally.
            if (pagerState.currentPage != cue.fromPage) return@LaunchedEffect
            pagerState.scrollToPage(cue.page)
        }
    }

    // Load installed engines (Oplus / Google / 讯飞 / …) for the picker.
'''
s = replace_once(s, old, new, "timed page follow effect")
reader.write_text(s, encoding="utf-8")
