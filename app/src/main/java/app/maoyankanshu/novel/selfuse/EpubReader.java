package app.maoyankanshu.novel.selfuse;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal EPUB reader that follows the package spine instead of ZIP entry order. */
public final class EpubReader {
    private EpubReader() { }

    public static String read(InputStream source) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                files.put(normalize(entry.getName()), readAll(zip));
                zip.closeEntry();
            }
        }
        String container = text(files.get("META-INF/container.xml"));
        String opfPath = findRootFile(container);
        if (opfPath == null || !files.containsKey(opfPath)) {
            for (String name : files.keySet()) if (name.toLowerCase().endsWith(".opf")) { opfPath = name; break; }
        }
        List<String> chapters = opfPath == null ? new ArrayList<>() : spineFiles(text(files.get(opfPath)), opfPath);
        if (chapters.isEmpty()) for (String name : files.keySet()) if (isHtml(name)) chapters.add(name);

        StringBuilder result = new StringBuilder();
        for (String chapter : chapters) {
            byte[] data = files.get(normalize(chapter));
            if (data == null) continue;
            String cleaned = stripHtml(text(data));
            if (!cleaned.isEmpty()) result.append(cleaned).append("\n\n");
        }
        return result.toString().trim();
    }

    private static List<String> spineFiles(String opf, String opfPath) {
        Map<String, String> manifest = new HashMap<>();
        Matcher items = Pattern.compile("(?is)<item\\b([^>]*)>").matcher(opf);
        while (items.find()) {
            Map<String, String> attr = attributes(items.group(1));
            String id = attr.get("id"), href = attr.get("href");
            if (id != null && href != null) manifest.put(id, resolve(opfPath, href));
        }
        List<String> result = new ArrayList<>();
        Matcher refs = Pattern.compile("(?is)<itemref\\b([^>]*)>").matcher(opf);
        while (refs.find()) {
            String file = manifest.get(attributes(refs.group(1)).get("idref"));
            if (file != null && isHtml(file)) result.add(file);
        }
        return result;
    }
    private static String findRootFile(String container) {
        Matcher roots = Pattern.compile("(?is)<rootfile\\b([^>]*)>").matcher(container);
        if (!roots.find()) return null;
        String path = attributes(roots.group(1)).get("full-path");
        return path == null ? null : normalize(path);
    }
    private static Map<String, String> attributes(String input) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL).matcher(input);
        while (matcher.find()) values.put(matcher.group(1).toLowerCase(), matcher.group(3));
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
            if ("..".equals(part)) { int slash = output.lastIndexOf("/"); output.setLength(Math.max(0, slash)); }
            else { if (output.length() > 0) output.append('/'); output.append(part); }
        }
        return output.toString();
    }
    private static boolean isHtml(String name) { String lower = name.toLowerCase(); return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm"); }
    private static String stripHtml(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", "").replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?is)<(br|/p|/div|/h[1-6]|/li)[^>]*>", "\n")
                .replaceAll("(?s)<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("[ \\t]*\\n[ \\t]*", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }
    private static byte[] readAll(InputStream input) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n); return out.toByteArray(); }
    private static String text(byte[] data) { return data == null ? "" : new String(data, StandardCharsets.UTF_8); }
}
