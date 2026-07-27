package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Imports one user-authorized HTML article as an offline local book. */
public final class WebImportActivity extends Activity {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private EditText title;
    private EditText address;
    private Button importButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); int pad = dp(18);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true); root.setPadding(pad, pad, pad, pad); root.setBackgroundColor(Color.rgb(250, 250, 250));
        TextView heading = new TextView(this); heading.setText("从网页导入文章"); heading.setTextSize(24); heading.setTextColor(Color.DKGRAY); root.addView(heading, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView note = new TextView(this); note.setText("仅导入你拥有或获授权使用的公开网页内容。一次导入一个网页；不内置网站规则、不绕过验证码或访问限制。动态网页可能无法完整导入。"); note.setTextSize(15); note.setTextColor(Color.GRAY); note.setLineSpacing(dp(5), 1f); root.addView(note, margin(0, dp(8), 0, dp(18)));
        title = new EditText(this); title.setHint("书名（可留空，使用网页标题）"); title.setSingleLine(true); root.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));
        address = new EditText(this); address.setHint("https://example.com/article"); address.setSingleLine(true); address.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI); root.addView(address, new LinearLayout.LayoutParams(-1, dp(56)));
        importButton = new Button(this); importButton.setText("导入为离线书籍"); importButton.setAllCaps(false); importButton.setOnClickListener(v -> begin()); root.addView(importButton, margin(0, dp(16), 0, 0)); setContentView(root);
    }
    private void begin() {
        String rawUrl = address.getText().toString().trim();
        String requestedTitle = title.getText().toString().trim();
        if (!rawUrl.startsWith("https://") && !rawUrl.startsWith("http://")) { Toast.makeText(this, "请输入 http 或 https 网页地址", Toast.LENGTH_SHORT).show(); return; }
        importButton.setEnabled(false); importButton.setText("正在读取网页…");
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection(); connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BiqugeSelfUse/1.0"); connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                int code = connection.getResponseCode(); if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                byte[] data; try (InputStream input = connection.getInputStream()) { data = readAll(input); } finally { connection.disconnect(); }
                String html = decode(data); String body = toText(html); if (body.length() < 20) throw new IllegalStateException("empty");
                String name = requestedTitle; if (name.isEmpty()) name = pageTitle(html); if (name.isEmpty()) name = "网页导入";
                String source = "网页导入\n" + rawUrl;
                final String saved = name; LibraryStore.get(this).add(saved, source, body + "\n\n——\n来源：" + rawUrl);
                runOnUiThread(() -> { Toast.makeText(this, "已加入《" + saved + "》", Toast.LENGTH_SHORT).show(); finish(); });
            } catch (Exception error) { runOnUiThread(() -> { Toast.makeText(this, "网页无法导入；请检查地址、网络或访问权限", Toast.LENGTH_LONG).show(); importButton.setEnabled(true); importButton.setText("导入为离线书籍"); }); }
        }).start();
    }
    private byte[] readAll(InputStream input) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) { if (out.size() + n > MAX_BYTES) throw new IllegalStateException("too large"); out.write(buffer, 0, n); } return out.toByteArray(); }
    private String decode(byte[] data) {
        String utf8 = new String(data, StandardCharsets.UTF_8); return utf8.indexOf('\ufffd') >= 0 ? new String(data, Charset.forName("GB18030")) : utf8;
    }
    private String pageTitle(String html) { Matcher titleTag = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html); return titleTag.find() ? clean(titleTag.group(1)) : ""; }
    private String toText(String html) { return clean(html.replaceAll("(?is)<script[^>]*>.*?</script>", "").replaceAll("(?is)<style[^>]*>.*?</style>", "").replaceAll("(?is)<(br|/p|/div|/h[1-6]|/li)[^>]*>", "\n").replaceAll("(?s)<[^>]+>", "")); }
    private String clean(String value) { return value.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replaceAll("[ \\t]*\\n[ \\t]*", "\n").replaceAll("\\n{3,}", "\n\n").trim(); }
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(l, t, r, b); return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
