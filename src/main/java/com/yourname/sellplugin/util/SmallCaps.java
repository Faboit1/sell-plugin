package com.yourname.sellplugin.util;

/**
 * Converts regular lowercase text to Unicode small-capital letters.
 * Uppercase letters are left as-is; digits, symbols, spaces, and
 * colour codes are preserved.
 */
public final class SmallCaps {

    private SmallCaps() {}

    private static final String LOWER =
            "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL =
            "\u1d00\u0299\u1d04\u1d05\u1d07\ua730\u0262\u029c\u026a\u1d0a\u1d0b\u029f\u1d0d\u0274\u1d0f\u1d18\u01eb\u0280\ua731\u1d1b\u1d1c\u1d20\u1d21x\u028f\u1d22";

    /**
     * Convert every ASCII lowercase letter in {@code text} to its
     * small-capital equivalent.  Everything else (uppercase, digits,
     * Minecraft colour codes like {@code §a}, spaces, symbols) is
     * kept unchanged.
     */
    public static String convert(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = LOWER.indexOf(c);
            if (idx >= 0) {
                sb.append(SMALL.charAt(idx));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
