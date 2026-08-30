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
        String[] keywords = {
            // DML / DDL
            "WITH", "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "DATABASE", "DROP", "USE", "TABLE", "SHOW", "DATABASES", "TABLES",
            "DESCRIBE", "DESC", "IF", "NOT", "EXISTS", "ORDER", "BY", "LIMIT", "ASC",
            "LIKE", "AND", "OR", "NULL", "CALL", "HELP",
            "USER", "IDENTIFIED", "GRANT", "PRIVILEGES", "FLUSH", "ON", "TO", "EXPORT", "IMPORT",
            "ALTER", "TRUNCATE", "RENAME", "REVOKE", "START", "TRANSACTION", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "ADD",
            "JOIN", "INNER", "LEFT", "RIGHT", "CROSS", "UNION", "ALL", "DISTINCT", "AS", "GROUP", "HAVING", "IS",
            "COLUMN", "COLUMNS", "FIRST", "AFTER", "ENGINE", "CHARACTER", "MODIFY", "CHANGE", "CONSTRAINT", "CONVERT", "COLLATE", "CHARSET",
            "FUNCTION", "RETURNS", "RETURN", "DECLARE", "END", "THEN", "ELSE", "WHILE", "LOOP", "REPEAT", "UNTIL", "CASE", "WHEN", "STATUS",
            "OVER", "PARTITION",
            // Column constraints
            "PRIMARY", "KEY", "AUTO_INCREMENT", "UNIQUE", "DEFAULT", "COMMENT", "DUPLICATE",
            "FOREIGN", "REFERENCES", "CHECK", "BETWEEN", "IN", "INDEX", "FULLTEXT", "SPATIAL", "NO", "ACTION", "RESTRICT", "CASCADE",
            "UNSIGNED", "SIGNED", "ZEROFILL", "VISIBLE", "INVISIBLE", "GENERATED", "ALWAYS", "AS", "VIRTUAL", "STORED",
            // ── Numeric types ──
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
            "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC", "BIT",
            // ── String types ──
            "CHAR", "VARCHAR", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            "BINARY", "VARBINARY", "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB",
            "ENUM", "SET",
            // ── Date/time types ──
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "YEAR",
            // ── JSON ──
            "JSON",
            // ── Spatial types ──
            "GEOMETRY", "POINT", "LINESTRING", "POLYGON",
            "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION"
        };
        for (String kw : keywords) {
            KEYWORDS.add(kw.toUpperCase());
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
            char c = input.charAt(pos);

            // 1. Skip whitespace
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            // 2. Skip Comments
            if (c == '#' || (c == '-' && pos + 1 < length && input.charAt(pos + 1) == '-')) {
                // Consume until newline
                while (pos < length && input.charAt(pos) != '\n' && input.charAt(pos) != '\r') {
                    pos++;
                }
                continue;
            }

            int tokenPos = pos;

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

            // 1-char symbols
            if (c == '=' || c == '<' || c == '>' || c == '(' || c == ')' || c == ',' || c == ';' || c == '*' || c == '.' || c == '@' || c == '+' || c == '-' || c == '/' || c == '%') {
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
