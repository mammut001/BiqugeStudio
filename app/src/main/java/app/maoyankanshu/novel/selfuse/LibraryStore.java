package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
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
     * Prefs-row rewrite only — does <strong>not</strong> call [books] / full-body decode.
     */
    private void migrateLegacySeedAuthor() {
        if (context == null) return;
        migrateLegacySeedAuthor(
                context.getString(R.string.app_name),
                context.getString(R.string.welcome_book_title));
    }

    /** Package-visible for tests: production seed-author migration without Context strings. */
    void migrateLegacySeedAuthor(String appName, String seedTitle) {
        if (appName == null || seedTitle == null) return;
        if (LEGACY_SEED_AUTHOR.equals(appName)) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        StringBuilder output = new StringBuilder(raw.length());
        boolean changed = false;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4) {
                output.append(row).append('\n');
                continue;
            }
            try {
                String title = decode(values[1]);
                String author = decode(values[2]);
                if (seedTitle.equals(title)
                        && (LEGACY_SEED_AUTHOR.equals(author) || "笔趣阁".equals(author))) {
                    output.append(values[0]).append('|').append(values[1]).append('|')
                            .append(encode(appName)).append('|').append(values[3]).append('\n');
                    changed = true;
                } else {
                    output.append(row).append('\n');
                }
            } catch (Exception ignored) {
                output.append(row).append('\n');
            }
        }
        if (changed) {
            prefs.edit().putString(KEY, output.toString()).apply();
        }
    }

    /**
     * Refresh the built-in《使用说明》when it still has the short one-page body so users can
     * try multi-page Kindle pagination without reinstalling. Only touches books whose title
     * is the seed title and author is the app name / legacy product name; never user imports.
     * Loads at most the matching seed file(s) — never the whole multi‑MB library.
     */
    private void migrateWelcomeSeedBody() {
        if (context == null) return;
        String appName = context.getString(R.string.app_name);
        String seedTitle = context.getString(R.string.welcome_book_title);
        String newBody = context.getString(R.string.welcome_book_body, appName);
        migrateWelcomeSeedBody(appName, seedTitle, newBody);
    }

    /** Package-visible for tests: seed-body migration without Android string resources. */
    void migrateWelcomeSeedBody(String appName, String seedTitle, String newBody) {
        if (appName == null || seedTitle == null || newBody == null) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        StringBuilder output = new StringBuilder(raw.length());
        boolean prefsChanged = false;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4) {
                output.append(row).append('\n');
                continue;
            }
            try {
                String id = values[0];
                String title = decode(values[1]);
                String author = decode(values[2]);
                if (!seedTitle.equals(title)) {
                    output.append(row).append('\n');
                    continue;
                }
                if (!(appName.equals(author)
                        || LEGACY_SEED_AUTHOR.equals(author)
                        || "笔趣阁".equals(author))) {
                    output.append(row).append('\n');
                    continue;
                }
                String text = readText(id);
                if (text == null) {
                    output.append(row).append('\n');
                    continue;
                }
                if (text.contains(WELCOME_BODY_MARKER)) {
                    if (!appName.equals(author)) {
                        output.append(id).append('|').append(values[1]).append('|')
                                .append(encode(appName)).append('|').append(values[3]).append('\n');
                        prefsChanged = true;
                    } else {
                        output.append(row).append('\n');
                    }
                    continue;
                }
                writeText(id, newBody);
                output.append(id).append('|').append(values[1]).append('|')
                        .append(encode(appName)).append('|').append(values[3]).append('\n');
                prefsChanged = true;
            } catch (Exception ignored) {
                output.append(row).append('\n');
            }
        }
        if (prefsChanged) {
            prefs.edit().putString(KEY, output.toString()).apply();
        }
    }

    public static LibraryStore get(Context context) { return new LibraryStore(context); }

    /** Opens the store without seed migrations for reader/progress hot paths. */
    public static LibraryStore getForReading(Context context) {
        return new LibraryStore(context, false);
    }

    /** Main-shell / shelf listing entry; migrations never materialize user multi‑MB bodies. */
    public static LibraryStore getForListing(Context context) {
        return new LibraryStore(context, true);
    }

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
                    books.add(new Book(values[0], title, author, text, pos, coverPathIfPresent(values[0])));
                }
            } catch (Exception ignored) { }
        }
        return books;
    }

    /** Shelf/list metadata without decoding multi‑MB TXT bodies into Book.text. */
    public List<Book> booksForListing() {
        List<Book> books = new ArrayList<>();
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return books;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length != 4) continue;
            try {
                String id = values[0];
                if (!bookFileExists(id)) continue;
                String title = decode(values[1]);
                String author = decode(values[2]);
                int pos = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                int chars = textCharCount(id);
                books.add(new Book(
                        id,
                        title,
                        author,
                        "",
                        pos,
                        coverPathIfPresent(id),
                        chars));
            } catch (Exception ignored) { }
        }
        return books;
    }

    /** Metadata only (no TXT body). */
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

    /** Raw UTF-8 bytes for one book file, or null if missing. */
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

    /** Adds only the new body + metadata row; existing books are never decoded/re-written. */
    public void add(String title, String author, String text, byte[] coverBytes) {
        String id = UUID.randomUUID().toString();
        writeText(id, text);
        writeCover(id, coverBytes);

        String raw = prefs.getString(KEY, "");
        StringBuilder output = metadataBuilder(raw, 160);
        appendMetadataRow(output, id, title, author, 0);
        prefs.edit().putString(KEY, output.toString()).apply();
    }

    /** Changes display metadata without changing saved text, cover, position, or other books. */
    public void updateMetadata(String id, String title, String author) {
        if (id == null || id.isEmpty()) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        StringBuilder output = new StringBuilder(raw.length());
        boolean changed = false;
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length == 4 && id.equals(values[0])) {
                try {
                    String oldTitle = decode(values[1]);
                    String oldAuthor = decode(values[2]);
                    int position = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                    appendMetadataRow(
                            output,
                            id,
                            cleanTitle(title, oldTitle),
                            cleanTitle(author, oldAuthor),
                            position);
                    changed = true;
                    continue;
                } catch (Exception ignored) {
                    // Preserve a malformed row rather than risking data loss during an edit.
                }
            }
            output.append(row).append('\n');
        }
        if (changed) prefs.edit().putString(KEY, output.toString()).apply();
    }

    /** Keeps the shelf order meaningful: a pinned book is shown first. Prefs-only. */
    public void moveToTop(String id) {
        if (id == null || id.isEmpty()) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return;
        String pinned = null;
        StringBuilder rest = new StringBuilder(raw.length());
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length == 4 && id.equals(values[0])) {
                pinned = row;
            } else {
                rest.append(row).append('\n');
            }
        }
        if (pinned == null) return;
        prefs.edit().putString(KEY, pinned + '\n' + rest).apply();
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

    /** Deletes one book. Prefs + files only — never full-library TXT decode. */
    public void remove(String id) {
        if (id == null || id.isEmpty()) return;
        String raw = prefs.getString(KEY, "");
        if (raw == null) raw = "";
        StringBuilder output = new StringBuilder(raw.length());
        for (String row : raw.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            if (values.length == 4 && id.equals(values[0])) continue;
            output.append(row).append('\n');
        }
        File file = bookFile(id);
        if (file.exists()) file.delete();
        deleteCharCount(id);
        deleteCover(id);
        prefs.edit().putString(KEY, output.toString()).apply();
    }

    /**
     * Exports all local books, progress, and covers into a portable ZIP backup.
     * TXT/cover files stream directly into the ZIP with a fixed buffer; backup output errors
     * propagate to the caller instead of being mistaken for a corrupt metadata row.
     */
    public void exportTo(OutputStream output) throws IOException {
        String raw = prefs.getString(KEY, "");
        if (raw == null) raw = "";
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            StringBuilder manifest = new StringBuilder(raw.length());
            for (String row : raw.split("\\n", -1)) {
                if (row.trim().isEmpty()) continue;
                String[] values = row.split("\\|", 4);
                if (values.length != 4) continue;

                String id;
                int position;
                try {
                    id = values[0];
                    // Validate base64 + position just like books(), but keep I/O outside this catch.
                    decode(values[1]);
                    decode(values[2]);
                    position = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                } catch (RuntimeException malformedMetadata) {
                    continue;
                }

                File bodyFile = bookFile(id);
                if (!isValidBookFile(bodyFile)) continue;
                manifest.append(id).append('|').append(values[1]).append('|')
                        .append(values[2]).append('|').append(position).append('\n');

                zip.putNextEntry(new ZipEntry("books/" + id + ".txt"));
                copyFile(bodyFile, zip);
                zip.closeEntry();

                File cover = coverFile(id);
                if (cover.isFile() && cover.length() > 0 && cover.length() <= MAX_COVER_BYTES) {
                    zip.putNextEntry(new ZipEntry("covers/" + id + ".cover"));
                    copyFile(cover, zip);
                    zip.closeEntry();
                }
            }
            zip.putNextEntry(new ZipEntry("library.txt"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** Writes one book as its already-stored UTF-8 TXT bytes; no String/byte[] round-trip. */
    public void exportBook(String id, OutputStream output) throws IOException {
        if (recordById(id) == null) throw new IOException("Book not found");
        File file = bookFile(id);
        if (!isValidBookFile(file)) throw new IOException("Book not found");
        copyFile(file, output);
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

        String raw = prefs.getString(KEY, "");
        StringBuilder output = metadataBuilder(raw, manifest.length());
        int imported = 0;
        for (String row : manifest.split("\\n", -1)) {
            if (row.trim().isEmpty()) continue;
            String[] values = row.split("\\|", 4);
            byte[] sourceText = values.length == 4 ? texts.get(values[0]) : null;
            if (values.length != 4 || sourceText == null) continue;
            try {
                String title = decode(values[1]);
                String author = decode(values[2]);
                int position = Math.max(0, Math.min(Integer.parseInt(values[3]), 1000));
                String newId = UUID.randomUUID().toString();
                writeText(newId, new String(sourceText, StandardCharsets.UTF_8));
                writeCover(newId, covers.get(values[0]));
                appendMetadataRow(output, newId, title, author, position);
                imported++;
            } catch (Exception ignored) { }
        }
        if (imported > 0) {
            prefs.edit().putString(KEY, output.toString()).apply();
        }
        return imported;
    }

    /** Legacy full-save helper retained for migrations/tests that intentionally own full bodies. */
    private void save(List<Book> books) {
        StringBuilder output = new StringBuilder();
        for (Book book : books) {
            writeText(book.id, book.text);
            appendMetadataRow(output, book.id, book.title, book.author, book.position);
        }
        prefs.edit().putString(KEY, output.toString()).apply();
    }

    /** Existing metadata rows plus exactly one trailing newline when needed. */
    private StringBuilder metadataBuilder(String raw, int extraCapacity) {
        String existing = raw == null ? "" : raw;
        StringBuilder output = new StringBuilder(existing.length() + Math.max(0, extraCapacity));
        if (!existing.isEmpty()) {
            output.append(existing);
            if (existing.charAt(existing.length() - 1) != '\n') output.append('\n');
        }
        return output;
    }

    private static void appendMetadataRow(
            StringBuilder output,
            String id,
            String title,
            String author,
            int position
    ) {
        int clamped = Math.max(0, Math.min(position, 1000));
        output.append(id).append('|').append(encode(title)).append('|').append(encode(author))
                .append('|').append(clamped).append('\n');
    }

    private File bookDir() {
        File dir = new File(filesDir, "books");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File bookFile(String id) {
        return new File(bookDir(), id + ".txt");
    }

    private boolean isValidBookFile(File file) {
        return file != null && file.isFile() && file.length() >= 0 && file.length() <= MAX_SINGLE_ENTRY_BYTES;
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

    /** Persist cover bytes; oversized/empty payloads are ignored gracefully. */
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
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset == data.length) return data;
            if (offset <= 0) return null;
            byte[] trimmed = new byte[offset];
            System.arraycopy(data, 0, trimmed, 0, offset);
            return trimmed;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void deleteCover(String id) {
        File file = coverFile(id);
        if (file.exists()) file.delete();
    }

    private byte[] readTextBytes(String id) {
        File file = bookFile(id);
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

    /** Copy one private file with bounded memory; caller owns [output]. */
    private static void copyFile(File file, OutputStream output) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    /** Full-body decode counter for JVM tests of list/hot mutation paths. */
    int fullBodyDecodeCount = 0;

    private String readText(String id) {
        byte[] data = readTextBytes(id);
        if (data == null) return null;
        fullBodyDecodeCount++;
        String text = data.length == 0 ? "" : new String(data, StandardCharsets.UTF_8);
        writeCharCount(id, text.length());
        return text;
    }

    private void writeText(String id, String text) {
        String safeText = text == null ? "" : text;
        try (FileOutputStream output = new FileOutputStream(bookFile(id))) {
            output.write(safeText.getBytes(StandardCharsets.UTF_8));
            writeCharCount(id, safeText.length());
        } catch (IOException ignored) { }
    }

    private boolean bookFileExists(String id) {
        return bookFile(id).exists();
    }

    private File charCountFile(String id) {
        return new File(bookDir(), id + ".chars");
    }

    private void writeCharCount(String id, int count) {
        try (FileOutputStream output = new FileOutputStream(charCountFile(id))) {
            output.write(Integer.toString(Math.max(0, count)).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
    }

    private void deleteCharCount(String id) {
        File file = charCountFile(id);
        if (file.exists()) file.delete();
    }

    /** Character count for list/overview UI with a small side cache. */
    private int textCharCount(String id) {
        File cache = charCountFile(id);
        if (cache.exists() && cache.length() > 0 && cache.length() < 32) {
            try (FileInputStream input = new FileInputStream(cache)) {
                byte[] data = new byte[(int) cache.length()];
                int read = input.read(data);
                if (read > 0) {
                    return Math.max(0, Integer.parseInt(new String(data, 0, read, StandardCharsets.UTF_8).trim()));
                }
            } catch (Exception ignored) { }
        }
        File file = bookFile(id);
        if (!file.exists()) return 0;
        int count = countUtf8Chars(file);
        writeCharCount(id, count);
        return count;
    }

    /** Stream UTF‑8 char count with fixed buffer — O(file) time, O(buffer) memory. */
    static int countUtf8Chars(File file) {
        if (file == null || !file.exists()) return 0;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int total = 0;
            int n;
            while ((n = reader.read(buf)) >= 0) {
                total += n;
            }
            return Math.max(0, total);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String cleanTitle(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty()
                ? fallback
                : clean.replace('|', '｜').replace('\n', ' ').replace('\r', ' ');
    }

    private static String encode(String value) {
        return TextBase64.encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(TextBase64.decode(value), StandardCharsets.UTF_8);
    }
}
