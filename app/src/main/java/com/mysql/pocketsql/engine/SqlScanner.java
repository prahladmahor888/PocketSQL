package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SqlScanner {
    private final String input;
    private final int length;
    private int pos;

    private static final Set<String> KEYWORDS = new HashSet<>();
    static {
        for (String raw : SqlKeywordSuggester.getKeywords()) {
            if (raw == null) continue;
            // Clean function parentheses e.g. "CONCAT()" -> "CONCAT"
            String cleaned = raw.replaceAll("\\(\\)", "").trim();
            // Split multi-word keywords e.g. "NOT NULL" -> "NOT", "NULL"
            for (String part : cleaned.split("\\s+")) {
                if (!part.isEmpty()) {
                    KEYWORDS.add(part.toUpperCase());
                }
            }
        }
    }

    public SqlScanner(String input) {
        this.input = input;
        this.length = input.length();
        this.pos = 0;
    }

    public List<SqlToken> scan() throws SqlSyntaxException {
        List<SqlToken> tokens = new ArrayList<>();
        while (pos < length) {
            int tokenPos = pos;
            char c = input.charAt(pos);

            // 1. Skip whitespace
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            // 2. Skip Comments
            // Block comments /* ... */
            if (c == '/' && pos + 1 < length && input.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos < length) {
                    if (input.charAt(pos) == '*' && pos + 1 < length && input.charAt(pos + 1) == '/') {
                        pos += 2;
                        break;
                    }
                    pos++;
                }
                continue;
            }

            // Single line comments -- or #
            if (c == '#' || (c == '-' && pos + 1 < length && input.charAt(pos + 1) == '-')) {
                if (c == '-') {
                    if (pos + 2 < length) {
                        char nextChar = input.charAt(pos + 2);
                        if (!Character.isWhitespace(nextChar) && nextChar != '\n' && nextChar != '\r') {
                            tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "-", tokenPos));
                            pos++;
                            continue;
                        }
                    }
                }

                boolean hasNewline = input.indexOf('\n', pos) != -1 || input.indexOf('\r', pos) != -1;
                pos += (c == '#' ? 1 : 2);

                if (hasNewline) {
                    while (pos < length && input.charAt(pos) != '\n' && input.charAt(pos) != '\r') {
                        pos++;
                    }
                } else {
                    int commentStart = pos;
                    int nextBoundary = length;
                    for (int p = commentStart; p < length; p++) {
                        char ch = input.charAt(p);
                        if (Character.isLetter(ch)) {
                            int wordEnd = p;
                            while (wordEnd < length && (Character.isLetterOrDigit(input.charAt(wordEnd)) || input.charAt(wordEnd) == '_')) {
                                wordEnd++;
                            }
                            String word = input.substring(p, wordEnd).toUpperCase();
                            int afterWord = wordEnd;
                            while (afterWord < length && Character.isWhitespace(input.charAt(afterWord))) {
                                afterWord++;
                            }
                            boolean isFuncCall = (afterWord < length && input.charAt(afterWord) == '(');
                            boolean isClauseKw = KEYWORDS.contains(word) && ("SELECT".equals(word) || "FROM".equals(word) || "WHERE".equals(word) || "GROUP".equals(word) || "ORDER".equals(word) || "HAVING".equals(word) || "LIMIT".equals(word) || "JOIN".equals(word) || "SET".equals(word) || "VALUES".equals(word) || "INSERT".equals(word) || "UPDATE".equals(word) || "DELETE".equals(word));
                            
                            if ((isFuncCall || isClauseKw) && p > commentStart + 1) {
                                nextBoundary = p;
                                break;
                            }
                        }
                    }
                    pos = nextBoundary;
                }
                continue;
            }

            // 3. Match Operators & Symbols (2-char symbols first)
            if (c == '!' && pos + 1 < length && input.charAt(pos + 1) == '=') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "!=", tokenPos));
                pos += 2;
                continue;
            }
            if (c == '<' && pos + 1 < length && input.charAt(pos + 1) == '>') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "<>", tokenPos));
                pos += 2;
                continue;
            }
            if (c == '<' && pos + 1 < length && input.charAt(pos + 1) == '=') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "<=", tokenPos));
                pos += 2;
                continue;
            }
            if (c == '>' && pos + 1 < length && input.charAt(pos + 1) == '=') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, ">=", tokenPos));
                pos += 2;
                continue;
            }
            if (c == '<' && pos + 1 < length && input.charAt(pos + 1) == '<') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, "<<", tokenPos));
                pos += 2;
                continue;
            }
            if (c == '>' && pos + 1 < length && input.charAt(pos + 1) == '>') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, ">>", tokenPos));
                pos += 2;
                continue;
            }

            // 1-char symbols
            if (c == '=' || c == '<' || c == '>' || c == '(' || c == ')' || c == ',' || c == ';' || c == '*' || c == '.' || c == '@' || c == '+' || c == '-' || c == '/' || c == '%' || c == '|' || c == '&' || c == '^' || c == '~' || c == ':' || c == '?') {
                tokens.add(new SqlToken(SqlToken.Type.SYMBOL, String.valueOf(c), tokenPos));
                pos++;
                continue;
            }

            // 4. String Literals
            if (c == '\'' || c == '"') {
                char quote = c;
                pos++; // skip opening quote
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (pos < length) {
                    char next = input.charAt(pos);
                    if (next == quote) {
                        closed = true;
                        pos++; // skip closing quote
                        break;
                    }
                    // Handle simple backslash escapes
                    if (next == '\\' && pos + 1 < length) {
                        pos++;
                        next = input.charAt(pos);
                    }
                    sb.append(next);
                    pos++;
                }
                if (!closed) {
                    throw new SqlSyntaxException("Unterminated string literal starting at position " + tokenPos, tokenPos);
                }
                tokens.add(new SqlToken(SqlToken.Type.STRING, sb.toString(), tokenPos));
                continue;
            }

            // 5. Numeric Literals
            if (Character.isDigit(c) || (c == '.' && pos + 1 < length && Character.isDigit(input.charAt(pos + 1)))) {
                StringBuilder sb = new StringBuilder();
                boolean hasDot = false;
                while (pos < length) {
                    char next = input.charAt(pos);
                    if (Character.isDigit(next)) {
                        sb.append(next);
                        pos++;
                    } else if (next == '.' && !hasDot) {
                        hasDot = true;
                        sb.append(next);
                        pos++;
                    } else {
                        break;
                    }
                }
                tokens.add(new SqlToken(SqlToken.Type.NUMBER, sb.toString(), tokenPos));
                continue;
            }

            // 6. Keywords & Identifiers (e.g. table names, column names)
            if (Character.isLetter(c) || c == '_' || c == '$') {
                StringBuilder sb = new StringBuilder();
                while (pos < length) {
                    char next = input.charAt(pos);
                    if (Character.isLetterOrDigit(next) || next == '_' || next == '$') {
                        sb.append(next);
                        pos++;
                    } else {
                        break;
                    }
                }
                String lexeme = sb.toString();
                String upperLexeme = lexeme.toUpperCase();
                if (KEYWORDS.contains(upperLexeme)) {
                    tokens.add(new SqlToken(SqlToken.Type.KEYWORD, lexeme, tokenPos));
                } else {
                    tokens.add(new SqlToken(SqlToken.Type.IDENTIFIER, lexeme, tokenPos));
                }
                continue;
            }

            // Unrecognized character
            throw new SqlSyntaxException("Unexpected character '" + c + "'", tokenPos);
        }

        tokens.add(new SqlToken(SqlToken.Type.EOF, "", pos));
        return tokens;
    }
}
