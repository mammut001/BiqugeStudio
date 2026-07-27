package app.maoyankanshu.novel.selfuse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local book detail page, replacing the legacy server-backed detail endpoint. */
public final class BookDetailActivity extends Activity {
    public static final String EXTRA_ID = "book_id";
    public static final String EXTRA_EDIT = "edit_book";
    private static final int EXPORT_BOOK = 41;
    private Book book;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        book = LibraryStore.get(this).byId(getIntent().getStringExtra(EXTRA_ID));
        if (book == null) { finish(); return; }
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true); root.setPadding(pad, pad, pad, pad); root.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("返回"); back.setOnClickListener(v -> finish());
        TextView heading = text(book.title, 20, Color.DKGRAY); heading.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(70), dp(52)));
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(text(book.title, 28, Color.rgb(50, 50, 50)), margin(0, dp(24), 0, 0));
        body.addView(text(book.author, 16, Color.GRAY), margin(0, dp(8), 0, 0));
        body.addView(text("本地文本 · " + countChapters() + " 个章节 · " + book.text.length() + " 字\n" + book.progressLabel(), 15, Color.GRAY), margin(0, dp(18), 0, 0));
        body.addView(text(preview(), 16, Color.DKGRAY), margin(0, dp(26), 0, dp(16)));

        Button read = button(book.position > 0 ? "继续阅读" : "开始阅读"); read.setOnClickListener(v -> startActivity(new Intent(this, ReaderActivity.class).putExtra(ReaderActivity.EXTRA_ID, book.id)));
        body.addView(read, new LinearLayout.LayoutParams(-1, dp(56)));
        Button edit = button("编辑书名和作者"); edit.setOnClickListener(v -> editMetadata());
        body.addView(edit, margin(0, dp(10), 0, 0));
        Button export = button("导出为 TXT 文件"); export.setOnClickListener(v -> exportBook());
        body.addView(export, margin(0, dp(10), 0, 0));
        Button remove = button("从书架删除"); remove.setTextColor(Color.rgb(180, 50, 50)); remove.setOnClickListener(v -> confirmDelete());
        body.addView(remove, margin(0, dp(10), 0, dp(24)));
        setContentView(root);
        if (getIntent().getBooleanExtra(EXTRA_EDIT, false)) editMetadata();
    }

    private int countChapters() { Matcher m = Pattern.compile("(?m)^\\s*第.{1,18}[章节回].*$").matcher(book.text); int count = 0; while (m.find()) count++; return Math.max(1, count); }
    private String preview() { String flat = book.text.trim(); return flat.length() > 180 ? flat.substring(0, 180) + "…" : flat; }
    private void editMetadata() {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); int pad = dp(20); fields.setPadding(pad, 0, pad, 0);
        EditText title = new EditText(this); title.setHint("书名"); title.setSingleLine(true); title.setText(book.title);
        EditText author = new EditText(this); author.setHint("作者"); author.setSingleLine(true); author.setText(book.author);
        fields.addView(title); fields.addView(author);
        new AlertDialog.Builder(this).setTitle("编辑书籍信息").setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
            LibraryStore.get(this).updateMetadata(book.id, title.getText().toString(), author.getText().toString());
            book = LibraryStore.get(this).byId(book.id); recreate();
        }).show();
    }
    private void exportBook() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TITLE, safeFileName(book.title) + ".txt");
        startActivityForResult(intent, EXPORT_BOOK);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_BOOK || result != RESULT_OK || data == null || data.getData() == null) return;
        try { LibraryStore.get(this).exportBook(book.id, getContentResolver().openOutputStream(data.getData())); Toast.makeText(this, "TXT 文件已导出", Toast.LENGTH_SHORT).show(); }
        catch (Exception error) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show(); }
    }
    private String safeFileName(String value) { return value.replaceAll("[\\\\/:*?\"<>|]", "_"); }
    private void confirmDelete() { new AlertDialog.Builder(this).setTitle("删除书籍？").setMessage("将删除本设备中保存的《" + book.title + "》文本和阅读进度。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { LibraryStore.get(this).remove(book.id); finish(); }).show(); }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(5), 1f); return view; }
    private Button button(String label) { Button view = new Button(this); view.setText(label); view.setTextSize(16); view.setAllCaps(false); return view; }
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(l, t, r, b); return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
