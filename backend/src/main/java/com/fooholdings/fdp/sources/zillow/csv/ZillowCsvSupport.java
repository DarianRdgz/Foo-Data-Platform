package com.fooholdings.fdp.sources.zillow.csv;

import java.util.ArrayList;
import java.util.List;

public final class ZillowCsvSupport {

    private ZillowCsvSupport() {
    }

    public static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        out.add(current.toString());
        return out;
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static boolean hasText(String raw) {
        return raw != null && !raw.trim().isEmpty();
    }
}