package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import app.maoyankanshu.novel.selfuse.ui.reader.Chapter;
import app.maoyankanshu.novel.selfuse.ui.reader.ChapterIndex;
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath;
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressiveTextOpen;
import app.maoyankanshu.novel.selfuse.ui.reader.TtsSpeechChunks;

/**
 * Legacy Java reader: continuous TTS, TTS rate, and auto-scroll.
 * Primary UI is Compose {@link ReaderActivity}.
 *
 * <p>ANR hardening (TTS / open path):
 * <ul>
 *   <li>Book body loaded off the main thread via {@link LibraryStore#getForReading}.</li>
 *   <li>Never binds multi‑MB text into one {@link TextView} — sliding character window only.</li>
 *   <li>{@link TextToSpeech#setLanguage} runs off the main thread (known multi‑second binder).</li>
 *   <li>TTS speaks short sentence chunks ({@link TtsSpeechChunks}), not 2k+ char blocks.</li>
 * </ul>
 */
public final class LegacyReaderActivity extends Activity {
    public static final String EXTRA_ID = "book_id";
    private static final String TAG = "YueJianLegacyReader";

    /** Max chars shown in the ScrollView TextView at once (layout stays O(window)). */
    private static final int WINDOW_CHARS = 32_000;
    /** When scroll / speech approaches this edge distance, rebind the window. */
    private static final int WINDOW_REBIND_MARGIN = 4_000;
    /** Debounce window rebind so fling-scroll does not thrash setText on main. */
    private static final long REBIND_DEBOUNCE_MS = 90L;

    private final List<Chapter> chapters = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "legacy-reader-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private String bookId;
    private String bookTitle = "";
    private int savedPosition;
    /** Full body once loaded; empty while opening. */
    private String bodyText = "";
    private int windowStart;
    private boolean bodyReady;

