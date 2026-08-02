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
import java.util.HashMap;
import java.util.Iterator;
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
    /** Cover images live under covers/{id}.cover; same bound as EPUB extract. */
    public static final int MAX_COVER_BYTES = EpubReader.MAX_COVER_BYTES;

    private static final String PREFS = "local_library";
    private static final String KEY = "books_v2";
    private final Context context;
    private final SharedPreferences prefs;
    private final File filesDir;

    /** Pre-rename product author on the built-in seed book only. */
    private static final String LEGACY_SEED_AUTHOR = "笔趣阁（自用）";

    private LibraryStore(Context context) {
        this(context, true);
    }

    /** Reader-only store: avoid running one-time seed migrations on every page/progress update. */
    private LibraryStore(Context context, boolean runMigrations) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.filesDir = this.context.getFilesDir();
        if (runMigrations) initSeedIfNeeded();
    }

    LibraryStore(SharedPreferences prefs, File filesDir, String defaultAppName, String defaultWelcomeTitle, String defaultWelcomeBody) {
        this.context = null;
        this.prefs = prefs;
        this.filesDir = filesDir;
        if (!prefs.contains(KEY)) {
            add(defaultWelcomeTitle, defaultAppName, defaultWelcomeBody);
        }
    }

    /** Prefix of the long multi-page welcome body; used to detect outdated seed text. */
    private static final String WELCOME_BODY_MARKER = "【阅笺使用说明】";

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
            migrateWelcomeSeedBody();
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
            if (seedTitle.equals(book.title) && (LEGACY_SEED_AUTHOR.equals(book.author) || "笔趣阁".equals(book.author))) {
                all.set(i, new Book(book.id, book.title, appName, book.text, book.position, book.coverPath));
                changed = true;
            }
        }
        if (changed) save(all);
    }

    /**
     * Refresh the built-in《使用说明》when it still has the short one-page body so users can
     * try multi-page Kindle pagination without reinstalling. Only touches books whose title
     * is the seed title and author is the app name / legacy product name; never user imports.
     */
    private void migrateWelcomeSeedBody() {
        if (context == null) return;
        String appName = context.getString(R.string.app_name);
        String seedTitle = context.getString(R.string.welcome_book_title);
        String newBody = context.getString(R.string.welcome_book_body, appName);
        List<Book> all = books();
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            Book book = all.get(i);
            if (!seedTitle.equals(book.title)) continue;
            if (!(appName.equals(book.author)
                    || LEGACY_SEED_AUTHOR.equals(book.author)
                    || "笔趣阁".equals(book.author))) {
                continue;
            }
            String text = book.text == null ? "" : book.text;
            if (text.contains(WELCOME_BODY_MARKER)) continue;
            all.set(i, new Book(book.id, book.title, appName, newBody, book.position, book.coverPath));
            changed = true;
        }
        if (changed) save(all);
    }

    public static LibraryStore get(Context context) { return new LibraryStore(context); }

    /**
     * Opens the store without seed migrations. Use on the reader path, where migrations would
     * otherwise reread every imported TXT before the first page can be shown.
     */
    public static LibraryStore getForReading(Context context) {
        return new LibraryStore(context, false);
    }

    public List<Book> books() {
        List<Book> books = new ArrayList<>();
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return books;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            // Backward-compatible: still exactly 4 fields (id|title|author|position).
            // Covers are optional side files, not a 5th column.
            String[] values = row.split("\\|", 4);
            if (values.length != 4) continue;
            try {
                String text = readText(values[0]);
                if (text != null) {
                    String title = decode(values[1]);
                    String author = decode(values[2]);
                    int pos = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                    books.add(new Book(values[0], title, author, text, pos, coverPathIfPresent(values[0])));
                }
            } catch (Exception ignored) { }
        }
        return books;
    }

    /**
     * Metadata only (no TXT body). Used on the progressive open path so the reader can
     * paint chrome / progress before a multi‑MB decode finishes.
     */
    public BookRecord recordById(String id) {
        if (id == null || id.isEmpty()) return null;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return null;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4 || !id.equals(values[0])) continue;
            try {
                int pos = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                return new BookRecord(
                        id,
                        decode(values[1]),
                        decode(values[2]),
                        pos,
                        coverPathIfPresent(id));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Raw UTF-8 bytes for one book file, or null if missing. Progressive open decodes a
     * window first, then the full string, without re-reading the shelf.
     */
    public byte[] readBookBytes(String id) {
        if (id == null || id.isEmpty()) return null;
        return readTextBytes(id);
    }

    /** Reads only the requested book; the shelf may contain very large TXT files. */
    public Book byId(String id) {
        BookRecord record = recordById(id);
        if (record == null) return null;
        String text = readText(id);
        if (text == null) return null;
        return new Book(
                record.id,
                record.title,
                record.author,
                text,
                record.position,
                record.coverPath);
    }

    /** Shelf row without body text — safe to load on the open hot path. */
    public static final class BookRecord {
        public final String id;
        public final String title;
        public final String author;
        public final int position;
        public final String coverPath;

        public BookRecord(String id, String title, String author, int position, String coverPath) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.position = position;
            this.coverPath = coverPath;
        }
    }
    public void add(String title, String author, String text) {
        add(title, author, text, null);
    }

    /** Optional [coverBytes] (JPEG/PNG/…) stored under covers/{id}.cover when within size limit. */
    public void add(String title, String author, String text, byte[] coverBytes) {
        List<Book> all = books();
        String id = UUID.randomUUID().toString();
        String coverPath = writeCover(id, coverBytes);
        all.add(new Book(id, title, author, text, 0, coverPath));
        save(all);
    }
    /** Changes display metadata without changing the saved text, cover, or reading position. */
    public void updateMetadata(String id, String title, String author) {
        List<Book> all = books();
        for (int i = 0; i < all.size(); i++) {
            Book old = all.get(i);
            if (old.id.equals(id)) {
                all.set(i, new Book(
                        old.id,
                        cleanTitle(title, old.title),
                        cleanTitle(author, old.author),
                        old.text,
                        old.position,
                        old.coverPath));
            }
        }
        save(all);
    }
    /** Keeps the shelf order meaningful: a pinned book is shown first. */
    public void moveToTop(String id) {
        List<Book> all = books();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) { Book book = all.remove(i); all.add(0, book); break; }
        save(all);
    }
    /** Updates only the metadata row; never rereads or rewrites imported book text. */
    public void savePosition(String id, int progress) {
        if (id == null || id.isEmpty()) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        int clamped = Math.max(0, Math.min(progress, 1000));
        StringBuilder output = new StringBuilder(raw.length());
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length == 4 && id.equals(values[0])) {
                output.append(values[0]).append('|').append(values[1]).append('|')
                        .append(values[2]).append('|').append(clamped).append('\n');
            } else {
                output.append(row).append('\n');
            }
        }
        prefs.edit().putString(KEY, output.toString()).apply();
    }
    public void remove(String id) {
        List<Book> all = books();
        Iterator<Book> iterator = all.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().id.equals(id)) iterator.remove();
        }
        File file = new File(bookDir(), id + ".txt");
        if (file.exists()) file.delete();
        deleteCover(id);
        save(all);
    }

    /** Exports all local books, progress, and covers into a portable ZIP backup. */
    public void exportTo(OutputStream output) throws IOException {
        List<Book> books = books();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            StringBuilder manifest = new StringBuilder();
            for (Book book : books) {
                // Manifest stays 4-field for old-client restore compatibility.
                manifest.append(book.id).append('|').append(encode(book.title)).append('|').append(encode(book.author)).append('|').append(book.position).append('\n');
                zip.putNextEntry(new ZipEntry("books/" + book.id + ".txt"));
                zip.write(book.text.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                byte[] cover = readCoverBytes(book.id);
                if (cover != null && cover.length > 0) {
                    zip.putNextEntry(new ZipEntry("covers/" + book.id + ".cover"));
                    zip.write(cover);
                    zip.closeEntry();
                }
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
        Map<String, byte[]> covers = new HashMap<>();
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
                String name = entry.getName();
                if ("library.txt".equals(name)) {
                    manifest = data.toString("UTF-8");
                } else if (name.startsWith("books/") && name.endsWith(".txt")) {
                    texts.put(name.substring(6, name.length() - 4), data.toByteArray());
                } else if (name.startsWith("covers/") && name.endsWith(".cover")) {
                    byte[] cover = data.toByteArray();
                    if (cover.length > 0 && cover.length <= MAX_COVER_BYTES) {
                        covers.put(name.substring(7, name.length() - 6), cover);
                    }
                }
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
                String newId = UUID.randomUUID().toString();
                String coverPath = writeCover(newId, covers.get(values[0]));
                all.add(new Book(
                        newId,
                        title,
                        author,
                        new String(texts.get(values[0]), StandardCharsets.UTF_8),
                        position,
                        coverPath));
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
            // 4-field rows only — covers stay as side files (old clients ignore them).
            output.append(book.id).append('|').append(encode(book.title)).append('|').append(encode(book.author)).append('|').append(book.position).append('\n');
        }
        // Single edit() → apply() so CommitPrefEdits lint stays clean (API 23+ SharedPreferences).
        prefs.edit().putString(KEY, output.toString()).apply();
    }

    private File bookDir() {
        File dir = new File(filesDir, "books");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File coverDir() {
        File dir = new File(filesDir, "covers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File coverFile(String id) {
        return new File(coverDir(), id + ".cover");
    }

    /** Absolute path if a valid cover file exists; otherwise null. */
    private String coverPathIfPresent(String id) {
        File file = coverFile(id);
        if (!file.exists() || file.length() <= 0 || file.length() > MAX_COVER_BYTES) return null;
        return file.getAbsolutePath();
    }

    /**
     * Persist cover bytes; returns absolute path or null if skipped/failed.
     * Oversized or empty payloads are ignored (graceful no-cover).
     */
    private String writeCover(String id, byte[] coverBytes) {
        if (coverBytes == null || coverBytes.length == 0 || coverBytes.length > MAX_COVER_BYTES) {
            return null;
        }
        try (FileOutputStream output = new FileOutputStream(coverFile(id))) {
            output.write(coverBytes);
            return coverFile(id).getAbsolutePath();
        } catch (IOException ignored) {
            return null;
        }
    }

    private byte[] readCoverBytes(String id) {
        File file = coverFile(id);
        if (!file.exists() || file.length() <= 0 || file.length() > MAX_COVER_BYTES) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = input.read(data);
            return read <= 0 ? null : data;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void deleteCover(String id) {
        File file = coverFile(id);
        if (file.exists()) file.delete();
    }

    private byte[] readTextBytes(String id) {
        File file = new File(bookDir(), id + ".txt");
        if (!file.exists()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            long length = file.length();
            if (length < 0 || length > MAX_SINGLE_ENTRY_BYTES) return null;
            byte[] data = new byte[(int) length];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset == data.length) return data;
            if (offset <= 0) return new byte[0];
            byte[] trimmed = new byte[offset];
            System.arraycopy(data, 0, trimmed, 0, offset);
            return trimmed;
        } catch (IOException ignored) {
            return null;
        }
    }

    private String readText(String id) {
        byte[] data = readTextBytes(id);
        if (data == null) return null;
        return data.length == 0 ? "" : new String(data, StandardCharsets.UTF_8);
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
        // TextBase64: API-23-safe, JVM-unit-testable; same wire format as android.util.Base64.NO_WRAP
        return TextBase64.encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(TextBase64.decode(value), StandardCharsets.UTF_8);
    }
}
