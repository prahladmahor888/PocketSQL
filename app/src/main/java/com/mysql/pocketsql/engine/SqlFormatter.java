package com.mysql.pocketsql.engine;

import java.util.List;

public class SqlFormatter {

    public static String formatSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        try {
            SqlScanner scanner = new SqlScanner(sql);
            List<SqlToken> tokens = scanner.scan();
            return formatTokens(tokens);
        } catch (Exception e) {
            return sql;
        }
    }

    public static String formatTokens(List<SqlToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        boolean needsSpace = false;
        boolean newlinePending = false;

        for (int i = 0; i < tokens.size(); i++) {
            SqlToken tok = tokens.get(i);
            if (tok.type == SqlToken.Type.EOF) {
                break;
            }

            String val = tok.value;
            String upperVal = val.toUpperCase();

            // 1. Identify composite keywords / block changes
            boolean isGroupBy = "GROUP".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "BY".equalsIgnoreCase(tokens.get(i + 1).value));
            boolean isOrderBy = "ORDER".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "BY".equalsIgnoreCase(tokens.get(i + 1).value));
            boolean isEndIf = "END".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "IF".equalsIgnoreCase(tokens.get(i + 1).value));
            boolean isEndWhile = "END".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "WHILE".equalsIgnoreCase(tokens.get(i + 1).value));
            boolean isEndLoop = "END".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "LOOP".equalsIgnoreCase(tokens.get(i + 1).value));
            boolean isEndRepeat = "END".equalsIgnoreCase(val) && (i + 1 < tokens.size() && "REPEAT".equalsIgnoreCase(tokens.get(i + 1).value));

            // Keywords that should start a newline
            boolean isNewlineKeyword = false;
            if (tok.type == SqlToken.Type.KEYWORD) {
                if (upperVal.equals("SELECT") || upperVal.equals("FROM") || upperVal.equals("WHERE") ||
                    upperVal.equals("HAVING") || upperVal.equals("LIMIT") || upperVal.equals("UNION") ||
                    upperVal.equals("JOIN") || upperVal.equals("LEFT") || upperVal.equals("RIGHT") ||
                    upperVal.equals("INNER") || upperVal.equals("CROSS") || upperVal.equals("BEGIN") ||
                    upperVal.equals("DECLARE") || upperVal.equals("SET") || upperVal.equals("INSERT") ||
                    upperVal.equals("UPDATE") || upperVal.equals("DELETE") || upperVal.equals("RETURN") ||
                    upperVal.equals("IF") || upperVal.equals("WHILE") || upperVal.equals("LOOP") ||
                    upperVal.equals("REPEAT") || upperVal.equals("ELSE") || upperVal.equals("ELSEIF") ||
                    upperVal.equals("END") || isGroupBy || isOrderBy) {
                    isNewlineKeyword = true;
                }
            }

            // Adjust indent levels BEFORE printing the token for closing structures
            if (isEndIf || isEndWhile || isEndLoop || isEndRepeat) {
                indentLevel = Math.max(0, indentLevel - 1);
                newlinePending = true;
            } else if ("END".equalsIgnoreCase(val)) {
                indentLevel = Math.max(0, indentLevel - 1);
                newlinePending = true;
            } else if ("ELSE".equalsIgnoreCase(val) || "ELSEIF".equalsIgnoreCase(val)) {
                indentLevel = Math.max(0, indentLevel - 1);
                newlinePending = true;
            } else if ("FROM".equalsIgnoreCase(val) || "WHERE".equalsIgnoreCase(val) || 
                       "HAVING".equalsIgnoreCase(val) || "LIMIT".equalsIgnoreCase(val) || 
                       "UNION".equalsIgnoreCase(val) || "JOIN".equalsIgnoreCase(val) || 
                       "LEFT".equalsIgnoreCase(val) || "RIGHT".equalsIgnoreCase(val) || 
                       "INNER".equalsIgnoreCase(val) || "CROSS".equalsIgnoreCase(val) ||
                       isGroupBy || isOrderBy) {
                newlinePending = true;
            }

            if (isNewlineKeyword && !isEndIf && !isEndWhile && !isEndLoop && !isEndRepeat && !"END".equalsIgnoreCase(val) && !"ELSE".equalsIgnoreCase(val) && !"ELSEIF".equalsIgnoreCase(val) && !isGroupBy && !isOrderBy &&
                !("FROM".equalsIgnoreCase(val) || "WHERE".equalsIgnoreCase(val) || 
                  "HAVING".equalsIgnoreCase(val) || "LIMIT".equalsIgnoreCase(val) || 
                  "UNION".equalsIgnoreCase(val) || "JOIN".equalsIgnoreCase(val) || 
                  "LEFT".equalsIgnoreCase(val) || "RIGHT".equalsIgnoreCase(val) || 
                  "INNER".equalsIgnoreCase(val) || "CROSS".equalsIgnoreCase(val))) {
                // For other newline keywords (like SELECT, DECLARE, SET, IF, WHILE), force newline
                newlinePending = true;
            }

            // Handle newline insertion
            if (newlinePending) {
                if (sb.length() > 0) {
                    sb.append("\n");
                    for (int l = 0; l < indentLevel; l++) {
                        sb.append("    ");
                    }
                }
                newlinePending = false;
                needsSpace = false;
            }

            // 2. Determine space prefix before printing this token
            if (needsSpace) {
                // No space before comma, semicolon, closing parentheses, period
                if (",".equals(val) || ";".equals(val) || ")".equals(val) || ".".equals(val)) {
                    // no space
                } else if ("(".equals(val) && i > 0 && 
                           (tokens.get(i - 1).type == SqlToken.Type.IDENTIFIER || 
                            tokens.get(i - 1).type == SqlToken.Type.KEYWORD)) {
                    // no space
                } else {
                    sb.append(" ");
                }
            }

            // Print the token (handling strings with quotes)
            if (tok.type == SqlToken.Type.STRING) {
                sb.append("'").append(val.replace("'", "\\'")).append("'");
            } else {
                sb.append(val);
            }

            // 3. Adjust state and indent levels AFTER printing the token for opening structures
            if ("BEGIN".equalsIgnoreCase(val)) {
                indentLevel++;
                newlinePending = true;
            } else if ("THEN".equalsIgnoreCase(val)) {
                indentLevel++;
                newlinePending = true;
            } else if ("DO".equalsIgnoreCase(val)) {
                indentLevel++;
                newlinePending = true;
            } else if ("ELSE".equalsIgnoreCase(val)) {
                indentLevel++;
                newlinePending = true;
            }

            // Newline after semicolon
            if (";".equals(val)) {
                newlinePending = true;
            }

            // Determine if next token needs a space before it
            if ("(".equals(val) || ".".equals(val) || "@".equals(val)) {
                needsSpace = false;
            } else {
                needsSpace = true;
            }

            // Special composite keyword skip: e.g. print BY as part of GROUP BY on same line
            if (isGroupBy || isOrderBy || isEndIf || isEndWhile || isEndLoop || isEndRepeat) {
                // Consume the next token immediately so it's printed as part of this composite phrase
                i++;
                SqlToken nextTok = tokens.get(i);
                sb.append(" ").append(nextTok.value);
            }
        }

        return sb.toString().trim();
    }
}
