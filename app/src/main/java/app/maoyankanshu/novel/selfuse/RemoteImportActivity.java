package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports a user-authorized direct TXT/EPUB download. It does not include any built-in book source. */
public final class RemoteImportActivity extends Activity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private EditText title;
    private EditText address;
    private Button download;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true); root.setPadding(pad, pad, pad, pad); root.setBackgroundColor(Color.rgb(250, 250, 250));
        TextView heading = new TextView(this); heading.setText("从直链下载"); heading.setTextSize(24); heading.setTextColor(Color.DKGRAY);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView note = new TextView(this); note.setText("仅粘贴你有权下载的 TXT 或 EPUB 文件直链。应用不会内置或抓取第三方书源。"); note.setTextSize(15); note.setTextColor(Color.GRAY); note.setLineSpacing(dp(5), 1f);
        root.addView(note, margin(0, dp(8), 0, dp(18)));
        title = new EditText(this); title.setHint("书名（可留空，自动使用文件名）"); title.setSingleLine(true); root.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));
        address = new EditText(this); address.setHint("https://example.com/my-book.epub"); address.setSingleLine(true); address.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(address, new LinearLayout.LayoutParams(-1, dp(56)));
        String suggestedTitle = getIntent().getStringExtra(EXTRA_TITLE);
        String suggestedUrl = getIntent().getStringExtra(EXTRA_URL);
        if (suggestedTitle != null) title.setText(suggestedTitle);
        if (suggestedUrl != null) address.setText(suggestedUrl);
        download = new Button(this); download.setText("下载并加入书架"); download.setAllCaps(false); download.setOnClickListener(v -> begin());
        root.addView(download, margin(0, dp(16), 0, 0));
        setContentView(root);
    }

    private void begin() {
        String rawUrl = address.getText().toString().trim();
        if (!rawUrl.startsWith("https://") && !rawUrl.startsWith("http://")) { Toast.makeText(this, "请输入 http 或 https 直链", Toast.LENGTH_SHORT).show(); return; }
        download.setEnabled(false); download.setText("正在下载…");
        new Thread(() -> {
            try {
                URL url = new URL(rawUrl); HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(true); connection.setRequestProperty("User-Agent", "BiqugeSelfUse/1.0");
                int code = connection.getResponseCode(); if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                String contentType = connection.getContentType();
                byte[] data; try (InputStream input = connection.getInputStream()) { data = readAll(input); } finally { connection.disconnect(); }
                boolean epub = rawUrl.toLowerCase().contains(".epub") || contentType != null && contentType.contains("epub");
                String text = epub ? parseEpub(data) : decodeText(data);
                if (text.trim().isEmpty()) throw new IllegalStateException("empty");
                String bookTitle = title.getText().toString().trim();
                if (bookTitle.isEmpty()) bookTitle = fileName(rawUrl, epub ? "EPUB 导入" : "TXT 导入");
                final String importedTitle = bookTitle;
                LibraryStore.get(this).add(importedTitle, epub ? "直链 EPUB" : "直链 TXT", text);
                runOnUiThread(() -> { Toast.makeText(this, "已加入《" + importedTitle + "》", Toast.LENGTH_SHORT).show(); finish(); });
            } catch (Exception error) {
                Log.e("BiqugeRemoteImport", "Unable to import direct file", error);
                runOnUiThread(() -> { Toast.makeText(this, "下载或导入失败：" + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show(); download.setEnabled(true); download.setText("下载并加入书架"); });
            }
        }).start();
    }

    private byte[] readAll(InputStream input) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) { if (out.size() + n > MAX_BYTES) throw new IllegalStateException("too large"); out.write(buffer, 0, n); } return out.toByteArray(); }
    private String decodeText(byte[] data) {
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe) return new String(data, 2, data.length - 2, Charset.forName("UTF-16LE"));
        if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff) return new String(data, 2, data.length - 2, Charset.forName("UTF-16BE"));
        int offset = data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf ? 3 : 0;
        String utf8 = new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
        return utf8.indexOf('\ufffd') >= 0 ? new String(data, offset, data.length - offset, Charset.forName("GB18030")) : utf8;
    }
    private String parseEpub(byte[] data) throws Exception { return EpubReader.read(new java.io.ByteArrayInputStream(data)); }
    private String fileName(String url, String fallback) { String name = url.substring(url.lastIndexOf('/') + 1).replaceFirst("[?].*$", "").replaceFirst("\\.[^.]+$", ""); return name.isEmpty() ? fallback : name; }
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(l, t, r, b); return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
