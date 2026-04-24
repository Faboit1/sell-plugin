package com.yourname.sellplugin.util;

/**
 * Formats large numbers into compact, human-readable strings.
 * Examples: 1245 → "1.2k", 1_234_567 → "1.2m", 30.5 → "30.50", 36 → "36"
 */
public final class NumberFormatter {

    private NumberFormatter() {}

    private static final double THOUSAND  = 1_000.0;
    private static final double MILLION   = 1_000_000.0;
    private static final double BILLION   = 1_000_000_000.0;
    private static final double TRILLION  = 1_000_000_000_000.0;

    /**
     * Converts a double to a compact string:
     * <ul>
     *   <li>≥ 1t  → e.g. "1.2t"</li>
     *   <li>≥ 1b  → e.g. "3.4b"</li>
     *   <li>≥ 1m  → e.g. "5.6m"</li>
     *   <li>≥ 1k  → e.g. "7.8k"</li>
     *   <li>Whole → e.g. "36"</li>
     *   <li>Other → e.g. "12.50"</li>
     * </ul>
     */
    public static String format(double value) {
        if (value < 0) return "-" + format(-value);

        if (value >= TRILLION) return suffix(value / TRILLION, "t");
        if (value >= BILLION)  return suffix(value / BILLION,  "b");
        if (value >= MILLION)  return suffix(value / MILLION,  "m");
        if (value >= THOUSAND) return suffix(value / THOUSAND, "k");

        // Small values
        long asLong = (long) value;
        if (value == asLong) return Long.toString(asLong);
        return String.format("%.2f", value);
    }

    private static String suffix(double divided, String unit) {
        // 1 decimal place; strip trailing ".0"
        String s = String.format("%.1f", divided);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + unit;
    }
}
