package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy Java reader: continuous TTS, TTS rate, and auto-scroll. Primary UI is Compose ReaderActivity. */
public final class LegacyReaderActivity extends Activity {
    public static final String EXTRA_ID = "book_id";
    private final List<Chapter> chapters = new ArrayList<>();
    private Book book;
    private TextView text;
    private ScrollView scroll;
    private int textSize = 18;
    private int currentChapter;
    private ReaderPreferences preferences;
    private TextToSpeech speaker;
    private boolean speechReady;
    private boolean readingAloud;
    private int speechOffset;
    private Button speakButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoScrolling;
    private Button autoButton;
    private long resumeAt;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private TextView footerTime;
    private TextView footerProgress;
    private boolean menuVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        book = LibraryStore.get(this).byId(getIntent().getStringExtra(EXTRA_ID));
        if (book == null) { finish(); return; }
        preferences = ReaderPreferences.get(this);
        textSize = preferences.fontSize();
        findChapters();
        speaker = new TextToSpeech(this, status -> {
            speechReady = status == TextToSpeech.SUCCESS;
            if (speechReady) {
                speaker.setLanguage(Locale.CHINA); speaker.setSpeechRate(preferences.ttsRate());
                speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { runOnUiThread(() -> continueSpeech()); }
                    @Override public void onError(String utteranceId) { runOnUiThread(() -> stopSpeech()); }
                });
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        int theme = preferences.theme();
        boolean night = theme == ReaderPreferences.THEME_NIGHT;
        root.setBackgroundColor(backgroundFor(theme));
        applyBrightness(preferences.brightness());
        int pad = dp(16);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(pad, 0, pad, 0);
        toolbar.setBackgroundColor(Color.rgb(38, 38, 38));
        Button back = button(getString(R.string.reader_back)); back.setOnClickListener(v -> finish());
        TextView title = new TextView(this); title.setText(book.title); title.setTextSize(18); title.setTextColor(Color.LTGRAY); title.setGravity(Gravity.CENTER);
        Button toc = button(getString(R.string.reader_toc)); toc.setOnClickListener(v -> showContents());
        Button bookmarks = button(getString(R.string.reader_bookmarks)); bookmarks.setOnClickListener(v -> showBookmarks());
        Button appearance = button(getString(R.string.reader_appearance)); appearance.setOnClickListener(v -> showAppearance());
        Button find = button(getString(R.string.reader_find)); find.setOnClickListener(v -> showFind());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        toolbar.addView(appearance, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(find, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(bookmarks, new LinearLayout.LayoutParams(dp(70), dp(52)));
        toolbar.addView(toc, new LinearLayout.LayoutParams(dp(70), dp(52)));
        root.addView(toolbar);

        scroll = new ScrollView(this); scroll.setPadding(pad, pad, pad, pad);
        text = new TextView(this); text.setText(book.text); text.setTextColor(textColorFor(theme)); text.setLineSpacing(dp(8), 1f); text.setTextSize(textSize);
        scroll.addView(text);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> { updateCurrentChapter(); updateFooter(); });
        text.setOnClickListener(v -> setMenuVisible(!menuVisible));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(pad, 0, pad, 0);
        footerTime = new TextView(this); footerTime.setTextSize(12); footerTime.setTextColor(night ? Color.rgb(155, 155, 155) : Color.GRAY);
        footerProgress = new TextView(this); footerProgress.setTextSize(12); footerProgress.setTextColor(night ? Color.rgb(155, 155, 155) : Color.GRAY); footerProgress.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        footer.addView(footerTime, new LinearLayout.LayoutParams(0, -1, 1));
        footer.addView(footerProgress, new LinearLayout.LayoutParams(dp(74), -1));
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));