    private TextView text;
    private TextView loadingLabel;
    private ProgressBar loadingBar;
    private ScrollView scroll;
    private int textSize = 18;
    private int currentChapter;
    private ReaderPreferences preferences;
    private TextToSpeech speaker;
    private boolean speechReady;
    private boolean readingAloud;
    private int speechOffset;
    private Button speakButton;
    private boolean autoScrolling;
    private Button autoButton;
    private long resumeAt;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private TextView footerTime;
    private TextView footerProgress;
    private boolean menuVisible;
    private TextView titleView;
    private TextView ttsStatusLabel;
    private final Runnable debouncedRebind = this::maybeRebindWindowFromScrollNow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        bookId = getIntent().getStringExtra(EXTRA_ID);
        if (bookId == null || bookId.isEmpty()) {
            finish();
            return;
        }
        preferences = ReaderPreferences.get(this);
        textSize = preferences.fontSize();
        buildShellUi();
        startTtsEngineAsync();
        openBookAsync(bookId);
    }

    private void buildShellUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        int theme = preferences.theme();
        boolean night = theme == ReaderPreferences.THEME_NIGHT;
        root.setBackgroundColor(backgroundFor(theme));
        applyBrightness(preferences.brightness());
        int pad = dp(16);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(pad, 0, pad, 0);
        toolbar.setBackgroundColor(Color.rgb(38, 38, 38));
        Button back = button(getString(R.string.reader_back));
        back.setOnClickListener(v -> finish());
        titleView = new TextView(this);
        titleView.setText(R.string.reader_legacy_advanced);
        titleView.setTextSize(18);
        titleView.setTextColor(Color.LTGRAY);
        titleView.setGravity(Gravity.CENTER);
        Button toc = button(getString(R.string.reader_toc));
        toc.setOnClickListener(v -> showContents());
        Button bookmarks = button(getString(R.string.reader_bookmarks));
        bookmarks.setOnClickListener(v -> showBookmarks());
        Button appearance = button(getString(R.string.reader_appearance));
        appearance.setOnClickListener(v -> showAppearance());
        Button find = button(getString(R.string.reader_find));
        find.setOnClickListener(v -> showFind());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(titleView, new LinearLayout.LayoutParams(0, dp(52), 1));
        toolbar.addView(appearance, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(find, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(bookmarks, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(toc, new LinearLayout.LayoutParams(dp(70), dp(52)));
        root.addView(toolbar);

        scroll = new ScrollView(this);
        scroll.setPadding(pad, pad, pad, pad);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        loadingBar = new ProgressBar(this);
        loadingLabel = new TextView(this);
        loadingLabel.setText(R.string.reader_open_loading);
        loadingLabel.setTextColor(textColorFor(theme));
        loadingLabel.setTextSize(16);
        loadingLabel.setPadding(0, dp(24), 0, dp(12));
        body.addView(loadingBar);
        body.addView(loadingLabel);
        text = new TextView(this);
        text.setTextColor(textColorFor(theme));
        text.setLineSpacing(dp(8), 1f);
        text.setTextSize(textSize);
        text.setVisibility(android.view.View.GONE);
        body.addView(text);
        scroll.addView(body);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (!bodyReady) return;
            // Debounce rebind — rapid setText during fling was a jank/ANR risk.
            mainHandler.removeCallbacks(debouncedRebind);
            mainHandler.postDelayed(debouncedRebind, REBIND_DEBOUNCE_MS);
            updateCurrentChapter();
            updateFooter();
        });
        // Tap body only toggles chrome when controls are intentionally immersive;
        // classic TTS keeps chrome visible by default (see setMenuVisible at end of build).
        text.setOnClickListener(v -> setMenuVisible(!menuVisible));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        ttsStatusLabel = new TextView(this);
        ttsStatusLabel.setText(R.string.reader_tts_status_preparing);
        ttsStatusLabel.setTextSize(12);
        ttsStatusLabel.setTextColor(night ? Color.rgb(180, 180, 180) : Color.rgb(90, 90, 90));
        ttsStatusLabel.setPadding(pad, dp(4), pad, dp(2));
        ttsStatusLabel.setMaxLines(1);
        root.addView(ttsStatusLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(pad, 0, pad, 0);
        footerTime = new TextView(this);
        footerTime.setTextSize(12);
        footerTime.setTextColor(night ? Color.rgb(155, 155, 155) : Color.GRAY);
        footerProgress = new TextView(this);
        footerProgress.setTextSize(12);
        footerProgress.setTextColor(night ? Color.rgb(155, 155, 155) : Color.GRAY);
        footerProgress.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        footer.addView(footerTime, new LinearLayout.LayoutParams(0, -1, 1));
        footer.addView(footerProgress, new LinearLayout.LayoutParams(dp(96), -1));
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));

        controls = new LinearLayout(this);
        controls.setPadding(pad, 0, pad, 0);
        controls.setBackgroundColor(Color.rgb(38, 38, 38));
        addControl(controls, getString(R.string.reader_prev_chapter), v -> goChapter(currentChapter - 1));
        addControl(controls, getString(R.string.reader_font_smaller), v -> changeTextSize(-2));
        addControl(controls, getString(R.string.reader_font_larger), v -> changeTextSize(2));
        addControl(controls, night ? getString(R.string.reader_day) : getString(R.string.reader_night_toggle),
                v -> {
                    preferences.setNightMode(!preferences.nightMode());
                    recreate();
                });
        speakButton = button(getString(R.string.reader_tts));
        speakButton.setTextSize(13);
        speakButton.setOnClickListener(v -> toggleSpeech());
        speakButton.setOnLongClickListener(v -> {
            showTtsSettings();
            return true;
        });
        controls.addView(speakButton, new LinearLayout.LayoutParams(0, -1, 1));
        autoButton = button(getString(R.string.reader_auto));
        autoButton.setTextSize(13);
        autoButton.setOnClickListener(v -> toggleAutoScroll());
        controls.addView(autoButton, new LinearLayout.LayoutParams(0, -1, 1));
        addControl(controls, getString(R.string.reader_next_chapter), v -> goChapter(currentChapter + 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(56)));
        setContentView(root);
        // Classic/TTS surface: keep chrome visible so 朗读 is never “empty UI / can't find button”.
        setMenuVisible(true);
        updateSpeakButtonUi();
        updateFooter();
    }

    /**
     * TTS engine construction is async; {@link TextToSpeech#setLanguage} is moved off the
     * main thread because OEM engines often block for seconds (classic ANR with “Wait”).
     */
    private void startTtsEngineAsync() {
        try {
            speaker = new TextToSpeech(getApplicationContext(), status -> {
                if (destroyed.get()) return;
                if (status != TextToSpeech.SUCCESS || speaker == null) {
                    speechReady = false;
                    mainHandler.post(this::updateSpeakButtonUi);
                    return;
                }
                final TextToSpeech engine = speaker;
                final float rate = preferences.ttsRate();
                ioExecutor.execute(() -> {
                    int lang = TextToSpeech.LANG_NOT_SUPPORTED;
                    try {
                        lang = engine.setLanguage(Locale.CHINA);
                        if (lang < 0) {
                            lang = engine.setLanguage(Locale.CHINESE);
                        }
                        if (lang < 0) {
                            lang = engine.setLanguage(Locale.getDefault());
                        }
                        engine.setSpeechRate(rate);
                    } catch (Exception e) {
                        Log.w(TAG, "TTS setLanguage failed", e);
                        lang = TextToSpeech.LANG_NOT_SUPPORTED;
                    }
                    final boolean ok = lang >= TextToSpeech.LANG_AVAILABLE;
                    mainHandler.post(() -> {
                        if (destroyed.get() || speaker != engine) return;
                        speechReady = ok;
                        if (ok) {
                            try {
                                engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                    @Override
                                    public void onStart(String utteranceId) {
                                    }

                                    @Override
                                    public void onDone(String utteranceId) {
                                        mainHandler.post(() -> continueSpeech());
                                    }

                                    @Override
                                    public void onError(String utteranceId) {
                                        mainHandler.post(() -> stopSpeech());
                                    }
                                });
                            } catch (Exception e) {
                                Log.w(TAG, "TTS listener failed", e);
                                speechReady = false;
                            }
                        }
                        updateSpeakButtonUi();
                    });
                });
            });
        } catch (Exception e) {
            Log.w(TAG, "TTS create failed", e);
            speechReady = false;
            speaker = null;
            updateSpeakButtonUi();
        }
    }

    private void openBookAsync(String id) {
        // Capture string on main (or UI thread context) before IO work.
        final String fullChapterLabel = getString(R.string.reader_chapter_full);
        ioExecutor.execute(() -> {
            try {
                LibraryStore store = LibraryStore.getForReading(getApplicationContext());
                LibraryStore.BookRecord record = store.recordById(id);
                if (record == null) {
                    mainHandler.post(this::finishIfAlive);
                    return;
                }
                byte[] bytes = store.readBookBytes(id);
                if (bytes == null) {
                    mainHandler.post(this::finishIfAlive);
                    return;
                }
                String full = ProgressiveTextOpen.INSTANCE.decodeFullText(bytes);
                if (full == null || full.isEmpty()) {
                    full = new String(bytes, StandardCharsets.UTF_8);
                }
                final String body = full;
                final String title = record.title;
                final int position = record.position;
                // Chapter scan off main — regex over multi‑MB must not run on UI thread.
                final List<Chapter> found = ChapterIndex.INSTANCE.findChapters(body, fullChapterLabel);
                mainHandler.post(() -> onBookLoaded(title, position, body, found));
            } catch (Exception e) {
                Log.e(TAG, "open book failed", e);
                mainHandler.post(() -> {
                    if (destroyed.get()) return;
                    Toast.makeText(this, R.string.reader_open_failed, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void finishIfAlive() {
        if (!destroyed.get() && !isFinishing()) finish();
    }

    private void onBookLoaded(
            String title,
            int position,
            String body,
            List<Chapter> found
    ) {
        if (destroyed.get()) return;
        bookTitle = title != null ? title : "";
        savedPosition = ProgressMath.INSTANCE.clampProgress(position);
        bodyText = body != null ? body : "";
        chapters.clear();
        if (found != null) {
            chapters.addAll(found);
        }
        if (chapters.isEmpty()) {
            chapters.add(new Chapter(getString(R.string.reader_chapter_full), 0));
        }
        if (titleView != null) titleView.setText(bookTitle);
        bodyReady = true;
        if (loadingBar != null) loadingBar.setVisibility(android.view.View.GONE);
        if (loadingLabel != null) loadingLabel.setVisibility(android.view.View.GONE);
        text.setVisibility(android.view.View.VISIBLE);

        int offset = offsetForProgress(savedPosition);
        bindWindowAround(offset, /* scrollToOffset */ true);
        updateCurrentChapter();
        updateSpeakButtonUi();
        updateFooter();
    }

    /** Map 0…1000 progress onto a character offset in [bodyText]. */
    private int offsetForProgress(int progress) {
        int len = bodyText.length();
        if (len <= 0) return 0;
        int p = ProgressMath.INSTANCE.clampProgress(progress);
        return (int) ((p / 1000.0) * (len - 1));
    }

    private int progressForOffset(int offset) {
        int len = bodyText.length();
        if (len <= 1) return 0;
        int o = Math.max(0, Math.min(offset, len - 1));
        return ProgressMath.INSTANCE.clampProgress(Math.round((o / (float) (len - 1)) * 1000f));
    }

    /**
     * Bind a sliding window of [bodyText] into the TextView so layout stays bounded.
     *
     * @param globalOffset character offset in the full body to keep on screen
     * @param scrollToOffset after layout, scroll so [globalOffset] is near the top
     */
    private void bindWindowAround(int globalOffset, boolean scrollToOffset) {
        if (text == null || bodyText.isEmpty()) {
            if (text != null) text.setText("");
            windowStart = 0;
            return;
        }
        int len = bodyText.length();
        int center = Math.max(0, Math.min(globalOffset, len));
        int start = Math.max(0, center - WINDOW_CHARS / 3);
        int end = Math.min(len, start + WINDOW_CHARS);
        if (end - start < WINDOW_CHARS) {
            start = Math.max(0, end - WINDOW_CHARS);
        }
        windowStart = start;
        final String slice = bodyText.substring(start, end);
        text.setText(slice);
        if (scrollToOffset) {
            final int local = Math.max(0, center - start);
            scroll.post(() -> {
                if (destroyed.get() || text.getLayout() == null) return;
                int line = text.getLayout().getLineForOffset(Math.min(local, slice.length()));
                scroll.scrollTo(0, text.getLayout().getLineTop(line));
                updateFooter();
            });
        }
    }

    private void maybeRebindWindowFromScrollNow() {
        if (destroyed.get() || !bodyReady || text == null || text.getLayout() == null) return;
        int localTop = text.getLayout().getOffsetForHorizontal(
                text.getLayout().getLineForVertical(Math.max(0, scroll.getScrollY())), 0);
        int global = windowStart + localTop;
        int windowEnd = windowStart + text.getText().length();
        boolean nearStart = global - windowStart < WINDOW_REBIND_MARGIN && windowStart > 0;
        boolean nearEnd = windowEnd - global < WINDOW_REBIND_MARGIN && windowEnd < bodyText.length();
        if (nearStart || nearEnd) {
            int keepY = scroll.getScrollY();
            int keepLocal = localTop;
            bindWindowAround(global, false);
            scroll.post(() -> {
                if (destroyed.get() || text.getLayout() == null) return;
                int line = text.getLayout().getLineForOffset(
                        Math.min(keepLocal, text.getText().length()));
                int y = text.getLayout().getLineTop(line);
                // Prefer character-aligned scroll; fall back to previous y if layout not ready.
                scroll.scrollTo(0, y > 0 ? y : keepY);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bookId != null) {
            ReadingHistory.get(this).record(bookId);
        }
        resumeAt = android.os.SystemClock.elapsedRealtime();
        updateFooter();
    }

    @Override
    protected void onPause() {
        stopAutoScroll();
        stopSpeech();
        if (resumeAt > 0) {
            ReadingStats.add(this, android.os.SystemClock.elapsedRealtime() - resumeAt);
        }
        super.onPause();
        saveProgress();
    }

    @Override
    protected void onDestroy() {
        destroyed.set(true);
        stopAutoScroll();
        readingAloud = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (speaker != null) {
            try {
                speaker.stop();
                speaker.shutdown();
            } catch (Exception ignored) {
            }
            speaker = null;
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void showContents() {
        if (!bodyReady || chapters.isEmpty()) return;
        String[] names = new String[chapters.size()];
        for (int i = 0; i < chapters.size(); i++) names[i] = chapters.get(i).getTitle();
        new AlertDialog.Builder(this).setTitle(R.string.reader_toc)
                .setItems(names, (dialog, index) -> goChapter(index)).show();
    }

    private void showFind() {
        if (!bodyReady) return;
        EditText query = new EditText(this);
        query.setHint(R.string.reader_find_hint);
        query.setSingleLine(true);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(query, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(R.string.reader_find_title).setView(box)
                .setNegativeButton(R.string.reader_cancel, null)
                .setPositiveButton(R.string.reader_search, (dialog, which) ->
                        runFindOffMain(query.getText().toString()))
                .show();
    }

    /** Full-book lowercase scan off main thread (multi‑MB toLowerCase was an ANR source). */
    private void runFindOffMain(String raw) {
        final String keyword = raw == null ? "" : raw.trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, R.string.reader_find_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        final String snapshot = bodyText;
        loadingLabel.setVisibility(android.view.View.VISIBLE);
        loadingLabel.setText(R.string.reader_find_loading);
        ioExecutor.execute(() -> {
            String content = snapshot.toLowerCase(Locale.ROOT);
            String needle = keyword.toLowerCase(Locale.ROOT);
            ArrayList<Integer> positions = new ArrayList<>();
            int from = 0;
            while (positions.size() < 50) {
                int at = content.indexOf(needle, from);
                if (at < 0) break;
                positions.add(at);
                from = at + Math.max(1, needle.length());
            }
            ArrayList<String> labels = new ArrayList<>();
            for (int at : positions) {
                int start = Math.max(0, at - 20);
                int end = Math.min(snapshot.length(), at + keyword.length() + 36);
                labels.add(snapshot.substring(start, at)
                        + "【" + snapshot.substring(at, at + keyword.length()) + "】"
                        + snapshot.substring(at + keyword.length(), end).replace('\n', ' '));
            }
            mainHandler.post(() -> {
                if (destroyed.get()) return;
                loadingLabel.setVisibility(android.view.View.GONE);
                if (positions.isEmpty()) {
                    Toast.makeText(this, getString(R.string.reader_find_none, keyword), Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                String title = positions.size() == 50
                        ? getString(R.string.reader_find_results_capped, keyword, 50)
                        : getString(R.string.reader_find_results, keyword, positions.size());
                new AlertDialog.Builder(this).setTitle(title)
                        .setItems(labels.toArray(new String[0]), (d, index) -> {
                            jumpToGlobalOffset(positions.get(index));
                            updateCurrentChapter();
                        }).show();
            });
        });
    }

    private void showBookmarks() {
        if (bookId == null) return;
        List<BookmarkStore.Bookmark> items = BookmarkStore.get(this).list(bookId);
        String[] labels = new String[items.size() + 1];
        labels[0] = getString(R.string.reader_add_bookmark);
        for (int i = 0; i < items.size(); i++) {
            labels[i + 1] = getString(
                    R.string.reader_bookmark_list_item,
                    items.get(i).label,
                    Math.round(items.get(i).progress / 10f));
        }
        new AlertDialog.Builder(this).setTitle(R.string.reader_bookmarks).setItems(labels, (dialog, index) -> {
            if (index == 0) {
                int progress = currentProgress();
                String chapter = chapters.isEmpty()
                        ? getString(R.string.reader_current_position)
                        : chapters.get(currentChapter).getTitle();
                BookmarkStore.get(this).add(bookId, progress, chapter);
            } else {
                scrollToProgress(items.get(index - 1).progress);
            }
        }).setNegativeButton(
                items.isEmpty() ? getString(R.string.reader_close) : getString(R.string.reader_manage),
                (dialog, which) -> {
                    if (!items.isEmpty()) manageBookmarks(items);
                }).show();
    }

    private void manageBookmarks(List<BookmarkStore.Bookmark> items) {
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = getString(
                    R.string.reader_bookmark_manage_item,
                    items.get(i).label,
                    Math.round(items.get(i).progress / 10f));
        }
        new AlertDialog.Builder(this).setTitle(R.string.reader_manage_bookmarks)
                .setItems(labels, (dialog, index) ->
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.reader_delete_bookmark_title)
                                .setMessage(items.get(index).label)
                                .setNegativeButton(R.string.reader_cancel, null)
                                .setPositiveButton(R.string.reader_delete, (d, w) ->
                                        BookmarkStore.get(this).remove(bookId, index))
                                .show())
                .show();
    }

    private void showAppearance() {
        String[] themes = {
                getString(R.string.reader_theme_paper),
                getString(R.string.reader_theme_night),
                getString(R.string.reader_theme_eye)
        };
        new AlertDialog.Builder(this).setTitle(R.string.reader_appearance_title)
                .setSingleChoiceItems(themes, preferences.theme(), (dialog, index) -> {
                    preferences.setTheme(index);
                    dialog.dismiss();
                    recreate();
                })
                .setNeutralButton(R.string.reader_brightness_title, (dialog, which) -> showBrightness())
                .setNegativeButton(R.string.reader_cancel, null).show();
    }

    private void showBrightness() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), 0, dp(24), 0);
        TextView note = new TextView(this);
        note.setText(R.string.reader_brightness_note);
        note.setTextColor(Color.GRAY);
        note.setTextSize(14);
        SeekBar slider = new SeekBar(this);
        slider.setMax(92);
        float previous = preferences.brightness();
        int initial = previous < 0 ? 42 : Math.round((previous - .08f) * 100f);
        slider.setProgress(Math.max(0, Math.min(92, initial)));
        body.addView(note);
        body.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) applyBrightness(.08f + progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        new AlertDialog.Builder(this).setTitle(R.string.reader_brightness_title).setView(body)
                .setNegativeButton(R.string.reader_brightness_system, (d, w) -> {
                    preferences.setBrightness(-1);
                    applyBrightness(-1);
                })
                .setNeutralButton(R.string.reader_cancel, (d, w) -> applyBrightness(previous))
                .setPositiveButton(R.string.reader_brightness_save, (d, w) ->
                        preferences.setBrightness(.08f + slider.getProgress() / 100f))
                .show();
    }

    private void goChapter(int index) {
        if (!bodyReady || index < 0 || index >= chapters.size()) return;
        currentChapter = index;
        jumpToGlobalOffset(chapters.get(index).getStart());
    }

    private void updateCurrentChapter() {
        if (!bodyReady || chapters.isEmpty()) return;
        int offset = currentGlobalOffset();
        currentChapter = ChapterIndex.INSTANCE.chapterAtOffset(chapters, offset);
    }

    private void setMenuVisible(boolean visible) {
        menuVisible = visible;
        if (toolbar != null) {
            toolbar.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (controls != null) {
            controls.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void updateFooter() {
        if (footerTime == null || footerProgress == null) return;
        footerTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        int pct = ProgressMath.INSTANCE.percentOfProgress(currentProgress());
        footerProgress.setText(pct + "%");
    }

    private void scrollToProgress(int progress) {
        if (!bodyReady) return;
        jumpToGlobalOffset(offsetForProgress(progress));
    }

    private void jumpToGlobalOffset(int globalOffset) {
        int len = bodyText.length();
        int o = Math.max(0, Math.min(globalOffset, len));
        bindWindowAround(o, true);
        updateCurrentChapter();
        updateFooter();
    }

    private void saveProgress() {
        if (bookId == null || !bodyReady) return;
        LibraryStore.getForReading(this).savePosition(bookId, currentProgress());
    }

    /** Character-based 0…1000 progress (independent of the sliding TextView window). */
    private int currentProgress() {
        if (!bodyReady || bodyText.isEmpty()) return savedPosition;
        return progressForOffset(currentGlobalOffset());
    }

    private int currentGlobalOffset() {
        if (!bodyReady) return 0;
        if (text == null || text.getLayout() == null) {
            return windowStart;
        }
        int local = text.getLayout().getOffsetForHorizontal(
                text.getLayout().getLineForVertical(Math.max(0, scroll.getScrollY())), 0);
        return windowStart + Math.max(0, local);
    }

    private void toggleSpeech() {
        if (!bodyReady) {
            Toast.makeText(this, R.string.reader_tts_body_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!speechReady || speaker == null) {
            Toast.makeText(this, R.string.reader_tts_unavailable, Toast.LENGTH_SHORT).show();
            updateSpeakButtonUi();
            return;
        }
        if (readingAloud) {
            stopSpeech();
            return;
        }
        speechOffset = currentGlobalOffset();
        readingAloud = true;
        updateSpeakButtonUi();
        speakNextChunk();
    }

    /** TTS button + status strip: never leave blank / dead-looking chrome. */
    private void updateSpeakButtonUi() {
        if (speakButton == null) return;
        if (readingAloud) {
            speakButton.setEnabled(true);
            speakButton.setText(R.string.reader_tts_stop);
            if (ttsStatusLabel != null) {
                ttsStatusLabel.setText(R.string.reader_tts_status_speaking);
            }
            return;
        }
        if (!bodyReady) {
            speakButton.setEnabled(false);
            speakButton.setText(R.string.reader_tts_waiting_body);
            if (ttsStatusLabel != null) {
                ttsStatusLabel.setText(R.string.reader_open_loading);
            }
            return;
        }
        if (!speechReady || speaker == null) {
            speakButton.setEnabled(false);
            speakButton.setText(R.string.reader_tts_preparing);
            if (ttsStatusLabel != null) {
                ttsStatusLabel.setText(R.string.reader_tts_status_preparing);
            }
            return;
        }
        speakButton.setEnabled(true);
        speakButton.setText(R.string.reader_tts);
        if (ttsStatusLabel != null) {
            ttsStatusLabel.setText(R.string.reader_tts_status_idle);
        }
    }

    private void speakNextChunk() {
        if (!readingAloud || speaker == null) return;
        if (speechOffset >= bodyText.length()) {
            stopSpeech();
            return;
        }
        int end = TtsSpeechChunks.INSTANCE.nextChunkEnd(bodyText, speechOffset);
        if (end <= speechOffset) {
            stopSpeech();
            return;
        }
        String chunk = bodyText.substring(speechOffset, end);
        speechOffset = end;
        try {
            // Short chunks only — long speak() + main-thread setLanguage were ANR vectors.
            int result = speaker.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "reader-" + end);
            if (result == TextToSpeech.ERROR) {
                stopSpeech();
            }
        } catch (Exception e) {
            Log.w(TAG, "speak failed", e);
            stopSpeech();
        }
    }

    private void continueSpeech() {
        if (!readingAloud) return;
        // Keep the window under the spoken offset without full-book layout.
        if (speechOffset < windowStart + WINDOW_REBIND_MARGIN
                || speechOffset > windowStart + Math.max(0, text.getText().length() - WINDOW_REBIND_MARGIN)) {
            bindWindowAround(speechOffset, true);
        } else {
            scrollToLocalOffset(speechOffset - windowStart);
        }
        speakNextChunk();
    }

    private void scrollToLocalOffset(int localOffset) {
        scroll.post(() -> {
            if (destroyed.get() || text.getLayout() == null) return;
            int o = Math.max(0, Math.min(localOffset, text.getText().length()));
            int line = text.getLayout().getLineForOffset(o);
            scroll.smoothScrollTo(0, text.getLayout().getLineTop(line));
            updateFooter();
        });
    }

    private void stopSpeech() {
        readingAloud = false;
        if (speaker != null) {
            try {
                speaker.stop();
            } catch (Exception ignored) {
            }
        }
        updateSpeakButtonUi();
    }

    private void showTtsSettings() {
        String[] labels = {"0.75×", "1.0×", "1.25×", "1.5×"};
        float[] rates = {.75f, 1f, 1.25f, 1.5f};
        int selected = 1;
        float current = preferences.ttsRate();
        for (int i = 0; i < rates.length; i++) {
            if (Math.abs(rates[i] - current) < .05f) selected = i;
        }
        new AlertDialog.Builder(this).setTitle(R.string.reader_tts_rate_title)
                .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                    preferences.setTtsRate(rates[index]);
                    if (speaker != null) {
                        try {
                            speaker.setSpeechRate(rates[index]);
                        } catch (Exception ignored) {
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.reader_cancel, null)
                .show();
    }

    private void toggleAutoScroll() {
        if (autoScrolling) stopAutoScroll();
        else startAutoScroll();
    }

    private void startAutoScroll() {
        if (!bodyReady) return;
        autoScrolling = true;
        autoButton.setText(R.string.reader_auto_stop);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!autoScrolling || destroyed.get()) return;
                int maximum = Math.max(0, text.getHeight() - scroll.getHeight());
                if (scroll.getScrollY() >= maximum) {
                    // Advance window if more body remains.
                    int global = currentGlobalOffset();
                    if (global < bodyText.length() - 1) {
                        bindWindowAround(global + WINDOW_CHARS / 4, true);
                        mainHandler.postDelayed(this, 30);
                        return;
                    }
                    stopAutoScroll();
                    return;
                }
                scroll.scrollBy(0, 2);
                mainHandler.postDelayed(this, 30);
            }
        });
    }

    private void stopAutoScroll() {
        autoScrolling = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (autoButton != null) autoButton.setText(R.string.reader_auto);
    }

    private void addControl(LinearLayout row, String label, android.view.View.OnClickListener listener) {
        Button b = button(label);
        b.setTextSize(13);
        b.setOnClickListener(listener);
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private void changeTextSize(int change) {
        textSize = Math.max(14, Math.min(30, textSize + change));
        preferences.setFontSize(textSize);
        text.setTextSize(textSize);
        // Rebind so line metrics stay aligned with global offset.
        if (bodyReady) bindWindowAround(currentGlobalOffset(), true);
    }

    private int backgroundFor(int theme) {
        if (theme == ReaderPreferences.THEME_NIGHT) return Color.rgb(35, 35, 35);
        if (theme == ReaderPreferences.THEME_EYE_CARE) return Color.rgb(236, 232, 201);
        return Color.rgb(250, 247, 240);
    }

    private int textColorFor(int theme) {
        return theme == ReaderPreferences.THEME_NIGHT
                ? Color.rgb(225, 225, 225)
                : Color.rgb(55, 45, 35);
    }

    private void applyBrightness(float brightness) {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        getWindow().setAttributes(attributes);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
