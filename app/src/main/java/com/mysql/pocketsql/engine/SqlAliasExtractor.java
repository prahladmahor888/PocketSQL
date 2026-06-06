package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqlAliasExtractor {

    /**
     * Extracts table aliases from a SQL string in an error-tolerant way.
     * Returns a map of alias (lowercase) to table name.
     */
    public static Map<String, String> extractAliases(String sql) {
        Map<String, String> aliases = new HashMap<>();
        if (sql == null || sql.trim().isEmpty()) {
            return aliases;
        }

        // Tokenize SQL string in a simple, error-tolerant way.
        List<String> tokens = new ArrayList<>();
        int len = sql.length();
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == ',' || c == '.' || c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char next = sql.charAt(i);
                    if (Character.isLetterOrDigit(next) || next == '_' || next == '$') {
                        sb.append(next);
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(sb.toString());
            } else if (c == '\'' || c == '"' || c == '`') {
                // String or backtick literal - skip it safely
                char quote = c;
                i++;
                while (i < len && sql.charAt(i) != quote) {
                    if (sql.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i++; // skip closing quote
            } else {
                i++; // ignore other characters (like operators)
            }
        }

        int size = tokens.size();
        int idx = 0;

        Set<String> keywords = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "JOIN", "INNER", "LEFT", "RIGHT", "CROSS", "ON", "WHERE", "ORDER", "BY",
            "GROUP", "LIMIT", "HAVING", "AS", "AND", "OR", "NOT", "UNION", "INSERT", "UPDATE", "DELETE", "SET"
        ));

        while (idx < size) {
            String token = tokens.get(idx);
            String tokenUpper = token.toUpperCase();

            if (tokenUpper.equals("FROM") || tokenUpper.equals("JOIN") || tokenUpper.equals("UPDATE")) {
                idx++;
                while (idx < size) {
                    if (tokens.get(idx).equals("(")) {
                        idx++;
                        continue;
                    }

                    String tableName = null;
                    if (idx < size && isIdentifier(tokens.get(idx), keywords)) {
                        tableName = tokens.get(idx);
                        idx++;

                        // Check if it's db_name.table_name
                        if (idx < size && tokens.get(idx).equals(".")) {
                            idx++; // skip '.'
                            if (idx < size && isIdentifier(tokens.get(idx), keywords)) {
                                tableName = tokens.get(idx); // actual table name is the second one
                                idx++;
                            }
                        }
                    }

                    if (tableName == null) {
                        break;
                    }

                    String alias = null;
                    if (idx < size) {
                        String next = tokens.get(idx);
                        String nextUpper = next.toUpperCase();
                        if (nextUpper.equals("AS")) {
                            idx++; // skip AS
                            if (idx < size && isIdentifier(tokens.get(idx), keywords)) {
                                alias = tokens.get(idx);
                                idx++;
                            }
                        } else if (isIdentifier(next, keywords)) {
                            alias = next;
                            idx++;
                        }
                    }

                    if (alias != null) {
                        aliases.put(alias.toLowerCase(), tableName);
                    }

                    // Check if comma-separated table list
                    if (idx < size && tokens.get(idx).equals(",")) {
                        idx++;
                    } else {
                        break;
                    }
                }
            } else {
                idx++;
            }
        }

        return aliases;
    }

    private static boolean isIdentifier(String token, Set<String> keywords) {
        if (token.length() == 1 && (token.equals(",") || token.equals(".") || token.equals("(") || token.equals(")"))) {
            return false;
        }
        return !keywords.contains(token.toUpperCase());
    }
}
