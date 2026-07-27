package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Device-only library metadata plus private text files, safe for long TXT imports. */
public final class LibraryStore {
    private static final String PREFS = "local_library";
    private static final String KEY = "books_v2";
    private final Context context;
    private final SharedPreferences prefs;

    private LibraryStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY)) {
            String appName = this.context.getString(R.string.app_name);
            add(
                    this.context.getString(R.string.welcome_book_title),
                    appName,
                    this.context.getString(R.string.welcome_book_body, appName)
            );
        }
    }

    public static LibraryStore get(Context context) { return new LibraryStore(context); }

    public List<Book> books() {
        List<Book> books = new ArrayList<>();
        String raw = prefs.getString(KEY, "");
        if (raw.isEmpty()) return books;
        for (String row : raw.split("\\n", -1)) {
            String[] values = row.split("\\|", 4);
            if (values.length != 4) continue;
            try {
                String text = readText(values[0]);
                if (text != null) books.add(new Book(values[0], decode(values[1]), decode(values[2]), text, Integer.parseInt(values[3])));
            } catch (IllegalArgumentException ignored) { }
        }
        return books;
    }

    public Book byId(String id) { for (Book book : books()) if (book.id.equals(id)) return book; return null; }
    public void add(String title, String author, String text) { List<Book> all = books(); all.add(new Book(UUID.randomUUID().toString(), title, author, text, 0)); save(all); }
    /** Changes display metadata without changing the saved text or reading position. */
    public void updateMetadata(String id, String title, String author) {
        List<Book> all = books();
        for (int i = 0; i < all.size(); i++) {
            Book old = all.get(i);
            if (old.id.equals(id)) all.set(i, new Book(old.id, cleanTitle(title, old.title), cleanTitle(author, old.author), old.text, old.position));
        }
        save(all);
    }
    /** Keeps the shelf order meaningful: a pinned book is shown first. */
    public void moveToTop(String id) {
        List<Book> all = books();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) { Book book = all.remove(i); all.add(0, book); break; }
        save(all);
    }
    public void savePosition(String id, int progress) { List<Book> all = books(); for (Book book : all) if (book.id.equals(id)) book.position = Math.max(0, Math.min(progress, 1000)); save(all); }
    public void remove(String id) { List<Book> all = books(); all.removeIf(book -> book.id.equals(id)); File file = new File(bookDir(), id + ".txt"); if (file.exists()) file.delete(); save(all); }

    /** Exports all local books and progress into a portable ZIP backup. */
    public void exportTo(OutputStream output) throws IOException {
        List<Book> books = books();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            StringBuilder manifest = new StringBuilder();
            for (Book book : books) {
                manifest.append(book.id).append('|').append(encode(book.title)).append('|').append(encode(book.author)).append('|').append(book.position).append('\n');
                zip.putNextEntry(new ZipEntry("books/" + book.id + ".txt"));
                zip.write(book.text.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("library.txt"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** Writes one book as a standard UTF-8 TXT file for use in another reader. */
    public void exportBook(String id, OutputStream output) throws IOException {
        Book book = byId(id);
        if (book == null) throw new IOException("Book not found");
        output.write(book.text.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    /** Adds books from a backup ZIP; existing local books remain untouched. */
    public int importFrom(InputStream input) throws IOException {
        Map<String, byte[]> texts = new HashMap<>();
        String manifest = null;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = zip.read(buffer)) != -1) data.write(buffer, 0, count);
                if ("library.txt".equals(entry.getName())) manifest = data.toString("UTF-8");
                else if (entry.getName().startsWith("books/") && entry.getName().endsWith(".txt")) texts.put(entry.getName().substring(6, entry.getName().length() - 4), data.toByteArray());
                zip.closeEntry();
            }
        }
        if (manifest == null) throw new IOException("Not a library backup");
        List<Book> all = books(); int imported = 0;
        for (String row : manifest.split("\\n", -1)) {
            String[] values = row.split("\\|", 4);
            if (values.length != 4 || !texts.containsKey(values[0])) continue;
            try {
                all.add(new Book(UUID.randomUUID().toString(), decode(values[1]), decode(values[2]), new String(texts.get(values[0]), StandardCharsets.UTF_8), Integer.parseInt(values[3])));
                imported++;
            } catch (IllegalArgumentException ignored) { }
        }
        save(all);
        return imported;
    }

    private void save(List<Book> books) {
        StringBuilder output = new StringBuilder();
        for (Book book : books) {
            writeText(book.id, book.text);
            output.append(book.id).append('|').append(encode(book.title)).append('|').append(encode(book.author)).append('|').append(book.position).append('\n');
        }
        prefs.edit().putString(KEY, output.toString()).apply();
    }

    private File bookDir() { File dir = new File(context.getFilesDir(), "books"); if (!dir.exists()) dir.mkdirs(); return dir; }
    private String readText(String id) {
        File file = new File(bookDir(), id + ".txt");
        if (!file.exists()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = input.read(data);
            return read < 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (IOException ignored) { return null; }
    }
    private void writeText(String id, String text) {
        try (FileOutputStream output = new FileOutputStream(new File(bookDir(), id + ".txt"))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
    }
    private static String cleanTitle(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? fallback : clean.replace('|', '｜').replace('\n', ' ');
    }
    private static String encode(String value) { return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private static String decode(String value) { return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8); }
}