        controls = new LinearLayout(this); controls.setPadding(pad, 0, pad, 0);
        controls.setBackgroundColor(Color.rgb(38, 38, 38));
        addControl(controls, getString(R.string.reader_prev_chapter), v -> goChapter(currentChapter - 1));
        addControl(controls, getString(R.string.reader_font_smaller), v -> changeTextSize(-2));
        addControl(controls, getString(R.string.reader_font_larger), v -> changeTextSize(2));
        addControl(controls, night ? getString(R.string.reader_day) : getString(R.string.reader_night_toggle), v -> { preferences.setNightMode(!preferences.nightMode()); recreate(); });
        speakButton = button(getString(R.string.reader_tts)); speakButton.setTextSize(13); speakButton.setOnClickListener(v -> toggleSpeech());
        speakButton.setOnLongClickListener(v -> { showTtsSettings(); return true; }); controls.addView(speakButton, new LinearLayout.LayoutParams(0, -1, 1));
        autoButton = button(getString(R.string.reader_auto)); autoButton.setTextSize(13); autoButton.setOnClickListener(v -> toggleAutoScroll()); controls.addView(autoButton, new LinearLayout.LayoutParams(0, -1, 1));
        addControl(controls, getString(R.string.reader_next_chapter), v -> goChapter(currentChapter + 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(56)));
        setContentView(root);
        restoreProgress();
        setMenuVisible(false);
        scroll.post(this::updateFooter);
    }

    @Override protected void onResume() { super.onResume(); ReadingHistory.get(this).record(book.id); resumeAt = android.os.SystemClock.elapsedRealtime(); updateFooter(); }
    @Override protected void onPause() { stopAutoScroll(); stopSpeech(); if (resumeAt > 0) ReadingStats.add(this, android.os.SystemClock.elapsedRealtime() - resumeAt); super.onPause(); saveProgress(); }
    @Override protected void onDestroy() { stopAutoScroll(); readingAloud = false; if (speaker != null) { speaker.stop(); speaker.shutdown(); } super.onDestroy(); }

    private void findChapters() {
        Matcher matcher = Pattern.compile("(?m)^\\s*第.{1,18}[章节回].*$", Pattern.UNICODE_CASE).matcher(book.text);
        while (matcher.find()) chapters.add(new Chapter(matcher.group().trim(), matcher.start()));
        if (chapters.isEmpty()) chapters.add(new Chapter(getString(R.string.reader_chapter_full), 0));
    }
    private void showContents() {
        String[] names = new String[chapters.size()]; for (int i = 0; i < chapters.size(); i++) names[i] = chapters.get(i).title;
        new AlertDialog.Builder(this).setTitle(R.string.reader_toc).setItems(names, (dialog, index) -> goChapter(index)).show();
    }
    private void showFind() {
        EditText query = new EditText(this); query.setHint(R.string.reader_find_hint); query.setSingleLine(true); query.setSelectAllOnFocus(false);
        int pad = dp(20); LinearLayout box = new LinearLayout(this); box.setPadding(pad, 0, pad, 0); box.addView(query, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(R.string.reader_find_title).setView(box)
                .setNegativeButton(R.string.reader_cancel, null)
                .setPositiveButton(R.string.reader_search, (dialog, which) -> showFindResults(query.getText().toString())).show();
    }
    private void showFindResults(String raw) {
        String keyword = raw == null ? "" : raw.trim();
        if (keyword.isEmpty()) { android.widget.Toast.makeText(this, R.string.reader_find_empty, android.widget.Toast.LENGTH_SHORT).show(); return; }
        String content = book.text.toLowerCase(java.util.Locale.ROOT), needle = keyword.toLowerCase(java.util.Locale.ROOT);
        java.util.ArrayList<Integer> positions = new java.util.ArrayList<>(); int from = 0;
        while (positions.size() < 50) { int at = content.indexOf(needle, from); if (at < 0) break; positions.add(at); from = at + Math.max(1, needle.length()); }
        if (positions.isEmpty()) { android.widget.Toast.makeText(this, getString(R.string.reader_find_none, keyword), android.widget.Toast.LENGTH_SHORT).show(); return; }
        String[] labels = new String[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            int at = positions.get(i), start = Math.max(0, at - 20), end = Math.min(book.text.length(), at + keyword.length() + 36);
            labels[i] = book.text.substring(start, at) + "【" + book.text.substring(at, at + keyword.length()) + "】" + book.text.substring(at + keyword.length(), end).replace('\n', ' ');
        }
        String title = positions.size() == 50
                ? getString(R.string.reader_find_results_capped, keyword, 50)
                : getString(R.string.reader_find_results, keyword, positions.size());
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(labels, (dialog, index) -> { scrollToOffset(positions.get(index)); updateCurrentChapter(); }).show();
    }
    private void showBookmarks() {
        java.util.List<BookmarkStore.Bookmark> items = BookmarkStore.get(this).list(book.id);
        String[] labels = new String[items.size() + 1];
        labels[0] = getString(R.string.reader_add_bookmark);
        for (int i = 0; i < items.size(); i++) {
            labels[i + 1] = getString(R.string.reader_bookmark_list_item, items.get(i).label, Math.round(items.get(i).progress / 10f));
        }
        new AlertDialog.Builder(this).setTitle(R.string.reader_bookmarks).setItems(labels, (dialog, index) -> {
            if (index == 0) {
                int progress = currentProgress();
                String chapter = chapters.isEmpty() ? getString(R.string.reader_current_position) : chapters.get(currentChapter).title;
                BookmarkStore.get(this).add(book.id, progress, chapter);
            } else scrollToProgress(items.get(index - 1).progress);
        }).setNegativeButton(items.isEmpty() ? getString(R.string.reader_close) : getString(R.string.reader_manage), (dialog, which) -> {
            if (!items.isEmpty()) manageBookmarks(items);
        }).show();
    }
    private void manageBookmarks(java.util.List<BookmarkStore.Bookmark> items) {
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = getString(R.string.reader_bookmark_manage_item, items.get(i).label, Math.round(items.get(i).progress / 10f));
        }
        new AlertDialog.Builder(this).setTitle(R.string.reader_manage_bookmarks).setItems(labels, (dialog, index) ->
                new AlertDialog.Builder(this).setTitle(R.string.reader_delete_bookmark_title).setMessage(items.get(index).label)
                        .setNegativeButton(R.string.reader_cancel, null)
                        .setPositiveButton(R.string.reader_delete, (d, w) -> BookmarkStore.get(this).remove(book.id, index)).show()).show();
    }
    private void showAppearance() {
        String[] themes = {
                getString(R.string.reader_theme_paper),
                getString(R.string.reader_theme_night),
                getString(R.string.reader_theme_eye)
        };
        new AlertDialog.Builder(this).setTitle(R.string.reader_appearance_title)
                .setSingleChoiceItems(themes, preferences.theme(), (dialog, index) -> {
                    preferences.setTheme(index); dialog.dismiss(); recreate();
                })
                .setNeutralButton(R.string.reader_brightness_title, (dialog, which) -> showBrightness())
                .setNegativeButton(R.string.reader_cancel, null).show();
    }
    private void showBrightness() {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24), 0, dp(24), 0);
        TextView note = new TextView(this); note.setText(R.string.reader_brightness_note); note.setTextColor(Color.GRAY); note.setTextSize(14);
        SeekBar slider = new SeekBar(this); slider.setMax(92);
        float previous = preferences.brightness(); int initial = previous < 0 ? 42 : Math.round((previous - .08f) * 100f);
        slider.setProgress(Math.max(0, Math.min(92, initial))); body.addView(note); body.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if (fromUser) applyBrightness(.08f + progress / 100f); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        new AlertDialog.Builder(this).setTitle(R.string.reader_brightness_title).setView(body)
                .setNegativeButton(R.string.reader_brightness_system, (d, w) -> { preferences.setBrightness(-1); applyBrightness(-1); })
                .setNeutralButton(R.string.reader_cancel, (d, w) -> applyBrightness(previous))
                .setPositiveButton(R.string.reader_brightness_save, (d, w) -> preferences.setBrightness(.08f + slider.getProgress() / 100f)).show();
    }
    private void goChapter(int index) {
        if (index < 0 || index >= chapters.size() || text.getLayout() == null) return;
        currentChapter = index;
        int line = text.getLayout().getLineForOffset(chapters.get(index).start);
        scroll.smoothScrollTo(0, text.getLayout().getLineTop(line));
    }
    /** Keeps navigation, TTS and new bookmarks aligned with manual scrolling. */
    private void updateCurrentChapter() {
        if (text == null || text.getLayout() == null || chapters.isEmpty()) return;
        int offset = text.getLayout().getOffsetForHorizontal(text.getLayout().getLineForVertical(Math.max(0, scroll.getScrollY())), 0);
        int selected = 0;
        for (int i = 1; i < chapters.size(); i++) {
            if (chapters.get(i).start > offset) break;
            selected = i;
        }
        currentChapter = selected;
    }
    /** Original reader stays immersive: controls appear only after tapping the page. */
    private void setMenuVisible(boolean visible) {
        menuVisible = visible;
        if (toolbar != null) toolbar.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        if (controls != null) controls.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    private void updateFooter() {
        if (footerTime == null || footerProgress == null || scroll == null || text == null) return;
        footerTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        footerProgress.setText(Math.round(currentProgress() / 10f) + "%");
    }
    private void restoreProgress() {
        scrollToProgress(book.position);
    }
    private void scrollToProgress(int progress) { scroll.post(() -> { int maximum = Math.max(0, text.getHeight() - scroll.getHeight()); scroll.scrollTo(0, Math.round(maximum * (progress / 1000f))); }); }
    private void saveProgress() {
        LibraryStore.get(this).savePosition(book.id, currentProgress());
    }
    private int currentProgress() { int maximum = Math.max(1, text.getHeight() - scroll.getHeight()); return Math.round(Math.min(1f, scroll.getScrollY() / (float) maximum) * 1000); }
    private void toggleSpeech() {
        if (!speechReady) { android.widget.Toast.makeText(this, R.string.reader_tts_unavailable, android.widget.Toast.LENGTH_SHORT).show(); return; }
        if (readingAloud) { stopSpeech(); return; }
        speechOffset = currentTextOffset(); readingAloud = true; speakButton.setText(R.string.reader_tts_stop); speakNextChunk();
    }
    private void speakNextChunk() {
        if (!readingAloud || speechOffset >= book.text.length()) { stopSpeech(); return; }
        int end = Math.min(book.text.length(), speechOffset + 2200);
        int natural = Math.max(book.text.lastIndexOf('。', end), Math.max(book.text.lastIndexOf('！', end), book.text.lastIndexOf('？', end)));
        if (natural > speechOffset + 600) end = natural + 1;
        String chunk = book.text.substring(speechOffset, end); speechOffset = end;
        speaker.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "reader-" + end);
    }
    private void continueSpeech() {
        if (!readingAloud) return;
        scrollToOffset(speechOffset); speakNextChunk();
    }
    private void stopSpeech() { readingAloud = false; if (speaker != null) speaker.stop(); if (speakButton != null) speakButton.setText(R.string.reader_tts); }
    private int currentTextOffset() {
        if (text.getLayout() == null) return chapterStart();
        return text.getLayout().getOffsetForHorizontal(text.getLayout().getLineForVertical(Math.max(0, scroll.getScrollY())), 0);
    }
    private void scrollToOffset(int offset) { scroll.post(() -> { if (text.getLayout() != null) scroll.smoothScrollTo(0, text.getLayout().getLineTop(text.getLayout().getLineForOffset(Math.max(0, Math.min(offset, book.text.length()))))); }); }
    private void showTtsSettings() {
        String[] labels = {"0.75×", "1.0×", "1.25×", "1.5×"}; float[] rates = {.75f, 1f, 1.25f, 1.5f};
        int selected = 1; float current = preferences.ttsRate(); for (int i = 0; i < rates.length; i++) if (Math.abs(rates[i] - current) < .05f) selected = i;
        new AlertDialog.Builder(this).setTitle(R.string.reader_tts_rate_title).setSingleChoiceItems(labels, selected, (dialog, index) -> {
            preferences.setTtsRate(rates[index]); if (speaker != null) speaker.setSpeechRate(rates[index]); dialog.dismiss();
        }).setNegativeButton(R.string.reader_cancel, null).show();
    }
    private void toggleAutoScroll() { if (autoScrolling) stopAutoScroll(); else startAutoScroll(); }
    private void startAutoScroll() {
        autoScrolling = true; autoButton.setText(R.string.reader_auto_stop);
        handler.post(new Runnable() { @Override public void run() {
            if (!autoScrolling) return;
            int maximum = Math.max(0, text.getHeight() - scroll.getHeight());
            if (scroll.getScrollY() >= maximum) { stopAutoScroll(); return; }
            scroll.scrollBy(0, 2);
            handler.postDelayed(this, 30);
        }});
    }
    private void stopAutoScroll() { autoScrolling = false; handler.removeCallbacksAndMessages(null); if (autoButton != null) autoButton.setText(R.string.reader_auto); }
    private int chapterStart() { return chapters.isEmpty() ? 0 : chapters.get(currentChapter).start; }
    private void addControl(LinearLayout row, String label, android.view.View.OnClickListener listener) { Button b = button(label); b.setTextSize(13); b.setOnClickListener(listener); row.addView(b, new LinearLayout.LayoutParams(0, -1, 1)); }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    private void changeTextSize(int change) { textSize = Math.max(14, Math.min(30, textSize + change)); preferences.setFontSize(textSize); text.setTextSize(textSize); }
    private int backgroundFor(int theme) {
        if (theme == ReaderPreferences.THEME_NIGHT) return Color.rgb(35, 35, 35);
        if (theme == ReaderPreferences.THEME_EYE_CARE) return Color.rgb(236, 232, 201);
        return Color.rgb(250, 247, 240);
    }
    private int textColorFor(int theme) { return theme == ReaderPreferences.THEME_NIGHT ? Color.rgb(225, 225, 225) : Color.rgb(55, 45, 35); }
    private void applyBrightness(float brightness) { WindowManager.LayoutParams attributes = getWindow().getAttributes(); attributes.screenBrightness = brightness; getWindow().setAttributes(attributes); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private static final class Chapter { final String title; final int start; Chapter(String title, int start) { this.title = title; this.start = start; } }
}
