package com.yourname.sellplugin.util;

import java.util.Locale;

public final class ItemNameFormatter {

    private ItemNameFormatter() {
    }

    public static String formatKey(String key) {
        if (key == null || key.isBlank()) return "";

        String[] parts = key.split(":", 2);
        String materialName = formatWords(parts[0]);
        if (parts.length == 1) return materialName;

        return materialName + " (" + formatWords(parts[1]) + ")";
    }

    private static String formatWords(String raw) {
        String[] words = raw.toLowerCase(Locale.ENGLISH).split("[_ ]+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1));
        }
        return builder.toString();
    }
}
