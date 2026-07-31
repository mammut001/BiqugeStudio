package app.maoyankanshu.novel.selfuse;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal EPUB reader that follows the package spine (XHTML/HTML) instead of ZIP entry order.
 * Decodes chapter bytes via UTF-8/UTF-16 BOM, UTF-16 XML signature, or XML {@code encoding=},
 * then strips tags and HTML entities (including NBSP). Pure Java — minSdk 23 + JVM unit tests.
 *
 * <p>{@link ZipInputStream} expansion is capped at {@link #MAX_BYTES} (32 MiB) per entry and
 * total uncompressed — same limit / {@link IllegalArgumentException} message as
 * {@code LocalBookImport}.
 */
public final class EpubReader {
    /** Matches LocalBookImport 32 MiB cap (per ZIP entry and total uncompressed). */
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    private static final String TOO_LARGE = "file too large, max 32MB";

    private static final Pattern ENTITY_PATTERN =
            Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|[A-Za-z][A-Za-z0-9]+);");
    private static final Pattern XML_ENCODING =
            Pattern.compile("(?is)<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern ITEM_TAG = Pattern.compile("(?is)<item\\b([^>]*)>");
    private static final Pattern ITEMREF_TAG = Pattern.compile("(?is)<itemref\\b([^>]*)>");
    private static final Pattern ROOTFILE_TAG = Pattern.compile("(?is)<rootfile\\b([^>]*)>");
    private static final Pattern ATTR_PATTERN =
            Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL);
    /**
     * Dublin Core title/creator, prefix-tolerant: {@code dc:title}, {@code title},
     * {@code opf:dc:title}-style local names ending in title/creator.
     */
    private static final Pattern DC_TITLE = Pattern.compile(
            "(?is)<(?:[\\w.-]+:)?title(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?title\\s*>");
    private static final Pattern DC_CREATOR = Pattern.compile(
            "(?is)<(?:[\\w.-]+:)?creator(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?creator\\s*>");

    private static final Map<String, String> NAMED_ENTITIES = namedEntities();

    /**
     * EPUB package + chapter text. [title]/[author] may be null when OPF omits them
     * (callers should fall back to filename / default labels).
     */
    public static final class Book {
        public final String title;
        public final String author;
        public final String text;

        public Book(String title, String author, String text) {
            this.title = title;
            this.author = author;
            this.text = text == null ? "" : text;
        }
    }

    private EpubReader() { }

    /** Spine text only (source-compatible with existing callers). */
    public static String read(InputStream source) throws Exception {
        return readBook(source).text;
    }

    /**
     * Read package metadata ({@code dc:title} / {@code dc:creator}) and spine chapter text.
     * Metadata values have HTML/XML entities decoded; blank after trim → null.
     */
    public static Book readBook(InputStream source) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long totalUncompressed = 0L;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                long declared = entry.getSize();
                if (declared > MAX_BYTES) {
                    throw new IllegalArgumentException(TOO_LARGE);
                }
                long[] total = new long[] { totalUncompressed };
                byte[] data = readAllBounded(zip, total);
                totalUncompressed = total[0];
                files.put(normalize(entry.getName()), data);
                zip.closeEntry();
            }
        }
        String container = decodeText(files.get("META-INF/container.xml"));
        String opfPath = findRootFile(container);
        if (opfPath == null || !files.containsKey(opfPath)) {
            for (String name : files.keySet()) {
                if (name.toLowerCase(Locale.ROOT).endsWith(".opf")) {
                    opfPath = name;
                    break;
                }
            }
        }
        String opfXml = opfPath == null ? "" : decodeText(files.get(opfPath));
        String[] meta = parsePackageMetadata(opfXml);
        List<String> chapters = opfPath == null
                ? new ArrayList<>()
                : spineFiles(opfXml, opfPath);
        if (chapters.isEmpty()) {
            for (String name : files.keySet()) {
                if (isHtml(name)) chapters.add(name);
            }
        }

        // Spine order; each chapter is stripHtml'd (already single-\n inside).
        // Join with one '\n' and collapse any accidental blank lines from tags.
        StringBuilder result = new StringBuilder();
        for (String chapter : chapters) {
            byte[] data = files.get(normalize(chapter));
            if (data == null) continue;
            String cleaned = stripHtml(decodeText(data));
            if (cleaned.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(cleaned);
        }
        // Guarantee "ch1\nch2" not "ch1\n\nch2" if a chapter ends/starts with a block newline.
        String text = normalizeWhitespace(result.toString());
        return new Book(meta[0], meta[1], text);
    }

    /**
     * Package-private for JVM tests: first non-blank dc:title / dc:creator from OPF XML.
     * Returns {@code {titleOrNull, authorOrNull}}; entities decoded, inner tags stripped.
     */
    static String[] parsePackageMetadata(String opf) {
        if (opf == null || opf.isEmpty()) return new String[] { null, null };
        String title = firstDcField(DC_TITLE, opf);
        String author = firstDcField(DC_CREATOR, opf);
        return new String[] { title, author };
    }

    private static String firstDcField(Pattern pattern, String opf) {
        Matcher m = pattern.matcher(opf);
        while (m.find()) {
            String raw = m.group(1);
            if (raw == null) continue;
            // Inner markup rare in DC fields; strip tags then entities.
            String cleaned = normalizeWhitespace(
                    decodeHtmlEntities(raw.replaceAll("(?s)<[^>]+>", "")));
            if (!cleaned.isEmpty()) return cleaned;
        }
        return null;
    }

    /**
     * Package-private for JVM tests: decode EPUB/XML/HTML bytes.
     * Order: UTF-8/UTF-16 BOM → UTF-16 XML signature → XML encoding= → UTF-8.
     */
    static String decodeText(byte[] data) {
        if (data == null || data.length == 0) return "";

        // UTF-8 BOM
        if (data.length >= 3
                && (data[0] & 0xff) == 0xef
                && (data[1] & 0xff) == 0xbb
                && (data[2] & 0xff) == 0xbf) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        // UTF-16LE BOM
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        // UTF-16BE BOM
        if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        // UTF-16LE without BOM: '<' 0 '?' 0 (common for <?xml …)
        if (data.length >= 4
                && data[0] == '<' && data[1] == 0
                && data[2] == '?' && data[3] == 0) {
            return new String(data, StandardCharsets.UTF_16LE);
        }
        // UTF-16BE without BOM: 0 '<' 0 '?'
        if (data.length >= 4
                && data[0] == 0 && data[1] == '<'
                && data[2] == 0 && data[3] == '?') {
            return new String(data, StandardCharsets.UTF_16BE);
        }

        String xmlEnc = detectXmlEncoding(data);
        if (xmlEnc != null) {
            try {
                Charset cs = Charset.forName(xmlEnc);
                return new String(data, cs);
            } catch (Exception ignored) {
                // fall through to UTF-8
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Package-private for JVM tests: strip tags + decode entities (NBSP → U+0020). */
    static String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String stripped = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?is)<(br|/p|/div|/h[1-6]|/li)[^>]*>", "\n")
                .replaceAll("(?s)<[^>]+>", "");
        return normalizeWhitespace(decodeHtmlEntities(stripped));
    }

    /** Package-private for JVM tests: named / decimal / hex entities. */
    static String decodeHtmlEntities(String value) {
        if (value == null || value.indexOf('&') < 0) return value == null ? "" : value;
        Matcher matcher = ENTITY_PATTERN.matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String body = matcher.group(1);
            String replacement;
            if (body.regionMatches(true, 0, "#x", 0, 2)) {
                replacement = codePointToString(parseIntSafe(body.substring(2), 16));
            } else if (body.startsWith("#")) {
                replacement = codePointToString(parseIntSafe(body.substring(1), 10));
            } else {
                String named = NAMED_ENTITIES.get(body.toLowerCase(Locale.ROOT));
                replacement = named != null ? named : matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Package-private for JVM tests: spine XHTML/HTML paths in reading order. */
    static List<String> spineFiles(String opf, String opfPath) {
        Map<String, String> manifest = new HashMap<>();
        Map<String, String> mediaTypes = new HashMap<>();
        Matcher items = ITEM_TAG.matcher(opf == null ? "" : opf);
        while (items.find()) {
            Map<String, String> attr = attributes(items.group(1));
            String id = attr.get("id");
            String href = attr.get("href");
            if (id != null && href != null) {
                String resolved = resolve(opfPath, href);
                manifest.put(id, resolved);
                String media = attr.get("media-type");
                if (media != null) mediaTypes.put(id, media);
            }
        }
        List<String> result = new ArrayList<>();
        Matcher refs = ITEMREF_TAG.matcher(opf == null ? "" : opf);
        while (refs.find()) {
            String idref = attributes(refs.group(1)).get("idref");
            String file = idref == null ? null : manifest.get(idref);
            if (file == null) continue;
            String media = mediaTypes.get(idref);
            if (isHtml(file) || isHtmlMediaType(media)) {
                result.add(file);
            }
        }
        return result;
    }

    private static String findRootFile(String container) {
        Matcher roots = ROOTFILE_TAG.matcher(container == null ? "" : container);
        if (!roots.find()) return null;
        String path = attributes(roots.group(1)).get("full-path");
        return path == null ? null : normalize(path);
    }

    private static Map<String, String> attributes(String input) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(input == null ? "" : input);
        while (matcher.find()) {
            values.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(3));
        }
        return values;
    }

    private static String resolve(String base, String href) {
        int slash = base.lastIndexOf('/');
        return normalize((slash < 0 ? "" : base.substring(0, slash + 1)) + href.replaceFirst("#.*$", ""));
    }

    private static String normalize(String path) {
        StringBuilder output = new StringBuilder();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                int slash = output.lastIndexOf("/");
                output.setLength(Math.max(0, slash));
            } else {
                if (output.length() > 0) output.append('/');
                output.append(part);
            }
        }
        return output.toString();
    }

    private static boolean isHtml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private static boolean isHtmlMediaType(String mediaType) {
        if (mediaType == null) return false;
        String m = mediaType.toLowerCase(Locale.ROOT);
        return m.contains("html") || m.contains("xhtml");
    }

    private static String detectXmlEncoding(byte[] data) {
        int n = Math.min(data.length, 512);
        // ASCII-compatible view of the prolog (UTF-8 / Latin-1 encodings).
        String head = new String(data, 0, n, StandardCharsets.ISO_8859_1);
        Matcher m = XML_ENCODING.matcher(head);
        if (!m.find()) return null;
        String enc = m.group(1).trim();
        return enc.isEmpty() ? null : enc;
    }

    private static String normalizeWhitespace(String value) {
        return value
                .replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private static String codePointToString(int code) {
        if (code < 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) return "";
        // NBSP and related spaces → U+0020 for plain-text reading (matches WebImportFetcher).
        if (code == 0xA0 || code == 0x2002 || code == 0x2003 || code == 0x2009
                || code == 0x202F || code == 0xFEFF) {
            return " ";
        }
        if (code <= 0xFFFF) return String.valueOf((char) code);
        return new String(Character.toChars(code));
    }

    private static int parseIntSafe(String s, int radix) {
        try {
            return Integer.parseInt(s, radix);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Map<String, String> namedEntities() {
        Map<String, String> m = new HashMap<>();
        m.put("nbsp", " ");
        m.put("ensp", " ");
        m.put("emsp", " ");
        m.put("thinsp", " ");
        m.put("amp", "&");
        m.put("lt", "<");
        m.put("gt", ">");
        m.put("quot", "\"");
        m.put("apos", "'");
        m.put("mdash", "—");
        m.put("ndash", "–");
        m.put("hellip", "…");
        m.put("ldquo", "“");
        m.put("rdquo", "”");
        m.put("lsquo", "‘");
        m.put("rsquo", "’");
        return m;
    }

    /**
     * Read a ZIP entry body with a 32 MiB per-entry and running total cap.
     * {@code totalUncompressed[0]} is updated with bytes read. Package-private for JVM tests.
     */
    static byte[] readAllBounded(InputStream input, long[] totalUncompressed) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        long entryBytes = 0L;
        while ((n = input.read(buffer)) != -1) {
            entryBytes += n;
            if (entryBytes > MAX_BYTES) {
                throw new IllegalArgumentException(TOO_LARGE);
            }
            long newTotal = totalUncompressed[0] + n;
            if (newTotal > MAX_BYTES) {
                throw new IllegalArgumentException(TOO_LARGE);
            }
            totalUncompressed[0] = newTotal;
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
