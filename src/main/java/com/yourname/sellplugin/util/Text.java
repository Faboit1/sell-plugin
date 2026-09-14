package com.yourname.sellplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns configured text into something Bukkit can display.
 *
 * <p>Everything the plugin shows is written in <a
 * href="https://docs.advntr.dev/minimessage/format.html">MiniMessage</a>, so a
 * server decides its own colours, gradients and decorations rather than getting
 * whatever the code hard-coded. The old {@code &amp;} colour codes still work:
 * they are rewritten to MiniMessage tags before parsing, so a config written
 * either way - or half and half - renders the same.
 *
 * <p>The result is handed back as a legacy section-sign string because that is
 * what the inventory and item APIs take, and because legacy lore is the form
 * the client renders upright instead of italic.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    /** Legacy colour/format codes in the order of their code character. */
    private static final String LEGACY_CODES = "0123456789abcdefklmnor";
    private static final String[] LEGACY_TAGS = {
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
        "obfuscated", "bold", "strikethrough", "underlined", "italic", "reset"
    };

    private Text() {
    }

    /** Parses configured text into a component. */
    public static Component component(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return MINI.deserialize(legacyToMiniMessage(raw));
    }

    /** Parses configured text and renders it for Bukkit's string-based APIs. */
    public static String legacy(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return SECTION.serialize(component(raw));
    }

    /** Parses every line of a configured list. */
    public static List<String> legacyLines(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> rendered = new ArrayList<>(raw.size());
        for (String line : raw) rendered.add(legacy(line));
        return rendered;
    }

    /**
     * Substitutes {@code {name}} placeholders, given as alternating name and
     * value. Done before parsing, so a value may itself carry MiniMessage.
     */
    public static String fill(String raw, Object... namesAndValues) {
        if (raw == null) return "";
        String filled = raw;
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            String value = namesAndValues[i + 1] == null ? "" : String.valueOf(namesAndValues[i + 1]);
            filled = filled.replace("{" + namesAndValues[i] + "}", value);
        }
        return filled;
    }

    /** True when the line still mentions a placeholder, used to drop lines that no longer apply. */
    public static boolean mentions(String raw, String placeholder) {
        return raw != null && raw.contains("{" + placeholder + "}");
    }

    /**
     * Rewrites legacy colour codes as the MiniMessage tags that mean the same
     * thing, leaving every other character - MiniMessage tags included - alone,
     * so the two notations can be mixed in one string.
     *
     * <p>Handles {@code &amp;a} style codes, {@code &amp;#rrggbb} hex, and the
     * {@code &amp;x&amp;r&amp;r&amp;g&amp;g&amp;b&amp;b} form some plugins write, under either
     * {@code &amp;} or the section sign.
     */
    static String legacyToMiniMessage(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char current = raw.charAt(i);
            if (!isLegacyMarker(current) || i + 1 >= raw.length()) {
                out.append(current);
                i++;
                continue;
            }

            char code = Character.toLowerCase(raw.charAt(i + 1));

            // &x&r&r&g&g&b&b - six code pairs spelling out one hex colour.
            if (code == 'x' && i + 13 < raw.length()) {
                String hex = readSpacedHex(raw, i);
                if (hex != null) {
                    out.append("<#").append(hex).append('>');
                    i += 14;
                    continue;
                }
            }

            // &#rrggbb
            if (code == '#' && i + 7 < raw.length()) {
                String hex = raw.substring(i + 2, i + 8);
                if (isHex(hex)) {
                    out.append("<#").append(hex.toLowerCase(Locale.ROOT)).append('>');
                    i += 8;
                    continue;
                }
            }

            int index = LEGACY_CODES.indexOf(code);
            if (index >= 0) {
                out.append('<').append(LEGACY_TAGS[index]).append('>');
                i += 2;
                continue;
            }

            out.append(current);
            i++;
        }
        return out.toString();
    }

    /** The hex colour spelled by an {@code &x&r&r&g&g&b&b} run, or null if it is not one. */
    private static String readSpacedHex(String raw, int start) {
        StringBuilder hex = new StringBuilder(6);
        for (int pair = 0; pair < 6; pair++) {
            int marker = start + 2 + pair * 2;
            if (!isLegacyMarker(raw.charAt(marker))) return null;
            char digit = raw.charAt(marker + 1);
            if (Character.digit(digit, 16) < 0) return null;
            hex.append(Character.toLowerCase(digit));
        }
        return hex.toString();
    }

    private static boolean isLegacyMarker(char c) {
        return c == '&' || c == '§';
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
