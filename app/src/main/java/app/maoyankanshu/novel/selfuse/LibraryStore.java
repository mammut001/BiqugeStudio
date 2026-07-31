package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Device-only library metadata plus private text files, safe for long TXT imports. */
public final class LibraryStore {
    public static final long MAX_SINGLE_ENTRY_BYTES = 32 * 1024 * 1024L; // 32 MiB
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 128 * 1024 * 1024L; // 128 MiB

    private static final String PREFS = "local_library";
    private static final String KEY = "books_v2";
    private final Context context;
    private final SharedPreferences prefs;
    private final File filesDir;

    /** Pre-rename product author on the built-in seed book only. */
    private static final String LEGACY_SEED_AUTHOR = "笔趣阁（自用）";

    private LibraryStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.filesDir = this.context.getFilesDir();
        initSeedIfNeeded();
    }

    LibraryStore(SharedPreferences prefs, File filesDir, String defaultAppName, String defaultWelcomeTitle, String defaultWelcomeBody) {
        this.context = null;
        this.prefs = prefs;
        this.filesDir = filesDir;
        if (!prefs.contains(KEY)) {
            add(defaultWelcomeTitle, defaultAppName, defaultWelcomeBody);
        }
    }

    private void initSeedIfNeeded() {
        if (!prefs.contains(KEY)) {
            String appName = this.context.getString(R.string.app_name);
            add(
                    this.context.getString(R.string.welcome_book_title),
                    appName,
                    this.context.getString(R.string.welcome_book_body, appName)
            );
        } else {
            migrateLegacySeedAuthor();
        }
    }

    /**
     * If the built-in seed book still uses the old product name as author, update only that
     * author field to {@link R.string#app_name}. Never changes titles or user-imported books.
     */
    private void migrateLegacySeedAuthor() {
        if (context == null) return;
        String appName = context.getString(R.string.app_name);
        String seedTitle = context.getString(R.string.welcome_book_title);
        if (LEGACY_SEED_AUTHOR.equals(appName)) return;
        List<Book> all = books();
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            Book book = all.get(i);
            if (seedTitle.equals(book.title) && LEGACY_SEED_AUTHOR.equals(book.author)) {
                all.set(i, new Book(book.id, book.title, appName, book.text, book.position));
                changed = true;
            }
        }
        if (changed) save(all);
    }

    public static LibraryStore get(Context context) { return new LibraryStore(context); }

    public List<Book> books() {
        List<Book> books = new ArrayList<>();
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return books;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4) continue;
            try {
                String text = readText(values[0]);
                if (text != null) {
                    String title = decode(values[1]);
                    String author = decode(values[2]);
                    int pos = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                    books.add(new Book(values[0], title, author, text, pos));
                }
            } catch (Exception ignored) { }
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
        long totalUncompressedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                long entryBytes = 0;
                while ((count = zip.read(buffer)) != -1) {
                    entryBytes += count;
                    totalUncompressedBytes += count;
                    if (entryBytes > MAX_SINGLE_ENTRY_BYTES) {
                        throw new IllegalArgumentException("ZIP entry exceeds maximum allowed size of 32 MiB");
                    }
                    if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("ZIP total uncompressed size exceeds maximum allowed limit of 128 MiB");
                    }
                    data.write(buffer, 0, count);
                }
                if ("library.txt".equals(entry.getName())) manifest = data.toString("UTF-8");
                else if (entry.getName().startsWith("books/") && entry.getName().endsWith(".txt")) texts.put(entry.getName().substring(6, entry.getName().length() - 4), data.toByteArray());
                zip.closeEntry();
            }
        }
        if (manifest == null) throw new IOException("备份文件损坏或缺失 library.txt 清单");
        List<Book> all = books(); int imported = 0;
        for (String row : manifest.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4 || !texts.containsKey(values[0])) continue;
            try {
                String title = decode(values[1]);
                String author = decode(values[2]);
                int position = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                all.add(new Book(UUID.randomUUID().toString(), title, author, new String(texts.get(values[0]), StandardCharsets.UTF_8), position));
                imported++;
            } catch (Exception ignored) { }
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
        if (prefs.edit() != null) {
            prefs.edit().putString(KEY, output.toString()).apply();
        }
    }

    private File bookDir() {
        File dir = new File(filesDir, "books");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

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

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
