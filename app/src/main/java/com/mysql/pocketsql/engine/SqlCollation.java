package com.mysql.pocketsql.engine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlCollation {

    public static class CharsetInfo {
        public final String name;
        public final String desc;
        public final String defaultCollation;
        public final int maxlen;

        public CharsetInfo(String name, String desc, String defaultCollation, int maxlen) {
            this.name = name;
            this.desc = desc;
            this.defaultCollation = defaultCollation;
            this.maxlen = maxlen;
        }
    }

    public static class CollationInfo {
        public final String name;
        public final String charset;
        public final long id;
        public final boolean isDefault;
        public final boolean isCompiled;
        public final int sortlen;

        public CollationInfo(String name, String charset, long id, boolean isDefault, boolean isCompiled, int sortlen) {
            this.name = name;
            this.charset = charset;
            this.id = id;
            this.isDefault = isDefault;
            this.isCompiled = isCompiled;
            this.sortlen = sortlen;
        }
    }

    public static final List<CharsetInfo> CHARSETS = new ArrayList<>();
    public static final List<CollationInfo> COLLATIONS = new ArrayList<>();

    static {
        CHARSETS.add(new CharsetInfo("utf8mb4", "UTF-8 Unicode", "utf8mb4_0900_ai_ci", 4));
        CHARSETS.add(new CharsetInfo("utf8", "UTF-8 Unicode (old)", "utf8_general_ci", 3));
        CHARSETS.add(new CharsetInfo("latin1", "cp1252 West European", "latin1_swedish_ci", 1));
        CHARSETS.add(new CharsetInfo("ascii", "US ASCII", "ascii_general_ci", 1));
        CHARSETS.add(new CharsetInfo("binary", "Binary pseudo-charset", "binary", 1));

        COLLATIONS.add(new CollationInfo("utf8mb4_0900_ai_ci", "utf8mb4", 255, true, true, 1));
        COLLATIONS.add(new CollationInfo("utf8mb4_general_ci", "utf8mb4", 45, false, true, 1));
        COLLATIONS.add(new CollationInfo("utf8mb4_unicode_ci", "utf8mb4", 224, false, true, 8));
        COLLATIONS.add(new CollationInfo("utf8mb4_bin", "utf8mb4", 46, false, true, 1));
        
        COLLATIONS.add(new CollationInfo("utf8_general_ci", "utf8", 33, true, true, 1));
        COLLATIONS.add(new CollationInfo("utf8_unicode_ci", "utf8", 192, false, true, 8));
        COLLATIONS.add(new CollationInfo("utf8_bin", "utf8", 83, false, true, 1));

        COLLATIONS.add(new CollationInfo("latin1_swedish_ci", "latin1", 8, true, true, 1));
        COLLATIONS.add(new CollationInfo("latin1_general_ci", "latin1", 48, false, true, 1));
        COLLATIONS.add(new CollationInfo("latin1_bin", "latin1", 47, false, true, 1));

        COLLATIONS.add(new CollationInfo("ascii_general_ci", "ascii", 11, true, true, 1));
        COLLATIONS.add(new CollationInfo("ascii_bin", "ascii", 65, false, true, 1));
        
        COLLATIONS.add(new CollationInfo("binary", "binary", 63, true, true, 1));
    }

    public static boolean isValidCharset(String charset) {
        if (charset == null) return false;
        String c = charset.toLowerCase().trim();
        for (CharsetInfo info : CHARSETS) {
            if (info.name.equals(c)) return true;
        }
        return false;
    }

    public static boolean isValidCollation(String collation) {
        if (collation == null) return false;
        String c = collation.toLowerCase().trim();
        for (CollationInfo info : COLLATIONS) {
            if (info.name.equals(c)) return true;
        }
        return false;
    }

    public static String getDefaultCollationForCharset(String charset) {
        if (charset == null) return "utf8mb4_0900_ai_ci";
        String c = charset.toLowerCase().trim();
        for (CharsetInfo info : CHARSETS) {
            if (info.name.equals(c)) return info.defaultCollation;
        }
        return "utf8mb4_0900_ai_ci";
    }

    public static String getCharsetForCollation(String collation) {
        if (collation == null) return "utf8mb4";
        String c = collation.toLowerCase().trim();
        for (CollationInfo info : COLLATIONS) {
            if (info.name.equals(c)) return info.charset;
        }
        return "utf8mb4";
    }

    public static String stripAccents(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public static int compare(String s1, String s2, String collation) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;

        String collLower = (collation != null) ? collation.toLowerCase().trim() : "utf8mb4_0900_ai_ci";

        boolean accentInsensitive = collLower.contains("_ai") || collLower.contains("general_ci") || collLower.contains("unicode_ci");
        boolean caseInsensitive = collLower.contains("_ci");
        boolean isBinary = collLower.contains("_bin") || "binary".equals(collLower);

        if (isBinary) {
            return s1.compareTo(s2);
        }

        String normalize1 = s1;
        String normalize2 = s2;

        if (accentInsensitive) {
            normalize1 = stripAccents(normalize1);
            normalize2 = stripAccents(normalize2);
        }

        if (caseInsensitive) {
            return normalize1.compareToIgnoreCase(normalize2);
        } else {
            return normalize1.compareTo(normalize2);
        }
    }

    public static QueryResult showCharacterSets() {
        List<String> columns = new ArrayList<>();
        columns.add("Charset");
        columns.add("Description");
        columns.add("Default collation");
        columns.add("Maxlen");

        List<String> types = new ArrayList<>();
        types.add("TEXT");
        types.add("TEXT");
        types.add("TEXT");
        types.add("INT");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CharsetInfo info : CHARSETS) {
            Map<String, Object> row = new HashMap<>();
            row.put("Charset", info.name);
            row.put("Description", info.desc);
            row.put("Default collation", info.defaultCollation);
            row.put("Maxlen", (long) info.maxlen);
            rows.add(row);
        }
        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }

    public static QueryResult showCollations() {
        List<String> columns = new ArrayList<>();
        columns.add("Collation");
        columns.add("Charset");
        columns.add("Id");
        columns.add("Default");
        columns.add("Compiled");
        columns.add("Sortlen");

        List<String> types = new ArrayList<>();
        types.add("TEXT");
        types.add("TEXT");
        types.add("INT");
        types.add("TEXT");
        types.add("TEXT");
        types.add("INT");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CollationInfo info : COLLATIONS) {
            Map<String, Object> row = new HashMap<>();
            row.put("Collation", info.name);
            row.put("Charset", info.charset);
            row.put("Id", info.id);
            row.put("Default", info.isDefault ? "Yes" : "");
            row.put("Compiled", info.isCompiled ? "Yes" : "");
            row.put("Sortlen", (long) info.sortlen);
            rows.add(row);
        }
        return QueryResult.createSelectSuccess(columns, types, rows, 0);
    }
}
