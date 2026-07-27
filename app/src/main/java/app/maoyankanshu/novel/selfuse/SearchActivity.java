package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

/** Search the device-only shelf and import user-selected UTF-8 TXT files. */
public final class SearchActivity extends Activity {
    private static final String TAG = "BiqugeSearch";
    public static final String EXTRA_IMPORT = "open_import";
    private static final int PICK_TEXT = 8;
    private LinearLayout results;
    private EditText query;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout row = new LinearLayout(this);
        query = new EditText(this);
        query.setHint("搜索本地书架"); query.setSingleLine(true);
        Button search = new Button(this); search.setText("搜索"); search.setOnClickListener(v -> render());
        row.addView(query, new LinearLayout.LayoutParams(0, dp(52), 1));
        row.addView(search, new LinearLayout.LayoutParams(dp(72), dp(52)));
        root.addView(row);

        Button importButton = new Button(this);
        importButton.setText("导入本地 TXT / EPUB 文件"); importButton.setAllCaps(false); importButton.setOnClickListener(v -> pickText());
        root.addView(importButton, new LinearLayout.LayoutParams(-1, dp(52)));
        Button publicDomain = new Button(this);
        publicDomain.setText("搜索维基文库公版文本"); publicDomain.setAllCaps(false); publicDomain.setOnClickListener(v -> searchWikisource());
        root.addView(publicDomain, new LinearLayout.LayoutParams(-1, dp(52)));
        Button featured = new Button(this);
        featured.setText("导入推荐公版《三国演义》"); featured.setAllCaps(false);
        featured.setOnClickListener(v -> importWikisource("三國演義"));
        root.addView(featured, new LinearLayout.LayoutParams(-1, dp(52)));
        Button completeEpub = new Button(this);
        completeEpub.setText("下载完整 EPUB《三国演义》"); completeEpub.setAllCaps(false);
        completeEpub.setOnClickListener(v -> startActivity(new Intent(this, RemoteImportActivity.class)
                .putExtra(RemoteImportActivity.EXTRA_TITLE, "三國演義")
                .putExtra(RemoteImportActivity.EXTRA_URL, "https://ws-export.wmcloud.org/?format=epub&page=%E4%B8%89%E5%9C%8B%E6%BC%94%E7%BE%A9&lang=zh")));
        root.addView(completeEpub, new LinearLayout.LayoutParams(-1, dp(52)));

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        render();
        if (getIntent().getBooleanExtra(EXTRA_IMPORT, false)) pickText();
    }

    private void render() {
        results.removeAllViews();
        String term = query.getText().toString().trim().toLowerCase();
        List<Book> books = LibraryStore.get(this).books();
        for (Book book : books) {
            if (!term.isEmpty() && !book.title.toLowerCase().contains(term) && !book.author.toLowerCase().contains(term)) continue;
            Button item = new Button(this);
            item.setText(book.title + "\n" + book.author + " · " + book.progressLabel());
            item.setAllCaps(false); item.setTextColor(Color.DKGRAY); item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setOnClickListener(v -> startActivity(new Intent(this, BookDetailActivity.class).putExtra(BookDetailActivity.EXTRA_ID, book.id)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(66)); p.setMargins(0, 0, 0, dp(8));
            results.addView(item, p);
        }
        if (results.getChildCount() == 0) { TextView empty = new TextView(this); empty.setText("没有找到书籍"); empty.setTextSize(16); empty.setTextColor(Color.GRAY); results.addView(empty); }
    }

    /** Searches the Chinese Wikisource MediaWiki API for public-domain/free-license texts. */
    private void searchWikisource() {
        String term = query.getText().toString().trim();
        if (term.isEmpty()) { query.setError("请输入书名、作者或关键词"); return; }
        results.removeAllViews();
        TextView loading = new TextView(this); loading.setText("正在搜索维基文库…"); loading.setTextSize(16); loading.setTextColor(Color.GRAY); results.addView(loading);
        new Thread(() -> {
            try {
                String api = "https://zh.wikisource.org/w/api.php?action=query&list=search&format=json&srlimit=10&srsearch=" + URLEncoder.encode(term, "UTF-8");
                JSONObject response = new JSONObject(readUrl(api));
                JSONArray items = response.getJSONObject("query").getJSONArray("search");
                List<OpenText> found = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    found.add(new OpenText(item.getString("title"), item.optString("snippet", "").replaceAll("(?s)<[^>]+>", "")));
                }
                runOnUiThread(() -> renderWikisourceResults(found));
            } catch (Exception error) {
                Log.e(TAG, "Wikisource search failed", error);
                runOnUiThread(() -> { results.removeAllViews(); addResultMessage("维基文库搜索失败，请检查网络后重试"); });
            }
        }).start();
    }

    private void renderWikisourceResults(List<OpenText> found) {
        results.removeAllViews();
        addResultMessage("维基文库 · 公共领域或自由许可文本");
        if (found.isEmpty()) { addResultMessage("没有找到结果"); return; }
        for (OpenText item : found) {
            Button result = new Button(this);
            result.setText(item.title + (item.summary.isEmpty() ? "" : "\n" + item.summary));
            result.setAllCaps(false); result.setTextColor(Color.DKGRAY); result.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            result.setOnClickListener(v -> importWikisource(item.title));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(76)); p.setMargins(0, 0, 0, dp(8)); results.addView(result, p);
        }
    }

    private void importWikisource(String pageTitle) {
        Toast.makeText(this, "正在导入《" + pageTitle + "》", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String api = "https://zh.wikisource.org/w/api.php?action=parse&prop=text&format=json&page=" + URLEncoder.encode(pageTitle, "UTF-8");
                JSONObject parsed = new JSONObject(readUrl(api));
                String html = parsed.getJSONObject("parse").getJSONObject("text").getString("*");
                String body = html.replaceAll("(?is)<script[^>]*>.*?</script>", "").replaceAll("(?is)<style[^>]*>.*?</style>", "").replaceAll("(?s)<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
                if (body.isEmpty()) throw new IllegalStateException("empty");
                String attribution = "\n\n——\n来源：中文维基文库《" + pageTitle + "》\n链接：https://zh.wikisource.org/wiki/" + pageTitle.replace(' ', '_') + "\n许可：CC BY-SA 4.0（请保留署名与许可信息）";
                LibraryStore.get(this).add(pageTitle, "中文维基文库", body + attribution);
                runOnUiThread(() -> { Toast.makeText(this, "已加入书架", Toast.LENGTH_SHORT).show(); render(); });
            } catch (Exception error) {
                Log.e(TAG, "Wikisource import failed: " + pageTitle, error);
                runOnUiThread(() -> Toast.makeText(this, "导入失败，请稍后重试", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String readUrl(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("User-Agent", "BiqugeSelfUse/1.0 (personal offline reader)");
        try (InputStream input = connection.getInputStream()) { return readText(input); }
        finally { connection.disconnect(); }
    }
    private void addResultMessage(String message) { TextView view = new TextView(this); view.setText(message); view.setTextSize(16); view.setTextColor(Color.GRAY); view.setPadding(0, dp(12), 0, dp(12)); results.addView(view); }

    private void pickText() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/epub+zip"}); intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_TEXT);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TEXT || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            String name = "本地导入";
            String raw = uri.getLastPathSegment(); if (raw != null && !raw.isEmpty()) name = raw.replaceFirst("\\.[^.]+$", "");
            boolean epub = raw != null && raw.toLowerCase().endsWith(".epub");
            String content = epub ? readEpub(stream) : readText(stream);
            if (content.trim().isEmpty()) throw new IllegalArgumentException("empty");
            LibraryStore.get(this).add(name, epub ? "本地 EPUB" : "本地 TXT", content);
            render();
            Toast.makeText(this, "已导入《" + name + "》", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { Toast.makeText(this, "无法读取该文本文件", Toast.LENGTH_SHORT).show(); }
    }

    private String readText(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        byte[] data = output.toByteArray();
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe) return new String(data, 2, data.length - 2, Charset.forName("UTF-16LE"));
        if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff) return new String(data, 2, data.length - 2, Charset.forName("UTF-16BE"));
        int offset = data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf ? 3 : 0;
        String utf8 = new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
        return utf8.indexOf('\ufffd') >= 0 ? new String(data, offset, data.length - offset, Charset.forName("GB18030")) : utf8;
    }

    /** Basic EPUB extraction: concatenate XHTML chapters in archive order and strip markup. */
    private String readEpub(InputStream stream) throws Exception {
        return EpubReader.read(stream);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private static final class OpenText { final String title; final String summary; OpenText(String title, String summary) { this.title = title; this.summary = summary; } }
}
