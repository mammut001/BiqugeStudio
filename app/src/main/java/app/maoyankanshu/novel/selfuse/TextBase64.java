package app.maoyankanshu.novel.selfuse;

/**
 * Pure-Java Base64 encode/decode with no line wrapping.
 * Wire-compatible with {@code android.util.Base64.NO_WRAP} and
 * {@code java.util.Base64} Basic (no MIME line breaks). Safe for minSdk 23
 * and JVM unit tests (unlike {@code android.util.Base64}, which is unmocked on the JVM).
 */
final class TextBase64 {
    private static final char[] ENCODE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DECODE = new int[128];

    static {
        for (int i = 0; i < DECODE.length; i++) DECODE[i] = -1;
        for (int i = 0; i < ENCODE.length; i++) DECODE[ENCODE[i]] = i;
        DECODE['='] = 0;
    }

    private TextBase64() {}

    static String encode(byte[] input) {
        int len = input.length;
        StringBuilder out = new StringBuilder(((len + 2) / 3) * 4);
        for (int i = 0; i < len; i += 3) {
            int b0 = input[i] & 0xff;
            int b1 = i + 1 < len ? input[i + 1] & 0xff : 0;
            int b2 = i + 2 < len ? input[i + 2] & 0xff : 0;
            out.append(ENCODE[b0 >> 2]);
            out.append(ENCODE[((b0 & 0x03) << 4) | (b1 >> 4)]);
            out.append(i + 1 < len ? ENCODE[((b1 & 0x0f) << 2) | (b2 >> 6)] : '=');
            out.append(i + 2 < len ? ENCODE[b2 & 0x3f] : '=');
        }
        return out.toString();
    }

    static byte[] decode(String input) {
        String cleaned = input.trim();
        if (cleaned.isEmpty()) return new byte[0];
        int pad = 0;
        if (cleaned.endsWith("==")) pad = 2;
        else if (cleaned.endsWith("=")) pad = 1;
        int len = cleaned.length();
        byte[] out = new byte[len / 4 * 3 - pad];
        int o = 0;
        for (int i = 0; i < len; i += 4) {
            int c0 = decodeChar(cleaned.charAt(i));
            int c1 = decodeChar(cleaned.charAt(i + 1));
            int c2 = i + 2 < len ? decodeChar(cleaned.charAt(i + 2)) : 0;
            int c3 = i + 3 < len ? decodeChar(cleaned.charAt(i + 3)) : 0;
            if (o < out.length) out[o++] = (byte) ((c0 << 2) | (c1 >> 4));
            if (o < out.length) out[o++] = (byte) (((c1 & 0x0f) << 4) | (c2 >> 2));
            if (o < out.length) out[o++] = (byte) (((c2 & 0x03) << 6) | c3);
        }
        return out;
    }

    private static int decodeChar(char c) {
        if (c >= DECODE.length || DECODE[c] < 0) {
            throw new IllegalArgumentException("Invalid Base64 character: " + c);
        }
        return DECODE[c];
    }
}
