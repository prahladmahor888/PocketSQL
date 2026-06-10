package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlParser {
    final List<SqlToken> tokens;
    final String sql;
    int pos;

    public SqlParser(List<SqlToken> tokens) {
        this(tokens, null);
    }

    public SqlParser(List<SqlToken> tokens, String sql) {
        this.tokens = tokens;
        this.sql = sql;
        this.pos = 0;
    }

    SqlToken peek() {
        if (pos < tokens.size()) {
            return tokens.get(pos);
        }
        return new SqlToken(SqlToken.Type.EOF, "", pos);
    }

    SqlToken consume() {
        SqlToken t = peek();
        if (t.type != SqlToken.Type.EOF) {
            pos++;
        }
        return t;
    }

    private boolean match(SqlToken.Type type) {
        if (peek().type == type) {
            consume();
            return true;
        }
        return false;
    }

    boolean matchKeyword(String keyword) {
        SqlToken t = peek();
        if (t.type == SqlToken.Type.KEYWORD && keyword.equalsIgnoreCase(t.value)) {
            consume();
            return true;
        }
        return false;
    }

    boolean matchName(String name) {
        SqlToken t = peek();
        if ((t.type == SqlToken.Type.IDENTIFIER || t.type == SqlToken.Type.KEYWORD) && name.equalsIgnoreCase(t.value)) {
            consume();
            return true;
        }
        return false;
    }

    boolean matchSymbol(String symbol) {
        SqlToken t = peek();
        if (t.type == SqlToken.Type.SYMBOL && symbol.equals(t.value)) {
            consume();
            return true;
        }
        return false;
    }

    private static final java.util.Set<String> NON_IDENTIFIER_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP",
        "ALTER", "USE", "SHOW", "FROM", "WHERE", "JOIN",
        "ON", "AND", "OR", "NULL", "TRUE", "FALSE",
        "AS", "LIMIT", "ORDER", "GROUP", "HAVING", "UNION",
        "DELIMITER", "VALUES", "EXPORT", "IMPORT"
    ));

    private boolean isIdentifier(SqlToken token) {
        if (token == null) return false;
        if (token.type == SqlToken.Type.IDENTIFIER) {
            return true;
        }
        if (token.type == SqlToken.Type.KEYWORD) {
            return !NON_IDENTIFIER_KEYWORDS.contains(token.value.toUpperCase());
        }
        return false;
    }

    void expect(SqlToken.Type type, String errorMsg) throws SqlSyntaxException {
        SqlToken t = peek();
        boolean match = (t.type == type);
        if (type == SqlToken.Type.IDENTIFIER) {
            match = isIdentifier(t);
        }
        if (!match) {
            throw new SqlSyntaxException(errorMsg + ", found " + t.type + " '" + t.value + "'", t.position);
        }
        consume();
    }

    void expectKeyword(String keyword, String errorMsg) throws SqlSyntaxException {
        SqlToken t = peek();
        if (t.type != SqlToken.Type.KEYWORD || !keyword.equalsIgnoreCase(t.value)) {
            throw new SqlSyntaxException(errorMsg + ", found '" + t.value + "'", t.position);
        }
        consume();
    }

    void expectSymbol(String symbol, String errorMsg) throws SqlSyntaxException {
        SqlToken t = peek();
        if (t.type != SqlToken.Type.SYMBOL || !symbol.equals(t.value)) {
            throw new SqlSyntaxException(errorMsg + ", found '" + t.value + "'", t.position);
        }
        consume();
    }

    String parseTableName(String errorMsg) throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, errorMsg);
        String tableName = tokens.get(pos - 1).value;
        if (matchSymbol(".")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected table name after '.'");
            tableName = tableName + "." + tokens.get(pos - 1).value;
        }
        return tableName;
    }

    public Command parse() throws SqlSyntaxException {
        SqlToken t = peek();
        if (t.type == SqlToken.Type.EOF) {
            throw new SqlSyntaxException("Empty query", 0);
        }

        Command cmd;
        if (t.type == SqlToken.Type.KEYWORD) {
            switch (t.value.toUpperCase()) {
                case "CREATE":
                    cmd = parseCreate();
                    break;
                case "DROP":
                    cmd = parseDrop();
                    break;
                case "USE":
                    cmd = parseUse();
                    break;
                case "SHOW":
                    cmd = parseShow();
                    break;
                case "DESCRIBE":
                case "DESC":
                    cmd = parseDescribe();
                    break;
                case "INSERT":
                    cmd = parseInsert();
                    break;
                case "SELECT":
                    cmd = parseSelect();
                    break;
                case "UPDATE":
                    cmd = parseUpdate();
                    break;
                case "DELETE":
                    cmd = parseDelete();
                    break;
                case "GRANT":
                    cmd = parseGrant();
                    break;
                case "REVOKE":
                    cmd = parseRevoke();
                    break;
                case "FLUSH":
                    cmd = parseFlush();
                    break;
                case "ALTER":
                    cmd = parseAlter();
                    break;
                case "TRUNCATE":
                    cmd = parseTruncate();
                    break;
                case "RENAME":
                    cmd = parseRename();
                    break;
                case "START":
                    cmd = parseStart();
                    break;
                case "BEGIN":
                    cmd = parseBegin();
                    break;
                case "COMMIT":
                    cmd = parseCommit();
                    break;
                case "ROLLBACK":
                    cmd = parseRollback();
                    break;
                case "SAVEPOINT":
                    cmd = parseSavepoint();
                    break;
                case "SET":
                    cmd = parseSet();
                    break;
                case "CALL":
                    cmd = parseCall();
                    break;
                case "HELP":
                    cmd = parseHelp();
                    break;
                case "EXPORT":
                    cmd = parseExport();
                    break;
                case "IMPORT":
                    cmd = parseImport();
                    break;
                default:
                    throw new SqlSyntaxException("Unsupported SQL command keyword '" + t.value + "'", t.position);
            }
        } else {
            throw new SqlSyntaxException("SQL queries must start with a keyword", t.position);
        }

        // Optional trailing semicolon
        matchSymbol(";");

        if (peek().type != SqlToken.Type.EOF) {
            throw new SqlSyntaxException("Unexpected tokens after SQL command end at '" + peek().value + "'", peek().position);
        }

        return cmd;
    }

    private Command parseSet() throws SqlSyntaxException {
        consume(); // SET
        
        matchName("GLOBAL");
        matchName("SESSION");
        boolean isUserVar = false;
        if (matchSymbol("@")) {
            isUserVar = true;
            if (matchSymbol("@")) {
                isUserVar = false; // @@ is system variable
                if (matchName("global") || matchName("session")) {
                    matchSymbol(".");
                }
            }
        }
        
        SqlToken varToken = peek();
        if (varToken.type != SqlToken.Type.IDENTIFIER && varToken.type != SqlToken.Type.KEYWORD) {
            throw new SqlSyntaxException("Expected variable name in SET statement", varToken.position);
        }
        
        String varName = varToken.value;
        if (isUserVar) {
            varName = "@" + varName;
        }
        consume();
        
        if ("NAMES".equalsIgnoreCase(varName)) {
            SqlToken charsetToken = peek();
            if (charsetToken.type == SqlToken.Type.IDENTIFIER || charsetToken.type == SqlToken.Type.KEYWORD || charsetToken.type == SqlToken.Type.STRING) {
                consume();
                String charset = charsetToken.value;
                if (matchKeyword("COLLATE")) {
                    SqlToken collToken = peek();
                    if (collToken.type == SqlToken.Type.IDENTIFIER || collToken.type == SqlToken.Type.KEYWORD || collToken.type == SqlToken.Type.STRING) {
                        consume();
                    }
                }
                return new Command.SetVariable("NAMES", charset);
            } else {
                throw new SqlSyntaxException("Expected charset name after SET NAMES", charsetToken.position);
            }
        }
        
        if ("CHARACTER".equalsIgnoreCase(varName)) {
            expectKeyword("SET", "Expected 'SET' after 'SET CHARACTER'");
            SqlToken charsetToken = peek();
            if (charsetToken.type == SqlToken.Type.IDENTIFIER || charsetToken.type == SqlToken.Type.KEYWORD || charsetToken.type == SqlToken.Type.STRING) {
                consume();
                String charset = charsetToken.value;
                return new Command.SetVariable("CHARACTER_SET", charset);
            } else {
                throw new SqlSyntaxException("Expected charset name after SET CHARACTER SET", charsetToken.position);
            }
        }
        
        expectSymbol("=", "Expected '=' in SET statement");
        
        SqlToken valToken = peek();
        Object value;
        if (valToken.type == SqlToken.Type.STRING) {
            consume();
            value = valToken.value;
        } else if (valToken.type == SqlToken.Type.NUMBER) {
            consume();
            if (valToken.value.contains(".")) {
                value = Double.parseDouble(valToken.value);
            } else {
                value = Long.parseLong(valToken.value);
            }
        } else if (valToken.type == SqlToken.Type.KEYWORD) {
            consume();
            String upperVal = valToken.value.toUpperCase();
            if ("NULL".equals(upperVal)) {
                value = null;
            } else if ("ON".equals(upperVal) || "TRUE".equals(upperVal)) {
                value = 1L;
            } else if ("OFF".equals(upperVal) || "FALSE".equals(upperVal)) {
                value = 0L;
            } else {
                value = valToken.value;
            }
        } else if (valToken.type == SqlToken.Type.IDENTIFIER) {
            consume();
            value = valToken.value;
        } else {
            throw new SqlSyntaxException("Expected value for variable assignment in SET statement", valToken.position);
        }
        
        return new Command.SetVariable(varName, value);
    }

    private Command parseCreate() throws SqlSyntaxException {
        int startPos = pos;
        consume(); // CREATE
        if (matchKeyword("DATABASE")) {
            boolean ifNotExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("NOT", "Expected 'NOT' after 'CREATE DATABASE IF'");
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'CREATE DATABASE IF NOT'");
                ifNotExists = true;
            }
            SqlToken dbToken = peek();
            if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected database name", dbToken.position);
            }
            consume();

            // Parse optional CHARACTER SET and COLLATE options
            boolean parsingOptions = true;
            String charset = null;
            String collation = null;
            while (parsingOptions) {
                if (matchName("DEFAULT")) {
                    // skip
                }
                
                if (matchName("CHARACTER")) {
                    matchName("SET"); // optional SET
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        charset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected character set name", valToken.position);
                    }
                } else if (matchName("CHARSET")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        charset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected charset name", valToken.position);
                    }
                } else if (matchName("COLLATE")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        collation = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected collation name", valToken.position);
                    }
                } else {
                    parsingOptions = false;
                }
            }

            return new Command.CreateDatabase(dbToken.value, ifNotExists, charset, collation);
        } else if (matchKeyword("FUNCTION")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected function name after 'CREATE FUNCTION'");
            String functionName = tokens.get(pos - 1).value;

            expectSymbol("(", "Expected '(' after function name");

            List<String> paramNames = new ArrayList<>();
            List<String> paramTypes = new ArrayList<>();

            if (!matchSymbol(")")) {
                do {
                    expect(SqlToken.Type.IDENTIFIER, "Expected parameter name");
                    String paramName = tokens.get(pos - 1).value;
                    
                    expect(SqlToken.Type.KEYWORD, "Expected parameter data type");
                    String paramType = tokens.get(pos - 1).value;
                    
                    if (matchSymbol("(")) {
                        consume(); // length
                        if (matchSymbol(",")) {
                            consume(); // scale
                        }
                        expectSymbol(")", "Expected ')' after type parameters");
                    }

                    paramNames.add(paramName);
                    paramTypes.add(paramType);
                } while (matchSymbol(","));
                
                expectSymbol(")", "Expected ')' after parameter list");
            }

            expectKeyword("RETURNS", "Expected 'RETURNS' keyword");
            
            // Return datatype
            expect(SqlToken.Type.KEYWORD, "Expected return data type");
            String returnType = tokens.get(pos - 1).value;
            if (matchSymbol("(")) {
                consume(); // length
                if (matchSymbol(",")) {
                    consume(); // scale
                }
                expectSymbol(")", "Expected ')' after return type parameters");
            }

            while (peek().type != SqlToken.Type.EOF && !peek().value.equalsIgnoreCase("BEGIN")) {
                consume(); // Skip DETERMINISTIC, etc.
            }
            expectKeyword("BEGIN", "Expected 'BEGIN' keyword");
            
            List<SqlToken> bodyTokens = new ArrayList<>();
            int beginDepth = 1;
            while (peek().type != SqlToken.Type.EOF) {
                SqlToken bodyTok = peek();
                if (bodyTok.type == SqlToken.Type.KEYWORD) {
                    if ("BEGIN".equalsIgnoreCase(bodyTok.value)) {
                        beginDepth++;
                    } else if ("END".equalsIgnoreCase(bodyTok.value)) {
                        boolean isCompoundEnd = false;
                        if (pos + 1 < tokens.size()) {
                            SqlToken nextTok = tokens.get(pos + 1);
                            if (nextTok.type == SqlToken.Type.KEYWORD && 
                                ("IF".equalsIgnoreCase(nextTok.value) || 
                                 "WHILE".equalsIgnoreCase(nextTok.value) || 
                                 "LOOP".equalsIgnoreCase(nextTok.value) || 
                                 "CASE".equalsIgnoreCase(nextTok.value) || 
                                 "REPEAT".equalsIgnoreCase(nextTok.value))) {
                                isCompoundEnd = true;
                            }
                        }
                        if (!isCompoundEnd) {
                            beginDepth--;
                            if (beginDepth == 0) {
                                consume(); // Consume 'END'
                                break;
                            }
                        }
                    }
                }
                bodyTokens.add(consume());
            }

            if (beginDepth > 0) {
                throw new SqlSyntaxException("Expected 'END' to close function body", peek().position);
            }

            Command.CreateFunction createFunctionCmd = new Command.CreateFunction(functionName, paramNames, paramTypes, returnType, bodyTokens);
            createFunctionCmd.definition = extractDefinition(startPos);
            return createFunctionCmd;
        } else if (matchKeyword("TABLE")) {
            boolean ifNotExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("NOT", "Expected 'NOT' after 'CREATE TABLE IF'");
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'CREATE TABLE IF NOT'");
                ifNotExists = true;
            }
            
            String tableName = parseTableName("Expected table name after 'CREATE TABLE'");

            expectSymbol("(", "Expected '(' after table name");

            List<String> columnNames = new ArrayList<>();
            List<String> columnTypes = new ArrayList<>();
            Map<String, String> columnDefaults = new HashMap<>();
            Map<String, String> columnOnUpdates = new HashMap<>();
            Map<String, Boolean> columnNullables = new HashMap<>();
            Map<String, String> columnKeys = new HashMap<>();
            Map<String, String> columnExtras = new HashMap<>();

            List<Map<String, Object>> checks = new ArrayList<>();
            Map<String, String> foreignKeys = new HashMap<>();
            List<String> primaryKey = new ArrayList<>();
            List<List<String>> uniques = new ArrayList<>();
            Map<List<String>, String> uniqueNames = new HashMap<>();
            Map<String, SqlAttributes> columnAttributes = new HashMap<>();

            do {
                SqlToken nameToken = peek();
                boolean parsedConstraint = false;
                
                // Parse table-level constraints
                if (nameToken.type == SqlToken.Type.KEYWORD) {
                    String val = nameToken.value.toUpperCase();
                    if ("CONSTRAINT".equals(val)) {
                        consume(); // CONSTRAINT
                        SqlToken constraintNameToken = peek();
                        if (constraintNameToken.type == SqlToken.Type.IDENTIFIER || constraintNameToken.type == SqlToken.Type.KEYWORD) {
                            consume();
                        } else {
                            throw new SqlSyntaxException("Expected constraint name after 'CONSTRAINT'", constraintNameToken.position);
                        }
                        nameToken = peek();
                        if (nameToken.type == SqlToken.Type.KEYWORD) {
                            val = nameToken.value.toUpperCase();
                        } else {
                            throw new SqlSyntaxException("Expected constraint type (PRIMARY, UNIQUE, FOREIGN) after constraint name", nameToken.position);
                        }
                    }

                    if ("FOREIGN".equals(val)) {
                        consume(); // FOREIGN
                        expectKeyword("KEY", "Expected 'KEY' after 'FOREIGN'");
                        expectSymbol("(", "Expected '(' after 'FOREIGN KEY'");
                        List<String> fkCols = new ArrayList<>();
                        do {
                            expect(SqlToken.Type.IDENTIFIER, "Expected column name in FOREIGN KEY");
                            fkCols.add(tokens.get(pos - 1).value);
                        } while (matchSymbol(","));
                        expectSymbol(")", "Expected ')' after FOREIGN KEY columns");
                        expectKeyword("REFERENCES", "Expected 'REFERENCES' after 'FOREIGN KEY(...)'");
                        String refTable = parseTableName("Expected parent table name");
                        expectSymbol("(", "Expected '(' after parent table name");
                        List<String> refCols = new ArrayList<>();
                        do {
                            expect(SqlToken.Type.IDENTIFIER, "Expected parent column name");
                            refCols.add(tokens.get(pos - 1).value);
                        } while (matchSymbol(","));
                        expectSymbol(")", "Expected ')' after parent column name");
                        
                        parseOptionalForeignKeyActions();
                        
                        for (int i = 0; i < fkCols.size(); i++) {
                            String fkCol = fkCols.get(i);
                            String refCol = i < refCols.size() ? refCols.get(i) : refCols.get(0);
                            foreignKeys.put(fkCol, refTable + "." + refCol);
                        }
                        parsedConstraint = true;
                    } else if ("CHECK".equals(val)) {
                        checks.add(parseInlineCheckConstraint());
                        parsedConstraint = true;
                    } else if ("PRIMARY".equals(val)) {
                        consume(); // PRIMARY
                        expectKeyword("KEY", "Expected 'KEY' after 'PRIMARY'");
                        expectSymbol("(", "Expected '(' after 'PRIMARY KEY'");
                        List<String> pkCols = new ArrayList<>();
                        do {
                            expect(SqlToken.Type.IDENTIFIER, "Expected column name in PRIMARY KEY");
                            pkCols.add(tokens.get(pos - 1).value);
                        } while (matchSymbol(","));
                        expectSymbol(")", "Expected ')' after PRIMARY KEY columns");
                        
                        primaryKey.addAll(pkCols);
                        parsedConstraint = true;
                    } else if ("UNIQUE".equals(val)) {
                        consume(); // UNIQUE
                        if (peek().type == SqlToken.Type.KEYWORD && (peek().value.equalsIgnoreCase("KEY") || peek().value.equalsIgnoreCase("INDEX"))) {
                            consume(); // KEY/INDEX
                        }
                        // Optional index name
                        String constraintName = null;
                        if (peek().type != SqlToken.Type.SYMBOL || !peek().value.equals("(")) {
                            if (isIdentifier(peek())) {
                                constraintName = peek().value;
                                consume();
                            }
                        }
                        expectSymbol("(", "Expected '(' after 'UNIQUE'");
                        List<String> uniCols = new ArrayList<>();
                        do {
                            expect(SqlToken.Type.IDENTIFIER, "Expected column name in UNIQUE constraint");
                            uniCols.add(tokens.get(pos - 1).value);
                        } while (matchSymbol(","));
                        expectSymbol(")", "Expected ')' after UNIQUE columns");
                        
                        uniques.add(uniCols);
                        if (constraintName != null) {
                            uniqueNames.put(uniCols, constraintName);
                        }
                        parsedConstraint = true;
                    } else if ("INDEX".equals(val) || "KEY".equals(val) || "FULLTEXT".equals(val) || "SPATIAL".equals(val)) {
                        // Skip it
                        while (!peek().value.equals(",") && !peek().value.equals(")") &&
                               peek().type != SqlToken.Type.EOF) {
                            consume();
                        }
                        parsedConstraint = true;
                    }
                }

                if (!parsedConstraint) {
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name");
                    String colName = tokens.get(pos - 1).value;

                    Command.ColumnDef cd = parseColumnDef(colName);
                    checks.addAll(cd.checks);

                    columnNames.add(cd.name);
                    columnTypes.add(cd.type);
                    columnDefaults.put(cd.name, cd.defaultValue);
                    columnOnUpdates.put(cd.name, cd.onUpdateValue);
                    columnNullables.put(cd.name, cd.nullable);

                    if (cd.isPrimaryKey) {
                        columnKeys.put(cd.name, "PRI");
                        primaryKey.add(cd.name);
                    } else if (cd.isUnique) {
                        columnKeys.put(cd.name, "UNI");
                        List<String> uniGroup = new ArrayList<>();
                        uniGroup.add(cd.name);
                        uniques.add(uniGroup);
                    } else {
                        columnKeys.put(cd.name, "");
                    }

                    if (cd.isAutoIncrement) {
                        columnExtras.put(cd.name, "auto_increment");
                    } else if (cd.onUpdateValue != null && SqlDefaults.isCurrentTimestampFunction(cd.onUpdateValue)) {
                        if (SqlDefaults.isCurrentTimestampFunction(cd.defaultValue)) {
                            columnExtras.put(cd.name, "DEFAULT_GENERATED on update " + cd.onUpdateValue.toUpperCase());
                        } else {
                            columnExtras.put(cd.name, "on update " + cd.onUpdateValue.toUpperCase());
                        }
                    } else {
                        columnExtras.put(cd.name, "");
                    }

                    if (cd.attributes != null) {
                        columnAttributes.put(cd.name, cd.attributes);
                        if (cd.attributes.referencesTable != null) {
                            foreignKeys.put(cd.name, cd.attributes.referencesTable + "." + cd.attributes.referencesColumn);
                        }
                    }
                }
            } while (matchSymbol(","));

            expectSymbol(")", "Expected ')' to close column definitions");

            // Post-process table-level constraints: enforce PRI and NOT NULL for primary key columns
            for (String pkCol : primaryKey) {
                columnKeys.put(pkCol, "PRI");
                columnNullables.put(pkCol, false);
            }
            // Enforce UNI for unique key groups
            for (List<String> uniGroup : uniques) {
                for (String uniCol : uniGroup) {
                    if (!"PRI".equals(columnKeys.get(uniCol))) {
                        columnKeys.put(uniCol, "UNI");
                    }
                }
            }

            // Parse optional table options after the closing parenthesis
            boolean parsingTableOptions = true;
            String tableCharset = null;
            String tableCollation = null;
            while (parsingTableOptions) {
                if (matchName("DEFAULT")) {
                    // skip
                }
                
                if (matchName("ENGINE")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected engine name", valToken.position);
                    }
                } else if (matchName("CHARACTER")) {
                    matchName("SET"); // optional SET
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        tableCharset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected character set name", valToken.position);
                    }
                } else if (matchName("CHARSET")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        tableCharset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected charset name", valToken.position);
                    }
                } else if (matchName("COLLATE")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        tableCollation = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected collation name", valToken.position);
                    }
                } else if (matchName("AUTO_INCREMENT")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.NUMBER) {
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected auto_increment value", valToken.position);
                    }
                } else if (matchName("COMMENT")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.STRING) {
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected comment string", valToken.position);
                    }
                } else {
                    parsingTableOptions = false;
                }
            }
            skipPartitioning();

            Command.CreateTable createTableCmd = new Command.CreateTable(tableName, columnNames, columnTypes,
                                           columnDefaults, columnOnUpdates, columnNullables,
                                           columnKeys, columnExtras, checks, foreignKeys,
                                           primaryKey, uniques, ifNotExists);
            createTableCmd.uniqueNames.putAll(uniqueNames);
            createTableCmd.charset = tableCharset;
            createTableCmd.collation = tableCollation;
            createTableCmd.columnAttributes.putAll(columnAttributes);
            createTableCmd.definition = extractDefinition(startPos);
            return createTableCmd;
        } else if (matchKeyword("USER")) {
            return parseCreateUser();
        } else if (peek().type == SqlToken.Type.KEYWORD && (peek().value.equalsIgnoreCase("INDEX") || (peek().value.equalsIgnoreCase("UNIQUE") && pos + 1 < tokens.size() && tokens.get(pos + 1).value.equalsIgnoreCase("INDEX")))) {
            return parseCreateIndex();
        } else if (matchName("VIEW") || (matchKeyword("OR") && matchName("REPLACE") && matchName("VIEW"))) {
            String viewName = parseTableName("Expected view name after VIEW");
            expectKeyword("AS", "Expected 'AS' after view name");
            
            // Capture all tokens after 'AS' until ';' or EOF
            StringBuilder selectBuilder = new StringBuilder();
            while (peek().type != SqlToken.Type.EOF) {
                SqlToken tok = peek();
                if (tok.type == SqlToken.Type.EOF || (tok.type == SqlToken.Type.SYMBOL && ";".equals(tok.value))) {
                    break;
                }
                if (tok.type == SqlToken.Type.STRING) {
                    selectBuilder.append("'").append(tok.value.replace("'", "\\'")).append("' ");
                } else {
                    selectBuilder.append(tok.value).append(" ");
                }
                consume();
            }
            String selectQuery = selectBuilder.toString().trim();
            Command.CreateView createViewCmd = new Command.CreateView(viewName, selectQuery);
            createViewCmd.definition = extractDefinition(startPos);
            return createViewCmd;
        } else if (matchName("PROCEDURE")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected procedure name after 'CREATE PROCEDURE'");
            String procName = tokens.get(pos - 1).value;
            String procDef = parseRestOfDdl("CREATE PROCEDURE " + procName + " ");
            return new Command.CreateProcedure(procName, procDef);
        } else if (matchName("TRIGGER")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected trigger name after 'CREATE TRIGGER'");
            String triggerName = tokens.get(pos - 1).value;
            String triggerDef = parseRestOfDdl("CREATE TRIGGER " + triggerName + " ");
            return new Command.CreateTrigger(triggerName, triggerDef);
        } else if (matchName("EVENT")) {
            boolean ifNotExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("NOT", "Expected NOT");
                expectKeyword("EXISTS", "Expected EXISTS");
                ifNotExists = true;
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected event name after 'CREATE EVENT'");
            String eventName = tokens.get(pos - 1).value;
            String eventDef = parseRestOfDdl("CREATE EVENT " + eventName + " ");
            return new Command.CreateEvent(eventName, eventDef);
        } else {
            throw new SqlSyntaxException("Expected 'DATABASE', 'TABLE', 'USER', 'VIEW', 'PROCEDURE', 'TRIGGER', 'EVENT', or 'INDEX' after 'CREATE'", peek().position);
        }
    }

    private Command parseCreateIndex() throws SqlSyntaxException {
        boolean unique = false;
        if (matchKeyword("UNIQUE")) {
            unique = true;
        }
        expectKeyword("INDEX", "Expected 'INDEX' keyword");
        
        expect(SqlToken.Type.IDENTIFIER, "Expected index name");
        String indexName = tokens.get(pos - 1).value;
        
        expectKeyword("ON", "Expected 'ON' after index name");
        String tableName = parseTableName("Expected table name");
        
        expectSymbol("(", "Expected '(' to open index column list");
        List<String> cols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name");
            cols.add(tokens.get(pos - 1).value);
            if (matchKeyword("ASC") || matchKeyword("DESC")) {
                // skip
            }
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' to close index column list");
        
        return new Command.CreateIndex(tableName, indexName, cols, unique);
    }

    private Command parseDrop() throws SqlSyntaxException {
        consume(); // DROP
        if (matchKeyword("DATABASE")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP DATABASE IF'");
                ifExists = true;
            }
            SqlToken dbToken = peek();
            if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected database name", dbToken.position);
            }
            consume();
            return new Command.DropDatabase(dbToken.value, ifExists);
        } else if (matchKeyword("TABLE")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP TABLE IF'");
                ifExists = true;
            }
            String tableName = parseTableName("Expected table name");
            return new Command.DropTable(tableName, ifExists);
        } else if (matchKeyword("FUNCTION")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP FUNCTION IF'");
                ifExists = true;
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected function name");
            String functionName = tokens.get(pos - 1).value;
            return new Command.DropFunction(functionName, ifExists);
        } else if (matchKeyword("VIEW") || matchName("VIEW")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP VIEW IF'");
                ifExists = true;
            }
            String viewName = parseTableName("Expected view name");
            return new Command.DropView(viewName, ifExists);
        } else if (matchKeyword("PROCEDURE") || matchName("PROCEDURE")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP PROCEDURE IF'");
                ifExists = true;
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected procedure name");
            String procName = tokens.get(pos - 1).value;
            return new Command.DropProcedure(procName, ifExists);
        } else if (matchKeyword("TRIGGER") || matchName("TRIGGER")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP TRIGGER IF'");
                ifExists = true;
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected trigger name");
            String triggerName = tokens.get(pos - 1).value;
            return new Command.DropTrigger(triggerName, ifExists);
        } else if (matchKeyword("EVENT") || matchName("EVENT")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP EVENT IF'");
                ifExists = true;
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected event name");
            String eventName = tokens.get(pos - 1).value;
            return new Command.DropEvent(eventName, ifExists);
        } else if (matchKeyword("USER")) {
            boolean ifExists = false;
            if (matchKeyword("IF")) {
                expectKeyword("EXISTS", "Expected 'EXISTS' after 'DROP USER IF'");
                ifExists = true;
            }
            String[] userSpec = parseUserSpec();
            String username = userSpec[0];
            String host = userSpec[1];
            return new Command.DropUser(username, host, ifExists);
        } else {
            throw new SqlSyntaxException("Expected 'DATABASE', 'TABLE', 'USER', 'FUNCTION', 'VIEW', 'PROCEDURE', 'TRIGGER', or 'EVENT' after 'DROP'", peek().position);
        }
    }

    private Command parseUse() throws SqlSyntaxException {
        consume(); // USE
        // Optional "DATABASE"
        matchKeyword("DATABASE");
        SqlToken dbToken = peek();
        if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
            throw new SqlSyntaxException("Expected database name after 'USE'", dbToken.position);
        }
        consume();
        return new Command.UseDatabase(dbToken.value);
    }

    private Command parseHelp() throws SqlSyntaxException {
        consume(); // HELP
        SqlToken t = peek();
        String topic = null;
        if (t.type == SqlToken.Type.IDENTIFIER || t.type == SqlToken.Type.KEYWORD || t.type == SqlToken.Type.STRING) {
            topic = t.value;
            consume();
        }
        return new Command.Help(topic);
    }

    private Command parseExport() throws SqlSyntaxException {
        consume(); // EXPORT
        matchKeyword("DATABASE"); // optional DATABASE
        SqlToken dbToken = peek();
        if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
            throw new SqlSyntaxException("Expected database name after 'EXPORT'", dbToken.position);
        }
        consume();
        expectKeyword("TO", "Expected 'TO' after database name");
        SqlToken pathToken = peek();
        if (pathToken.type != SqlToken.Type.STRING) {
            throw new SqlSyntaxException("Expected file path as string literal after 'TO'", pathToken.position);
        }
        consume();
        return new Command.ExportDatabase(dbToken.value, pathToken.value);
    }

    private Command parseImport() throws SqlSyntaxException {
        consume(); // IMPORT
        matchKeyword("DATABASE"); // optional DATABASE
        SqlToken dbToken = peek();
        if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
            throw new SqlSyntaxException("Expected database name after 'IMPORT'", dbToken.position);
        }
        consume();
        expectKeyword("FROM", "Expected 'FROM' after database name");
        SqlToken pathToken = peek();
        if (pathToken.type != SqlToken.Type.STRING) {
            throw new SqlSyntaxException("Expected file path as string literal after 'FROM'", pathToken.position);
        }
        consume();
        return new Command.ImportDatabase(dbToken.value, pathToken.value);
    }

    private Command parseCall() throws SqlSyntaxException {
        consume(); // CALL
        expect(SqlToken.Type.IDENTIFIER, "Expected procedure name after 'CALL'");
        String procName = tokens.get(pos - 1).value;

        List<Object> args = new ArrayList<>();
        if (matchSymbol("(")) {
            if (!peek().value.equals(")")) {
                do {
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.STRING) {
                        consume();
                        args.add(valToken.value);
                    } else if (valToken.type == SqlToken.Type.NUMBER) {
                        consume();
                        if (valToken.value.contains(".")) {
                            args.add(Double.parseDouble(valToken.value));
                        } else {
                            args.add(Long.parseLong(valToken.value));
                        }
                    } else if (valToken.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(valToken.value)) {
                        consume();
                        args.add(null);
                    } else if (matchSymbol("@")) {
                        SqlToken varNameTok = consume();
                        if (varNameTok.type != SqlToken.Type.IDENTIFIER && varNameTok.type != SqlToken.Type.KEYWORD) {
                            throw new SqlSyntaxException("Expected variable name after '@' in CALL argument list", varNameTok.position);
                        }
                        String varName = "@" + varNameTok.value;
                        args.add(varName);
                    } else {
                        throw new SqlSyntaxException("Expected literal value or user variable in CALL argument list", valToken.position);
                    }
                } while (matchSymbol(","));
            }
            expectSymbol(")", "Expected ')' to close CALL argument list");
        }
        return new Command.CallProcedure(procName, args);
    }

    private Command parseShow() throws SqlSyntaxException {
        consume(); // SHOW
        boolean full = false;
        if (matchName("FULL")) {
            full = true;
        }
        
        if (matchKeyword("DATABASES")) {
            return new Command.ShowDatabases();
        } else if (matchKeyword("TABLES")) {
            String databaseName = null;
            if (matchKeyword("FROM") || matchKeyword("IN")) {
                SqlToken dbTok = peek();
                if (dbTok.type == SqlToken.Type.IDENTIFIER || dbTok.type == SqlToken.Type.KEYWORD) {
                    databaseName = dbTok.value;
                    consume();
                } else {
                    throw new SqlSyntaxException("Expected database name after SHOW TABLES FROM/IN", dbTok.position);
                }
            }
            Clause.Where where = parseOptionalWhere();
            return new Command.ShowTables(full, databaseName, where);
        } else if (matchName("COLUMNS") || matchName("FIELDS")) {
            if (!matchKeyword("FROM") && !matchKeyword("IN")) {
                throw new SqlSyntaxException("Expected 'FROM' or 'IN' after 'SHOW COLUMNS'", peek().position);
            }
            String tableName = parseTableName("Expected table name after 'SHOW COLUMNS'");
            if (matchKeyword("FROM") || matchKeyword("IN")) {
                expect(SqlToken.Type.IDENTIFIER, "Expected database name");
            }
            return new Command.DescribeTable(tableName);
        } else if (matchName("INDEX") || matchName("INDEXES") || matchName("KEYS")) {
            if (!matchKeyword("FROM") && !matchKeyword("IN")) {
                throw new SqlSyntaxException("Expected 'FROM' or 'IN' after 'SHOW INDEX/INDEXES/KEYS'", peek().position);
            }
            String tableName = parseTableName("Expected table name");
            if (matchKeyword("FROM") || matchKeyword("IN")) {
                expect(SqlToken.Type.IDENTIFIER, "Expected database name");
            }
            return new Command.ShowIndexes(tableName);
        } else if (matchKeyword("PROCEDURE") || matchName("PROCEDURE")) {
            expectKeyword("STATUS", "Expected 'STATUS' after 'SHOW PROCEDURE'");
            Clause.Where where = parseOptionalWhere();
            return new Command.ShowProcedureStatus(where);
        } else if (matchKeyword("FUNCTION") || matchName("FUNCTION")) {
            expectKeyword("STATUS", "Expected 'STATUS' after 'SHOW FUNCTION'");
            Clause.Where where = parseOptionalWhere();
            return new Command.ShowFunctionStatus(where);
        } else if (matchName("CHARACTER") || matchName("CHARACTERS")) {
            expectKeyword("SET", "Expected 'SET' after 'SHOW CHARACTER(S)'");
            return new Command.ShowCharacterSets();
        } else if (matchName("COLLATION")) {
            return new Command.ShowCollations();
        } else if (matchName("CREATE")) {
            if (matchKeyword("DATABASE")) {
                SqlToken dbToken = peek();
                if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
                    throw new SqlSyntaxException("Expected database name after 'SHOW CREATE DATABASE'", dbToken.position);
                }
                consume();
                return new Command.ShowCreateDatabase(dbToken.value);
            } else if (matchKeyword("TABLE")) {
                String tableName = parseTableName("Expected table name after 'SHOW CREATE TABLE'");
                return new Command.ShowCreateTable(tableName);
            } else if (matchKeyword("PROCEDURE") || matchName("PROCEDURE")) {
                expect(SqlToken.Type.IDENTIFIER, "Expected procedure name after 'SHOW CREATE PROCEDURE'");
                return new Command.ShowCreateProcedure(tokens.get(pos - 1).value);
            } else if (matchKeyword("VIEW") || matchName("VIEW")) {
                String viewName = parseTableName("Expected view name after 'SHOW CREATE VIEW'");
                return new Command.ShowCreateView(viewName);
            } else if (matchKeyword("FUNCTION") || matchName("FUNCTION")) {
                String functionName = parseTableName("Expected function name after 'SHOW CREATE FUNCTION'");
                return new Command.ShowCreateFunction(functionName);
            } else {
                throw new SqlSyntaxException("Expected 'DATABASE', 'TABLE', 'VIEW', 'PROCEDURE', or 'FUNCTION' after 'SHOW CREATE'", peek().position);
            }
        } else {
            throw new SqlSyntaxException("Expected 'DATABASES', 'TABLES', 'COLUMNS', 'PROCEDURE STATUS', 'CHARACTER SET', 'COLLATION', 'CREATE DATABASE', 'CREATE TABLE' or 'FUNCTION STATUS' after 'SHOW'", peek().position);
        }
    }

    private Command parseDescribe() throws SqlSyntaxException {
        consume(); // DESCRIBE / DESC
        String tableName = parseTableName("Expected table name");
        return new Command.DescribeTable(tableName);
    }

    private Command parseInsert() throws SqlSyntaxException {
        consume(); // INSERT
        expectKeyword("INTO", "Expected 'INTO' after 'INSERT'");
        String tableName = parseTableName("Expected table name");

        List<String> columnNames = null;
        if (matchSymbol("(")) {
            columnNames = new ArrayList<>();
            do {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name in insert columns list");
                columnNames.add(tokens.get(pos - 1).value);
            } while (matchSymbol(","));
            expectSymbol(")", "Expected ')' to close insert columns list");
        }

        expectKeyword("VALUES", "Expected 'VALUES' in insert command");

        List<List<Object>> valuesList = new ArrayList<>();
        do {
            expectSymbol("(", "Expected '(' to open insert row values list");
            List<Object> values = new ArrayList<>();
            if (!peek().value.equals(")")) {
                do {
                    values.add(parseInsertValue());
                } while (matchSymbol(","));
            }
            expectSymbol(")", "Expected ')' to close insert row values list");
            valuesList.add(values);
        } while (matchSymbol(","));

        Map<String, String> updateAssignments = null;
        if (matchKeyword("ON")) {
            expectKeyword("DUPLICATE", "Expected 'DUPLICATE' after 'ON'");
            expectKeyword("KEY", "Expected 'KEY' after 'ON DUPLICATE'");
            expectKeyword("UPDATE", "Expected 'UPDATE' after 'ON DUPLICATE KEY'");
            updateAssignments = new java.util.LinkedHashMap<>();
            do {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name in UPDATE assignment");
                String col = tokens.get(pos - 1).value;
                expectSymbol("=", "Expected '=' in UPDATE assignment");
                
                StringBuilder exprBuilder = new StringBuilder();
                while (peek().type != SqlToken.Type.EOF && !peek().value.equals(",") && !peek().value.equals(";")) {
                    exprBuilder.append(consume().value).append(" ");
                }
                updateAssignments.put(col, exprBuilder.toString().trim());
            } while (matchSymbol(","));
        }

        Command.Insert cmd = new Command.Insert(tableName, columnNames, valuesList);
        cmd.updateAssignments = updateAssignments;
        return cmd;
    }

    private Object parseInsertValue() throws SqlSyntaxException {
        SqlToken valToken = peek();
        boolean negative = false;
        if (valToken.type == SqlToken.Type.SYMBOL && (valToken.value.equals("-") || valToken.value.equals("+"))) {
            if (valToken.value.equals("-")) {
                negative = true;
            }
            if (pos + 1 < tokens.size() && tokens.get(pos + 1).type == SqlToken.Type.NUMBER) {
                consume(); // Consume '-' or '+'
                valToken = peek();
                consume(); // Consume number
                if (valToken.value.contains(".")) {
                    double num = Double.parseDouble(valToken.value);
                    return negative ? -num : num;
                } else {
                    long num = Long.parseLong(valToken.value);
                    return negative ? -num : num;
                }
            }
        }

        if (valToken.type == SqlToken.Type.STRING) {
            consume();
            return valToken.value;
        }
        if (valToken.type == SqlToken.Type.NUMBER) {
            consume();
            if (valToken.value.contains(".")) {
                return Double.parseDouble(valToken.value);
            } else {
                return Long.parseLong(valToken.value);
            }
        }
        if (valToken.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(valToken.value)) {
            consume();
            return null;
        }
        if (valToken.type == SqlToken.Type.KEYWORD && "DEFAULT".equalsIgnoreCase(valToken.value)) {
            consume();
            return "\u0000DEFAULT\u0000";
        }

        // Parse expression
        StringBuilder exprBuilder = new StringBuilder();
        int parenDepth = 0;
        while (peek().type != SqlToken.Type.EOF) {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.EOF) {
                break;
            }
            if (parenDepth == 0 && t.type == SqlToken.Type.SYMBOL && (t.value.equals(",") || t.value.equals(")"))) {
                break;
            }
            
            consume();
            if (t.type == SqlToken.Type.SYMBOL && t.value.equals("(")) {
                parenDepth++;
            } else if (t.type == SqlToken.Type.SYMBOL && t.value.equals(")")) {
                parenDepth--;
            }
            
            if (t.type == SqlToken.Type.STRING) {
                exprBuilder.append(" '").append(t.value.replace("'", "\\'")).append("'");
            } else {
                exprBuilder.append(" ").append(t.value);
            }
        }
        
        String expr = exprBuilder.toString().trim();
        if (expr.isEmpty()) {
            throw new SqlSyntaxException("Expected value in insert list", valToken.position);
        }
        return "\u0000EXPR\u0000" + expr;
    }

    private Command parseSelect() throws SqlSyntaxException {
        consume(); // SELECT
        
        boolean distinct = false;
        if (matchKeyword("DISTINCT")) {
            distinct = true;
        }

        List<String> projection = new ArrayList<>();
        Map<String, String> aliases = new HashMap<>();

        if (matchSymbol("*")) {
            projection = null; // null represents *
        } else {
            do {
                String selectItem;
                SqlToken t = peek();
                if (t.type == SqlToken.Type.SYMBOL && "*".equals(t.value)) {
                    consume();
                    selectItem = "*";
                } else {
                    selectItem = parseSelectExpression();
                }

                // Check for alias
                if (matchKeyword("AS")) {
                    SqlToken aliasToken = peek();
                    if (aliasToken.type != SqlToken.Type.IDENTIFIER && aliasToken.type != SqlToken.Type.KEYWORD) {
                        throw new SqlSyntaxException("Expected alias name after AS", aliasToken.position);
                    }
                    consume();
                    aliases.put(selectItem, aliasToken.value);
                } else {
                    SqlToken aliasToken = peek();
                    if (aliasToken.type == SqlToken.Type.IDENTIFIER && 
                        !"FROM".equalsIgnoreCase(aliasToken.value) && 
                        !"JOIN".equalsIgnoreCase(aliasToken.value) && 
                        !"INNER".equalsIgnoreCase(aliasToken.value) && 
                        !"LEFT".equalsIgnoreCase(aliasToken.value) && 
                        !"RIGHT".equalsIgnoreCase(aliasToken.value) && 
                        !"CROSS".equalsIgnoreCase(aliasToken.value) && 
                        !"UNION".equalsIgnoreCase(aliasToken.value) && 
                        !"ORDER".equalsIgnoreCase(aliasToken.value) && 
                        !"GROUP".equalsIgnoreCase(aliasToken.value) && 
                        !"LIMIT".equalsIgnoreCase(aliasToken.value) && 
                        !"WHERE".equalsIgnoreCase(aliasToken.value)) {
                        consume();
                        aliases.put(selectItem, aliasToken.value);
                    }
                }
                projection.add(selectItem);
            } while (matchSymbol(","));
        }

        String tableName = null;
        List<Clause.Join> joins = new ArrayList<>();
        Map<String, String> tableAliases = new HashMap<>();
        if (matchKeyword("FROM")) {
            tableName = parseTableName("Expected table name");
            String tableAlias = parseTableAlias();
            if (tableAlias != null) {
                tableAliases.put(tableAlias, tableName);
            }

            // Parse Joins
            while (true) {
                String joinType = "";
                if (matchKeyword("INNER")) {
                    expectKeyword("JOIN", "Expected 'JOIN' after 'INNER'");
                    joinType = "INNER";
                } else if (matchKeyword("LEFT")) {
                    expectKeyword("JOIN", "Expected 'JOIN' after 'LEFT'");
                    joinType = "LEFT";
                } else if (matchKeyword("RIGHT")) {
                    expectKeyword("JOIN", "Expected 'JOIN' after 'RIGHT'");
                    joinType = "RIGHT";
                } else if (matchKeyword("CROSS")) {
                    expectKeyword("JOIN", "Expected 'JOIN' after 'CROSS'");
                    joinType = "CROSS";
                } else if (matchKeyword("JOIN")) {
                    joinType = "INNER";
                } else {
                    break;
                }

                String joinTable = parseTableName("Expected table name to join");
                String joinAlias = parseTableAlias();
                if (joinAlias != null) {
                    tableAliases.put(joinAlias, joinTable);
                }

                String leftCol = null;
                String rightCol = null;
                List<Clause.Where> extraConditions = new java.util.ArrayList<>();
                if (!"CROSS".equals(joinType)) {
                    expectKeyword("ON", "Expected 'ON' after JOIN table name");
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name in join ON condition");
                    leftCol = tokens.get(pos - 1).value;
                    if (matchSymbol(".")) {
                        expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                        leftCol = leftCol + "." + tokens.get(pos - 1).value;
                    }
                    expectSymbol("=", "Expected '=' in join ON condition");
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name in join ON condition");
                    rightCol = tokens.get(pos - 1).value;
                    if (matchSymbol(".")) {
                        expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                        rightCol = rightCol + "." + tokens.get(pos - 1).value;
                    }

                    // Parse optional extra conditions (AND col = val or AND col OP val)
                    while (matchKeyword("AND")) {
                        expect(SqlToken.Type.IDENTIFIER, "Expected column name in join ON extra condition");
                        String extraCol = tokens.get(pos - 1).value;
                        if (matchSymbol(".")) {
                            expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                            extraCol = extraCol + "." + tokens.get(pos - 1).value;
                        }

                        SqlToken extraOpToken = peek();
                        if (extraOpToken.type != SqlToken.Type.SYMBOL && 
                            !(extraOpToken.type == SqlToken.Type.KEYWORD && 
                             ("LIKE".equalsIgnoreCase(extraOpToken.value) || 
                              "IN".equalsIgnoreCase(extraOpToken.value) || 
                              "BETWEEN".equalsIgnoreCase(extraOpToken.value)))) {
                            throw new SqlSyntaxException("Expected operator in join ON extra condition", extraOpToken.position);
                        }
                        consume();
                        String extraOp = extraOpToken.value.toUpperCase();

                        SqlToken extraValToken = peek();
                        Object extraVal;
                        boolean isValCol = false;
                        if (extraValToken.type == SqlToken.Type.STRING) {
                            consume();
                            extraVal = extraValToken.value;
                        } else if (extraValToken.type == SqlToken.Type.NUMBER) {
                            consume();
                            if (extraValToken.value.contains(".")) {
                                extraVal = Double.parseDouble(extraValToken.value);
                            } else {
                                extraVal = Long.parseLong(extraValToken.value);
                            }
                        } else if (extraValToken.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(extraValToken.value)) {
                            consume();
                            extraVal = null;
                        } else if (extraValToken.type == SqlToken.Type.IDENTIFIER || extraValToken.type == SqlToken.Type.KEYWORD) {
                            consume();
                            String valCol = extraValToken.value;
                            if (matchSymbol(".")) {
                                expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                                valCol = valCol + "." + tokens.get(pos - 1).value;
                            }
                            extraVal = valCol;
                            isValCol = true;
                        } else {
                            throw new SqlSyntaxException("Expected filter value in join ON extra condition", extraValToken.position);
                        }
                        extraConditions.add(new Clause.Where(extraCol, extraOp, extraVal, isValCol));
                    }
                }
                joins.add(new Clause.Join(joinTable, joinType, leftCol, rightCol, joinAlias, extraConditions));
            }
        }

        Clause.Where where = parseOptionalWhere();

        Clause.GroupBy groupBy = null;
        if (matchKeyword("GROUP")) {
            expectKeyword("BY", "Expected 'BY' after 'GROUP'");
            expect(SqlToken.Type.IDENTIFIER, "Expected column name for GROUP BY");
            groupBy = new Clause.GroupBy(tokens.get(pos - 1).value);
        }

        Clause.Having having = null;
        if (matchKeyword("HAVING")) {
            SqlToken funcToken = peek();
            if (funcToken.type != SqlToken.Type.IDENTIFIER && funcToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected aggregate function in HAVING", funcToken.position);
            }
            consume();
            StringBuilder func = new StringBuilder(funcToken.value);
            expectSymbol("(", "Expected '(' after function name");
            SqlToken inner = peek();
            if (inner.type == SqlToken.Type.SYMBOL && "*".equals(inner.value)) {
                consume();
                func.append("(*)");
            } else {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name inside HAVING function");
                func.append("(").append(tokens.get(pos - 1).value).append(")");
            }
            expectSymbol(")", "Expected ')' inside HAVING function");

            SqlToken opToken = peek();
            if (opToken.type != SqlToken.Type.SYMBOL) {
                throw new SqlSyntaxException("Expected comparison operator in HAVING clause", opToken.position);
            }
            consume();
            String op = opToken.value;

            SqlToken valToken = peek();
            Object value;
            if (valToken.type == SqlToken.Type.STRING) {
                consume();
                value = valToken.value;
            } else if (valToken.type == SqlToken.Type.NUMBER) {
                consume();
                if (valToken.value.contains(".")) {
                    value = Double.parseDouble(valToken.value);
                } else {
                    value = Long.parseLong(valToken.value);
                }
            } else {
                throw new SqlSyntaxException("Expected numeric or string value in HAVING comparison", valToken.position);
            }

            having = new Clause.Having(func.toString(), op, value);
        }

        List<Clause.OrderBy> orderBySpecs = new ArrayList<>();
        if (matchKeyword("ORDER")) {
            expectKeyword("BY", "Expected 'BY' after 'ORDER'");
            do {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name for order sorting");
                String col = tokens.get(pos - 1).value;
                if (matchSymbol(".")) {
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                    col = col + "." + tokens.get(pos - 1).value;
                }
                boolean asc = true;
                if (matchKeyword("DESC")) {
                    asc = false;
                } else {
                    matchKeyword("ASC"); // Optional
                }
                orderBySpecs.add(new Clause.OrderBy(col, asc));
            } while (matchSymbol(","));
        }

        Integer limit = null;
        if (matchKeyword("LIMIT")) {
            expect(SqlToken.Type.NUMBER, "Expected numeric limit");
            limit = Integer.parseInt(tokens.get(pos - 1).value);
        }

        Clause.Union union = null;
        if (matchKeyword("UNION")) {
            boolean unionAll = false;
            if (matchKeyword("ALL")) {
                unionAll = true;
            }
            Command.Select secondSelect = (Command.Select) parseSelect();
            union = new Clause.Union(secondSelect, unionAll);
        }

        return new Command.Select(projection, tableName, where, orderBySpecs, limit,
                                  distinct, aliases, joins, groupBy, having, union, tableAliases);
    }

    private String parseTableAlias() throws SqlSyntaxException {
        if (matchKeyword("AS")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected alias name after AS");
            return tokens.get(pos - 1).value;
        }
        SqlToken next = peek();
        if (next.type == SqlToken.Type.IDENTIFIER &&
            !"JOIN".equalsIgnoreCase(next.value) &&
            !"INNER".equalsIgnoreCase(next.value) &&
            !"LEFT".equalsIgnoreCase(next.value) &&
            !"RIGHT".equalsIgnoreCase(next.value) &&
            !"CROSS".equalsIgnoreCase(next.value) &&
            !"UNION".equalsIgnoreCase(next.value) &&
            !"ORDER".equalsIgnoreCase(next.value) &&
            !"GROUP".equalsIgnoreCase(next.value) &&
            !"LIMIT".equalsIgnoreCase(next.value) &&
            !"WHERE".equalsIgnoreCase(next.value) &&
            !"ON".equalsIgnoreCase(next.value) &&
            !";".equals(next.value)) {
            consume();
            return next.value;
        }
        return null;
    }

    // Parses a SELECT projection expression into a string.
    // Consumes tokens until a top‑level comma or the FROM keyword.
    private String parseSelectExpression() throws SqlSyntaxException {
        StringBuilder sb = new StringBuilder();
        int parenDepth = 0;
        while (true) {
            SqlToken t = peek();
            if ((t.type == SqlToken.Type.SYMBOL && (",".equals(t.value) || ";".equals(t.value)) && parenDepth == 0) ||
                (t.type == SqlToken.Type.KEYWORD && "FROM".equalsIgnoreCase(t.value) && parenDepth == 0) ||
                t.type == SqlToken.Type.EOF) {
                break;
            }
            if (parenDepth == 0) {
                if (t.type == SqlToken.Type.KEYWORD && "AS".equalsIgnoreCase(t.value)) {
                    break;
                }
                if (sb.length() > 0 && 
                    (t.type == SqlToken.Type.IDENTIFIER || t.type == SqlToken.Type.KEYWORD) && 
                    !"FROM".equalsIgnoreCase(t.value) && 
                    !"JOIN".equalsIgnoreCase(t.value) && 
                    !"INNER".equalsIgnoreCase(t.value) && 
                    !"LEFT".equalsIgnoreCase(t.value) && 
                    !"RIGHT".equalsIgnoreCase(t.value) && 
                    !"CROSS".equalsIgnoreCase(t.value) && 
                    !"UNION".equalsIgnoreCase(t.value) && 
                    !"ORDER".equalsIgnoreCase(t.value) && 
                    !"GROUP".equalsIgnoreCase(t.value) && 
                    !"LIMIT".equalsIgnoreCase(t.value) && 
                    !"WHERE".equalsIgnoreCase(t.value)) {
                    
                    SqlToken prev = (pos > 0) ? tokens.get(pos - 1) : null;
                    boolean prevCanPrecedeAlias = prev != null && (
                        prev.type == SqlToken.Type.IDENTIFIER ||
                        prev.type == SqlToken.Type.NUMBER ||
                        prev.type == SqlToken.Type.STRING ||
                        (prev.type == SqlToken.Type.SYMBOL && ")".equals(prev.value)) ||
                        (prev.type == SqlToken.Type.KEYWORD && ("NULL".equalsIgnoreCase(prev.value) || "TRUE".equalsIgnoreCase(prev.value) || "FALSE".equalsIgnoreCase(prev.value)))
                    );
                    
                    if (prevCanPrecedeAlias) {
                        SqlToken next = (pos + 1 < tokens.size()) ? tokens.get(pos + 1) : null;
                        if (next == null || 
                            (next.type == SqlToken.Type.SYMBOL && ",".equals(next.value)) ||
                            (next.type == SqlToken.Type.KEYWORD && "FROM".equalsIgnoreCase(next.value)) ||
                            next.type == SqlToken.Type.EOF) {
                            break;
                        }
                    }
                }
            }
            consume();

            // Track parentheses depth for nested functions
            if (t.type == SqlToken.Type.SYMBOL) {
                if ("(".equals(t.value)) {
                    parenDepth++;
                } else if (")".equals(t.value) && parenDepth > 0) {
                    parenDepth--;
                }
            }

            if (t.type == SqlToken.Type.STRING) {
                sb.append("'").append(t.value.replace("'", "\\'")).append("'");
            } else {
                sb.append(t.value);
            }

            // Add space intelligently:
            //  - Never before '(' (so SUM(x) stays compact, not "SUM (x)")
            //  - Never after '(' or before ')'
            //  - Between normal tokens (identifiers, keywords, literals, operators)
            if (t.type != SqlToken.Type.SYMBOL) {
                SqlToken next = peek();
                boolean nextIsParen = next.type == SqlToken.Type.SYMBOL && "(".equals(next.value);
                boolean nextIsComma = next.type == SqlToken.Type.SYMBOL && ",".equals(next.value);
                boolean nextIsCloseParen = next.type == SqlToken.Type.SYMBOL && ")".equals(next.value);
                boolean nextIsDot = next.type == SqlToken.Type.SYMBOL && ".".equals(next.value);
                if (!nextIsParen && !nextIsComma && !nextIsCloseParen && !nextIsDot) {
                    sb.append(' ');
                }
            } else {
                // After '(' don't add space; after other symbols (operators) add one, except dot '.' and '@'
                if (!")".equals(t.value) && !"(".equals(t.value) && !".".equals(t.value) && !"@".equals(t.value)) {
                    SqlToken next = peek();
                    boolean nextIsCloseParen = next.type == SqlToken.Type.SYMBOL && ")".equals(next.value);
                    if (!nextIsCloseParen) {
                        sb.append(' ');
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private Command parseUpdate() throws SqlSyntaxException {
        consume(); // UPDATE
        String tableName = parseTableName("Expected table name in UPDATE");

        expectKeyword("SET", "Expected 'SET' in UPDATE");

        Map<String, Object> updates = new HashMap<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name for assignment");
            String colName = tokens.get(pos - 1).value;

            expectSymbol("=", "Expected '=' for assignment");

            SqlToken valToken = peek();
            boolean negative = false;
            if (valToken.type == SqlToken.Type.SYMBOL && (valToken.value.equals("-") || valToken.value.equals("+"))) {
                if (valToken.value.equals("-")) {
                    negative = true;
                }
                if (pos + 1 < tokens.size() && tokens.get(pos + 1).type == SqlToken.Type.NUMBER) {
                    consume(); // Consume '-' or '+'
                    valToken = peek();
                }
            }

            Object value;
            if (valToken.type == SqlToken.Type.STRING) {
                consume();
                value = valToken.value;
            } else if (valToken.type == SqlToken.Type.NUMBER) {
                consume();
                if (valToken.value.contains(".")) {
                    double num = Double.parseDouble(valToken.value);
                    value = negative ? -num : num;
                } else {
                    long num = Long.parseLong(valToken.value);
                    value = negative ? -num : num;
                }
            } else if (valToken.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(valToken.value)) {
                consume();
                value = null;
            } else {
                throw new SqlSyntaxException("Expected literal value (string, number, or NULL) for assignment", valToken.position);
            }

            updates.put(colName, value);

        } while (matchSymbol(","));

        Clause.Where where = parseOptionalWhere();

        return new Command.Update(tableName, updates, where);
    }

    private Command parseDelete() throws SqlSyntaxException {
        consume(); // DELETE
        expectKeyword("FROM", "Expected 'FROM' after 'DELETE'");
        String tableName = parseTableName("Expected table name in DELETE");

        Clause.Where where = parseOptionalWhere();

        return new Command.Delete(tableName, where);
    }

    private Clause.Where parseOptionalWhere() throws SqlSyntaxException {
        if (!matchKeyword("WHERE")) {
            return null;
        }
        return parseWhereExpression();
    }

    private Clause.Where parseWhereExpression() throws SqlSyntaxException {
        Clause.Where left = parseWhereAnd();
        while (matchKeyword("OR")) {
            Clause.Where right = parseWhereAnd();
            List<Clause.Where> subs = new ArrayList<>();
            subs.add(left);
            subs.add(right);
            left = new Clause.Where("OR", subs);
        }
        return left;
    }

    private Clause.Where parseWhereAnd() throws SqlSyntaxException {
        Clause.Where left = parseWhereUnary();
        while (matchKeyword("AND")) {
            Clause.Where right = parseWhereUnary();
            List<Clause.Where> subs = new ArrayList<>();
            subs.add(left);
            subs.add(right);
            left = new Clause.Where("AND", subs);
        }
        return left;
    }

    private Clause.Where parseWhereUnary() throws SqlSyntaxException {
        if (matchKeyword("NOT")) {
            Clause.Where sub = parseWhereUnary();
            List<Clause.Where> subs = new ArrayList<>();
            subs.add(sub);
            return new Clause.Where("NOT", subs);
        }
        return parseWherePrimary();
    }

    private Clause.Where parseWherePrimary() throws SqlSyntaxException {
        if (matchSymbol("(")) {
            Clause.Where expr = parseWhereExpression();
            expectSymbol(")", "Expected ')' to close parenthesized WHERE expression");
            return expr;
        }
        return parseSimpleComparison();
    }

    private Clause.Where parseSimpleComparison() throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, "Expected column name in WHERE filter");
        String column = tokens.get(pos - 1).value;
        if (matchSymbol(".")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
            column = column + "." + tokens.get(pos - 1).value;
        }

        // Support IS NULL / IS NOT NULL
        if (matchKeyword("IS")) {
            if (matchKeyword("NOT")) {
                expectKeyword("NULL", "Expected 'NULL' after 'IS NOT'");
                return new Clause.Where(column, "IS NOT NULL", null);
            } else {
                expectKeyword("NULL", "Expected 'NULL' after 'IS'");
                return new Clause.Where(column, "IS NULL", null);
            }
        }

        SqlToken opToken = peek();
        if (opToken.type != SqlToken.Type.SYMBOL && 
            !(opToken.type == SqlToken.Type.KEYWORD && 
             ("LIKE".equalsIgnoreCase(opToken.value) || 
              "IN".equalsIgnoreCase(opToken.value) || 
              "BETWEEN".equalsIgnoreCase(opToken.value)))) {
            throw new SqlSyntaxException("Expected operator (=, !=, <>, >, <, >=, <=, LIKE, IN, BETWEEN) in WHERE filter", opToken.position);
        }
        consume();
        String operator = opToken.value.toUpperCase();

        if ("IN".equals(operator)) {
            expectSymbol("(", "Expected '(' after 'IN'");
            List<Object> inValues = new ArrayList<>();
            do {
                SqlToken tk = peek();
                if (tk.type == SqlToken.Type.STRING) {
                    consume();
                    inValues.add(tk.value);
                } else if (tk.type == SqlToken.Type.NUMBER) {
                    consume();
                    if (tk.value.contains(".")) {
                        inValues.add(Double.parseDouble(tk.value));
                    } else {
                        inValues.add(Long.parseLong(tk.value));
                    }
                } else {
                    throw new SqlSyntaxException("Expected literal value in IN list", tk.position);
                }
            } while (matchSymbol(","));
            expectSymbol(")", "Expected ')' to close IN list");
            return new Clause.Where(column, "IN", inValues);
        }

        if ("BETWEEN".equals(operator)) {
            SqlToken lowToken = peek();
            Object low;
            if (lowToken.type == SqlToken.Type.STRING) {
                consume();
                low = lowToken.value;
            } else if (lowToken.type == SqlToken.Type.NUMBER) {
                consume();
                if (lowToken.value.contains(".")) low = Double.parseDouble(lowToken.value);
                else low = Long.parseLong(lowToken.value);
            } else {
                throw new SqlSyntaxException("Expected low value in BETWEEN clause", lowToken.position);
            }

            expectKeyword("AND", "Expected 'AND' in BETWEEN clause");

            SqlToken highToken = peek();
            Object high;
            if (highToken.type == SqlToken.Type.STRING) {
                consume();
                high = highToken.value;
            } else if (highToken.type == SqlToken.Type.NUMBER) {
                consume();
                if (highToken.value.contains(".")) high = Double.parseDouble(highToken.value);
                else high = Long.parseLong(highToken.value);
            } else {
                throw new SqlSyntaxException("Expected high value in BETWEEN clause", highToken.position);
            }

            return new Clause.Where(column, "BETWEEN", low, high);
        }

        SqlToken valToken = peek();
        Object value;
        boolean isValueColumn = false;
        if (valToken.type == SqlToken.Type.STRING) {
            consume();
            value = valToken.value;
        } else if (valToken.type == SqlToken.Type.NUMBER) {
            consume();
            if (valToken.value.contains(".")) {
                value = Double.parseDouble(valToken.value);
            } else {
                value = Long.parseLong(valToken.value);
            }
        } else if (valToken.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(valToken.value)) {
            consume();
            value = null;
        } else if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
            consume();
            String valCol = valToken.value;
            if (matchSymbol(".")) {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name after '.'");
                valCol = valCol + "." + tokens.get(pos - 1).value;
            }
            if (matchSymbol("(")) {
                StringBuilder params = new StringBuilder("(");
                int depth = 1;
                while (depth > 0 && peek().type != SqlToken.Type.EOF) {
                    SqlToken tok = consume();
                    if (tok.type == SqlToken.Type.SYMBOL && "(".equals(tok.value)) {
                        depth++;
                    } else if (tok.type == SqlToken.Type.SYMBOL && ")".equals(tok.value)) {
                        depth--;
                    }
                    if (tok.type == SqlToken.Type.STRING) {
                        params.append("'").append(tok.value.replace("'", "\\'")).append("'");
                    } else {
                        params.append(tok.value);
                    }
                }
                valCol = valCol + params.toString();
            }
            value = valCol;
            isValueColumn = true;
        } else if (matchSymbol("@")) {
            SqlToken varToken = peek();
            if (varToken.type != SqlToken.Type.IDENTIFIER && varToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected variable name after '@' in WHERE clause", varToken.position);
            }
            consume();
            value = "@" + varToken.value;
            isValueColumn = true;
        } else {
            throw new SqlSyntaxException("Expected filter value in WHERE clause", valToken.position);
        }

        return new Clause.Where(column, operator, value, isValueColumn);
    }

    private Command parseCreateUser() throws SqlSyntaxException {
        // We consumed CREATE USER already.
        // Expect: username@host IDENTIFIED BY password
        String[] userSpec = parseUserSpec();
        String username = userSpec[0];
        String host = userSpec[1];

        expectKeyword("IDENTIFIED", "Expected 'IDENTIFIED' in CREATE USER");
        expectKeyword("BY", "Expected 'BY' after 'IDENTIFIED'");

        SqlToken passToken = peek();
        if (passToken.type != SqlToken.Type.STRING) {
            throw new SqlSyntaxException("Expected password string literal", passToken.position);
        }
        consume();
        String password = passToken.value;

        return new Command.CreateUser(username, host, password);
    }

    private Command parseGrant() throws SqlSyntaxException {
        consume(); // GRANT
        
        List<String> privileges = parsePrivileges();

        expectKeyword("ON", "Expected 'ON' in GRANT statement");

        String dbPattern = parseDbPattern();

        expectKeyword("TO", "Expected 'TO' after DB pattern");

        String[] userSpec = parseUserSpec();
        String username = userSpec[0];
        String host = userSpec[1];

        return new Command.Grant(privileges, dbPattern, username, host);
    }

    private Command parseFlush() throws SqlSyntaxException {
        consume(); // FLUSH
        expectKeyword("PRIVILEGES", "Expected 'PRIVILEGES' after 'FLUSH'");
        return new Command.FlushPrivileges();
    }

    private Command parseRevoke() throws SqlSyntaxException {
        consume(); // REVOKE
        
        List<String> privileges = parsePrivileges();

        expectKeyword("ON", "Expected 'ON' in REVOKE statement");

        String dbPattern = parseDbPattern();

        expectKeyword("FROM", "Expected 'FROM' after DB pattern");

        String[] userSpec = parseUserSpec();
        String username = userSpec[0];
        String host = userSpec[1];

        return new Command.Revoke(privileges, dbPattern, username, host);
    }

    private Command.ColumnDef parseColumnDef(String colName) throws SqlSyntaxException {
        SqlToken typeToken = peek();
        if (typeToken.type != SqlToken.Type.KEYWORD && typeToken.type != SqlToken.Type.IDENTIFIER) {
            throw new SqlSyntaxException("Expected column type for column '" + colName + "'", typeToken.position);
        }
        consume();
        StringBuilder typeBuilder = new StringBuilder(typeToken.value.toUpperCase());

        if (matchSymbol("(")) {
            typeBuilder.append("(");
            int depth = 1;
            while (depth > 0 && peek().type != SqlToken.Type.EOF) {
                SqlToken tk = peek();
                if (tk.type == SqlToken.Type.SYMBOL && tk.value.equals("(")) {
                    depth++;
                    typeBuilder.append("(");
                } else if (tk.type == SqlToken.Type.SYMBOL && tk.value.equals(")")) {
                    depth--;
                    if (depth > 0) typeBuilder.append(")");
                } else if (tk.type == SqlToken.Type.STRING) {
                    typeBuilder.append("'").append(tk.value).append("'");
                } else {
                    typeBuilder.append(tk.value);
                }
                consume();
                if (depth > 0 && peek().type == SqlToken.Type.SYMBOL && peek().value.equals(",")) {
                    typeBuilder.append(",");
                    consume();
                }
            }
            typeBuilder.append(")");
        }
        String colType = typeBuilder.toString();

        boolean isNullable = true;
        boolean isPrimaryKey = false;
        boolean isAutoIncrement = false;
        boolean isUnique = false;
        String defaultValue = null;
        String onUpdateValue = null;
        List<Map<String, Object>> inlineChecks = new ArrayList<>();

        SqlAttributes attributes = new SqlAttributes();
        while (true) {
            SqlToken ct = peek();
            if (ct.type == SqlToken.Type.KEYWORD) {
                String val = ct.value.toUpperCase();
                if ("UNSIGNED".equals(val) || "SIGNED".equals(val) || "ZEROFILL".equals(val) || 
                    "BINARY".equals(val) || "VISIBLE".equals(val) || "INVISIBLE".equals(val) || 
                    "GENERATED".equals(val) || "REFERENCES".equals(val)) {
                    SqlAttributes parsed = SqlAttributes.parse(this);
                    if (parsed.unsigned) attributes.unsigned = true;
                    if (parsed.signed) attributes.signed = true;
                    if (parsed.zerofill) {
                        attributes.zerofill = true;
                        attributes.zerofillWidth = parsed.zerofillWidth;
                    }
                    if (parsed.binaryAttr) attributes.binaryAttr = true;
                    if (!parsed.visible) attributes.visible = false;
                    if (parsed.generatedExpr != null) {
                        attributes.generatedExpr = parsed.generatedExpr;
                        attributes.generatedType = parsed.generatedType;
                    }
                    if (parsed.referencesTable != null) {
                        attributes.referencesTable = parsed.referencesTable;
                        attributes.referencesColumn = parsed.referencesColumn;
                        attributes.onDeleteAction = parsed.onDeleteAction;
                        attributes.onUpdateAction = parsed.onUpdateAction;
                    }
                    continue;
                }

                if ("CHECK".equals(val)) {
                    inlineChecks.add(parseInlineCheckConstraint());
                    continue;
                }

                if ("NOT".equals(val)) {
                    consume(); // NOT
                    expectKeyword("NULL", "Expected 'NULL' after 'NOT'");
                    isNullable = false;
                } else if ("NULL".equals(val)) {
                    consume(); // NULL
                    isNullable = true;
                } else if ("PRIMARY".equals(val)) {
                    consume(); // PRIMARY
                    expectKeyword("KEY", "Expected 'KEY' after 'PRIMARY'");
                    isPrimaryKey = true;
                    isNullable = false;
                } else if ("AUTO_INCREMENT".equals(val)) {
                    consume();
                    isAutoIncrement = true;
                } else if ("UNIQUE".equals(val)) {
                    consume();
                    if (peek().type == SqlToken.Type.KEYWORD && "KEY".equalsIgnoreCase(peek().value)) {
                        consume();
                    }
                    isUnique = true;
                } else if ("DEFAULT".equals(val)) {
                    consume();
                    SqlToken valToken = peek();
                    if (valToken.value.equals("(")) {
                        consume(); // (
                        SqlToken inner = peek();
                        defaultValue = inner.value;
                        consume();
                        expectSymbol(")", "Expected closing parenthesis for DEFAULT expression");
                    } else {
                        defaultValue = valToken.value;
                        consume();
                        if (peek().type == SqlToken.Type.SYMBOL && "(".equals(peek().value)) {
                            consume(); // (
                            StringBuilder funcArgs = new StringBuilder();
                            while (peek().type != SqlToken.Type.EOF && !peek().value.equals(")")) {
                                funcArgs.append(consume().value);
                            }
                            expectSymbol(")", "Expected ')' after function call in DEFAULT");
                            defaultValue = defaultValue + "(" + funcArgs.toString() + ")";
                        }
                    }
                } else if ("ON".equals(val)) {
                    consume(); // ON
                    expectKeyword("UPDATE", "Expected 'UPDATE' after 'ON'");
                    SqlToken updateValToken = peek();
                    onUpdateValue = updateValToken.value;
                    consume();
                } else if ("CHARACTER".equals(val)) {
                    consume(); // CHARACTER
                    expectKeyword("SET", "Expected 'SET' after 'CHARACTER'");
                    SqlToken csToken = peek();
                    if (csToken.type == SqlToken.Type.IDENTIFIER || csToken.type == SqlToken.Type.KEYWORD) {
                        consume();
                        colType += " CHARACTER SET " + csToken.value;
                    } else {
                        throw new SqlSyntaxException("Expected character set name", csToken.position);
                    }
                } else if ("CHARSET".equals(val)) {
                    consume(); // CHARSET
                    SqlToken csToken2 = peek();
                    if (csToken2.type == SqlToken.Type.IDENTIFIER || csToken2.type == SqlToken.Type.KEYWORD) {
                        consume();
                        colType += " CHARACTER SET " + csToken2.value;
                    } else {
                        throw new SqlSyntaxException("Expected charset name", csToken2.position);
                    }
                } else if ("COLLATE".equals(val)) {
                    consume(); // COLLATE
                    SqlToken collToken = peek();
                    if (collToken.type == SqlToken.Type.IDENTIFIER || collToken.type == SqlToken.Type.KEYWORD) {
                        consume();
                        colType += " COLLATE " + collToken.value;
                    } else {
                        throw new SqlSyntaxException("Expected collation name", collToken.position);
                    }
                } else if ("COMMENT".equals(val)) {
                    consume(); // COMMENT
                    SqlToken commentVal = peek();
                    if (commentVal.type == SqlToken.Type.STRING) {
                        consume(); // Consume string comment
                    } else {
                        throw new SqlSyntaxException("Expected comment string after COMMENT", commentVal.position);
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        Command.ColumnDef def = new Command.ColumnDef(colName, colType, isNullable, defaultValue, onUpdateValue, isAutoIncrement, isPrimaryKey, isUnique);
        def.attributes = attributes;
        def.checks.addAll(inlineChecks);
        return def;
    }

    private Command.PositionSpec parsePositionSpec() throws SqlSyntaxException {
        if (matchKeyword("FIRST")) {
            return new Command.PositionSpec("FIRST", null);
        } else if (matchKeyword("AFTER")) {
            expect(SqlToken.Type.IDENTIFIER, "Expected target column name after AFTER");
            String target = tokens.get(pos - 1).value;
            return new Command.PositionSpec("AFTER", target);
        }
        return null;
    }

    private Command parseAddColumn(String tableName) throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, "Expected column name to add");
        String colName = tokens.get(pos - 1).value;
        Command.ColumnDef cd = parseColumnDef(colName);
        Command.PositionSpec ps = parsePositionSpec();
        return new Command.AlterTable(tableName, "ADD_COLUMN", cd, ps);
    }

    private Command parseModifyColumn(String tableName) throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, "Expected column name to modify");
        String colName = tokens.get(pos - 1).value;
        Command.ColumnDef cd = parseColumnDef(colName);
        Command.PositionSpec ps = parsePositionSpec();
        return new Command.AlterTable(tableName, "MODIFY_COLUMN", cd, ps);
    }

    private Command parseChangeColumn(String tableName) throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, "Expected old column name");
        String oldColName = tokens.get(pos - 1).value;
        expect(SqlToken.Type.IDENTIFIER, "Expected new column name");
        String newColName = tokens.get(pos - 1).value;
        Command.ColumnDef cd = parseColumnDef(newColName);
        Command.PositionSpec ps = parsePositionSpec();
        return new Command.AlterTable(tableName, "CHANGE_COLUMN", oldColName, cd, ps);
    }

    private Command parseRenameColumn(String tableName) throws SqlSyntaxException {
        expect(SqlToken.Type.IDENTIFIER, "Expected old column name");
        String oldColName = tokens.get(pos - 1).value;
        expectKeyword("TO", "Expected 'TO' after old column name in RENAME COLUMN");
        expect(SqlToken.Type.IDENTIFIER, "Expected new column name");
        String newColName = tokens.get(pos - 1).value;
        return new Command.AlterTable(tableName, "RENAME_COLUMN", oldColName, newColName);
    }

    private Command parseAddPrimaryKey(String tableName) throws SqlSyntaxException {
        expectSymbol("(", "Expected '(' after PRIMARY KEY");
        List<String> pkCols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name in PRIMARY KEY");
            pkCols.add(tokens.get(pos - 1).value);
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' after PRIMARY KEY columns");
        return new Command.AlterTable(tableName, "ADD_PRIMARY_KEY", pkCols);
    }

    private Command parseAddForeignKey(String tableName) throws SqlSyntaxException {
        expectSymbol("(", "Expected '(' after FOREIGN KEY");
        List<String> fkCols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name in FOREIGN KEY");
            fkCols.add(tokens.get(pos - 1).value);
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' after FOREIGN KEY columns");
        
        expectKeyword("REFERENCES", "Expected 'REFERENCES' after FOREIGN KEY columns");
        expect(SqlToken.Type.IDENTIFIER, "Expected parent table name");
        String refTable = tokens.get(pos - 1).value;
        
        expectSymbol("(", "Expected '(' after parent table name");
        List<String> refCols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected parent column name");
            refCols.add(tokens.get(pos - 1).value);
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' after parent columns");
        
        parseOptionalForeignKeyActions();
        
        return new Command.AlterTable(tableName, "ADD_FOREIGN_KEY", fkCols, refTable, refCols);
    }

    private void parseOptionalForeignKeyActions() throws SqlSyntaxException {
        while (true) {
            if (matchKeyword("ON")) {
                if (matchKeyword("DELETE") || matchKeyword("UPDATE")) {
                    if (matchKeyword("CASCADE")) {
                        // skip
                    } else if (matchKeyword("SET")) {
                        expectKeyword("NULL", "Expected 'NULL' after 'SET'");
                    } else if (matchKeyword("RESTRICT") || matchKeyword("NO")) {
                        if (matchKeyword("ACTION")) {
                            // skip
                        }
                    } else {
                        throw new SqlSyntaxException("Expected CASCADE, SET NULL, RESTRICT, or NO ACTION", peek().position);
                    }
                } else {
                    throw new SqlSyntaxException("Expected DELETE or UPDATE after ON", peek().position);
                }
            } else {
                break;
            }
        }
    }

    private Command parseAddUnique(String tableName) throws SqlSyntaxException {
        return parseAddUnique(tableName, null);
    }

    private Command parseAddUnique(String tableName, String constraintName) throws SqlSyntaxException {
        String indexName = constraintName;
        if (indexName == null && isIdentifier(peek())) {
            consume();
            indexName = tokens.get(pos - 1).value;
        }
        expectSymbol("(", "Expected '(' to open UNIQUE column list");
        List<String> cols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name in UNIQUE list");
            cols.add(tokens.get(pos - 1).value);
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' to close UNIQUE column list");
        return new Command.AlterTable(tableName, "ADD_UNIQUE", cols, indexName);
    }

    private Command parseAddIndex(String tableName, String indexType) throws SqlSyntaxException {
        String indexName = null;
        if (isIdentifier(peek())) {
            consume();
            indexName = tokens.get(pos - 1).value;
        }
        expectSymbol("(", "Expected '(' to open INDEX column list");
        List<String> cols = new ArrayList<>();
        do {
            expect(SqlToken.Type.IDENTIFIER, "Expected column name in INDEX list");
            cols.add(tokens.get(pos - 1).value);
        } while (matchSymbol(","));
        expectSymbol(")", "Expected ')' to close INDEX column list");
        Command.AlterTable cmd = new Command.AlterTable(tableName, "ADD_INDEX", cols, indexName);
        cmd.indexType = indexType;
        return cmd;
    }

    private Map<String, Object> parseInlineCheckConstraint() throws SqlSyntaxException {
        expectKeyword("CHECK", "Expected 'CHECK'");
        return parseCheckConstraintBody();
    }

    private Command parseAddCheck(String tableName) throws SqlSyntaxException {
        Map<String, Object> check = parseCheckConstraintBody();
        return new Command.AlterTable(tableName, "ADD_CHECK", check);
    }

    private Command parseAlter() throws SqlSyntaxException {
        consume(); // ALTER
        if (matchKeyword("DATABASE")) {
            SqlToken dbToken = peek();
            if (dbToken.type != SqlToken.Type.IDENTIFIER && dbToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected database name after 'ALTER DATABASE'", dbToken.position);
            }
            consume();
            String dbName = dbToken.value;

            String charset = null;
            String collation = null;

            boolean parsingOptions = true;
            while (parsingOptions) {
                if (matchName("DEFAULT")) {
                    // skip
                }
                
                if (matchName("CHARACTER")) {
                    matchName("SET"); // optional SET
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        charset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected character set name", valToken.position);
                    }
                } else if (matchName("CHARSET")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        charset = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected charset name", valToken.position);
                    }
                } else if (matchName("COLLATE")) {
                    matchSymbol("="); // optional =
                    SqlToken valToken = peek();
                    if (valToken.type == SqlToken.Type.IDENTIFIER || valToken.type == SqlToken.Type.KEYWORD) {
                        collation = valToken.value;
                        consume();
                    } else {
                        throw new SqlSyntaxException("Expected collation name", valToken.position);
                    }
                } else {
                    parsingOptions = false;
                }
            }

            if (charset == null && collation == null) {
                throw new SqlSyntaxException("Expected CHARACTER SET or COLLATE options after 'ALTER DATABASE <name>'", peek().position);
            }
            return new Command.AlterDatabase(dbName, charset, collation);
        }

        expectKeyword("TABLE", "Expected 'TABLE' or 'DATABASE' after 'ALTER'");
        expect(SqlToken.Type.IDENTIFIER, "Expected table name in ALTER TABLE");
        String tableName = tokens.get(pos - 1).value;

        SqlToken opToken = peek();
        if (opToken.type != SqlToken.Type.KEYWORD) {
            throw new SqlSyntaxException("Expected ALTER TABLE operation", opToken.position);
        }
        String op = opToken.value.toUpperCase();
        consume();

        if ("ADD".equals(op)) {
            SqlToken next = peek();
            if (next.type == SqlToken.Type.KEYWORD) {
                String nextVal = next.value.toUpperCase();
                if ("CONSTRAINT".equals(nextVal)) {
                    consume(); // CONSTRAINT
                    String constraintName = null;
                    if (peek().type == SqlToken.Type.IDENTIFIER || 
                        (peek().type == SqlToken.Type.KEYWORD && 
                         !"UNIQUE".equalsIgnoreCase(peek().value) && 
                         !"PRIMARY".equalsIgnoreCase(peek().value) && 
                         !"FOREIGN".equalsIgnoreCase(peek().value) && 
                         !"CHECK".equalsIgnoreCase(peek().value))) {
                        constraintName = peek().value;
                        consume();
                    }
                    SqlToken afterConstraint = peek();
                    if (afterConstraint.type == SqlToken.Type.KEYWORD) {
                        String subOp = afterConstraint.value.toUpperCase();
                        consume();
                        if ("UNIQUE".equals(subOp)) {
                            if (peek().type == SqlToken.Type.KEYWORD && "KEY".equalsIgnoreCase(peek().value)) {
                                consume();
                            }
                            return parseAddUnique(tableName, constraintName);
                        } else if ("PRIMARY".equals(subOp)) {
                            expectKeyword("KEY", "Expected 'KEY' after 'PRIMARY'");
                            return parseAddPrimaryKey(tableName);
                        } else if ("FOREIGN".equals(subOp)) {
                            expectKeyword("KEY", "Expected 'KEY' after 'FOREIGN'");
                            return parseAddForeignKey(tableName);
                        } else if ("CHECK".equals(subOp)) {
                            return parseAddCheck(tableName);
                        }
                    }
                    throw new SqlSyntaxException("Expected UNIQUE, PRIMARY KEY, FOREIGN KEY, or CHECK after CONSTRAINT name", afterConstraint.position);
                } else if ("COLUMN".equals(nextVal)) {
                    consume(); // COLUMN
                    return parseAddColumn(tableName);
                } else if ("PRIMARY".equals(nextVal)) {
                    consume(); // PRIMARY
                    expectKeyword("KEY", "Expected 'KEY' after 'PRIMARY'");
                    return parseAddPrimaryKey(tableName);
                } else if ("FOREIGN".equals(nextVal)) {
                    consume(); // FOREIGN
                    expectKeyword("KEY", "Expected 'KEY' after 'FOREIGN'");
                    return parseAddForeignKey(tableName);
                } else if ("UNIQUE".equals(nextVal)) {
                    consume(); // UNIQUE
                    if (peek().type == SqlToken.Type.KEYWORD && "KEY".equalsIgnoreCase(peek().value)) {
                        consume();
                    }
                    return parseAddUnique(tableName);
                } else if ("INDEX".equals(nextVal)) {
                    consume(); // INDEX
                    return parseAddIndex(tableName, "BTREE");
                } else if ("FULLTEXT".equals(nextVal) || "SPATIAL".equals(nextVal)) {
                    consume(); // FULLTEXT / SPATIAL
                    if (peek().type == SqlToken.Type.KEYWORD && ("INDEX".equalsIgnoreCase(peek().value) || "KEY".equalsIgnoreCase(peek().value))) {
                        consume(); // INDEX / KEY
                    }
                    return parseAddIndex(tableName, nextVal.toUpperCase());
                } else if ("CHECK".equals(nextVal)) {
                    consume(); // CHECK
                    return parseAddCheck(tableName);
                } else {
                    return parseAddColumn(tableName);
                }
            } else {
                return parseAddColumn(tableName);
            }
        } else if ("MODIFY".equals(op)) {
            if (peek().type == SqlToken.Type.KEYWORD && "COLUMN".equalsIgnoreCase(peek().value)) {
                consume();
            }
            return parseModifyColumn(tableName);
        } else if ("CHANGE".equals(op)) {
            if (peek().type == SqlToken.Type.KEYWORD && "COLUMN".equalsIgnoreCase(peek().value)) {
                consume();
            }
            return parseChangeColumn(tableName);
        } else if ("RENAME".equals(op)) {
            SqlToken next = peek();
            if (next.type == SqlToken.Type.KEYWORD) {
                String nextVal = next.value.toUpperCase();
                if ("COLUMN".equals(nextVal)) {
                    consume(); // COLUMN
                    return parseRenameColumn(tableName);
                } else if ("TO".equals(nextVal)) {
                    consume(); // TO
                    expect(SqlToken.Type.IDENTIFIER, "Expected new table name");
                    String newTableName = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "RENAME_TABLE", (String) null, newTableName);
                }
            }
            throw new SqlSyntaxException("Expected COLUMN or TO after RENAME", next.position);
        } else if ("DROP".equals(op)) {
            SqlToken next = peek();
            if (next.type == SqlToken.Type.KEYWORD) {
                String nextVal = next.value.toUpperCase();
                if ("COLUMN".equals(nextVal)) {
                    consume(); // COLUMN
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name to drop");
                    String col = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "DROP_COLUMN", col);
                } else if ("PRIMARY".equals(nextVal)) {
                    consume(); // PRIMARY
                    expectKeyword("KEY", "Expected 'KEY' after 'DROP PRIMARY'");
                    return new Command.AlterTable(tableName, "DROP_PRIMARY_KEY");
                } else if ("FOREIGN".equals(nextVal)) {
                    consume(); // FOREIGN
                    expectKeyword("KEY", "Expected 'KEY' after 'DROP FOREIGN'");
                    expect(SqlToken.Type.IDENTIFIER, "Expected foreign key constraint name to drop");
                    String fk = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "DROP_FOREIGN_KEY", fk);
                } else if ("INDEX".equals(nextVal) || "KEY".equals(nextVal)) {
                    consume();
                    expect(SqlToken.Type.IDENTIFIER, "Expected index name to drop");
                    String idx = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "DROP_INDEX", idx);
                } else if ("CHECK".equals(nextVal)) {
                    consume(); // CHECK
                    expect(SqlToken.Type.IDENTIFIER, "Expected check constraint name to drop");
                    String chkName = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "DROP_CHECK", chkName);
                } else {
                    expect(SqlToken.Type.IDENTIFIER, "Expected column name to drop");
                    String col = tokens.get(pos - 1).value;
                    return new Command.AlterTable(tableName, "DROP_COLUMN", col);
                }
            } else {
                expect(SqlToken.Type.IDENTIFIER, "Expected column name to drop");
                String col = tokens.get(pos - 1).value;
                return new Command.AlterTable(tableName, "DROP_COLUMN", col);
            }
        } else if ("ALTER".equals(op)) {
            if (peek().type == SqlToken.Type.KEYWORD && "COLUMN".equalsIgnoreCase(peek().value)) {
                consume();
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected column name in ALTER COLUMN");
            String colName = tokens.get(pos - 1).value;
            if (matchKeyword("SET")) {
                if (matchKeyword("DEFAULT")) {
                    SqlToken defaultValToken = peek();
                    String defVal = defaultValToken.value;
                    consume();
                    return new Command.AlterTable(tableName, "DEFAULT_VALUE_CHANGE", colName, defVal, false);
                } else if (matchKeyword("ON")) {
                    expectKeyword("UPDATE", "Expected 'UPDATE' after 'SET ON'");
                    SqlToken updateValToken = peek();
                    String updateVal = updateValToken.value;
                    consume();
                    return new Command.AlterTable(tableName, "ON_UPDATE_CHANGE", colName, updateVal, false, true);
                } else {
                    throw new SqlSyntaxException("Expected DEFAULT or ON UPDATE after ALTER COLUMN col SET", peek().position);
                }
            } else if (matchKeyword("DROP")) {
                if (matchKeyword("DEFAULT")) {
                    return new Command.AlterTable(tableName, "DEFAULT_VALUE_CHANGE", colName, null, true);
                } else if (matchKeyword("ON")) {
                    expectKeyword("UPDATE", "Expected 'UPDATE' after 'DROP ON'");
                    return new Command.AlterTable(tableName, "ON_UPDATE_CHANGE", colName, null, true, true);
                } else {
                    throw new SqlSyntaxException("Expected DEFAULT or ON UPDATE after ALTER COLUMN col DROP", peek().position);
                }
            }
            throw new SqlSyntaxException("Expected SET DEFAULT, SET ON UPDATE, DROP DEFAULT or DROP ON UPDATE", peek().position);
        } else if ("ENGINE".equals(op)) {
            matchSymbol("=");
            expect(SqlToken.Type.IDENTIFIER, "Expected engine name");
            String eng = tokens.get(pos - 1).value;
            return Command.AlterTable.createEngineOrCharSet(tableName, "ENGINE_CHANGE", eng);
        } else if ("CHARACTER".equals(op)) {
            expectKeyword("SET", "Expected 'SET' after 'CHARACTER'");
            expect(SqlToken.Type.IDENTIFIER, "Expected character set name");
            String cs = tokens.get(pos - 1).value;
            return Command.AlterTable.createEngineOrCharSet(tableName, "CHARACTER_SET_CHANGE", cs);
        } else if ("CONVERT".equals(op)) {
            expectKeyword("TO", "Expected 'TO' after 'CONVERT'");
            String charset = null;
            if (matchName("CHARACTER")) {
                expectKeyword("SET", "Expected 'SET' after 'CHARACTER'");
            } else {
                expectKeyword("CHARSET", "Expected 'CHARACTER SET' or 'CHARSET' after 'CONVERT TO'");
            }
            SqlToken csToken = peek();
            if (csToken.type != SqlToken.Type.IDENTIFIER && csToken.type != SqlToken.Type.KEYWORD) {
                throw new SqlSyntaxException("Expected character set name", csToken.position);
            }
            charset = csToken.value;
            consume();

            String collation = null;
            if (matchName("COLLATE")) {
                SqlToken collToken = peek();
                if (collToken.type != SqlToken.Type.IDENTIFIER && collToken.type != SqlToken.Type.KEYWORD) {
                    throw new SqlSyntaxException("Expected collation name", collToken.position);
                }
                collation = collToken.value;
                consume();
            }
            return new Command.AlterTableConvert(tableName, charset, collation);
        }
        
        throw new SqlSyntaxException("Unsupported ALTER TABLE operation: " + op, opToken.position);
    }

    private Command parseTruncate() throws SqlSyntaxException {
        consume(); // TRUNCATE
        if (peek().type == SqlToken.Type.KEYWORD && "TABLE".equalsIgnoreCase(peek().value)) {
            consume(); // Optional TABLE
        }
        expect(SqlToken.Type.IDENTIFIER, "Expected table name in TRUNCATE");
        String tableName = tokens.get(pos - 1).value;
        return new Command.TruncateTable(tableName);
    }

    private Command parseRename() throws SqlSyntaxException {
        consume(); // RENAME
        expectKeyword("TABLE", "Expected 'TABLE' after 'RENAME'");
        expect(SqlToken.Type.IDENTIFIER, "Expected table name in RENAME TABLE");
        String oldName = tokens.get(pos - 1).value;

        expectKeyword("TO", "Expected 'TO' in RENAME TABLE");
        expect(SqlToken.Type.IDENTIFIER, "Expected new table name in RENAME TABLE");
        String newName = tokens.get(pos - 1).value;

        return new Command.RenameTable(oldName, newName);
    }

    private Command parseStart() throws SqlSyntaxException {
        consume(); // START
        expectKeyword("TRANSACTION", "Expected 'TRANSACTION' after 'START'");
        return new Command.StartTransaction();
    }

    private Command parseBegin() throws SqlSyntaxException {
        consume(); // BEGIN
        if (peek().type == SqlToken.Type.KEYWORD && "WORK".equalsIgnoreCase(peek().value)) {
            consume();
        }
        return new Command.StartTransaction();
    }

    private Command parseCommit() throws SqlSyntaxException {
        consume(); // COMMIT
        if (peek().type == SqlToken.Type.KEYWORD && "WORK".equalsIgnoreCase(peek().value)) {
            consume();
        }
        return new Command.Commit();
    }

    private Command parseRollback() throws SqlSyntaxException {
        consume(); // ROLLBACK
        if (peek().type == SqlToken.Type.KEYWORD && "WORK".equalsIgnoreCase(peek().value)) {
            consume();
        }
        if (peek().type == SqlToken.Type.KEYWORD && "TO".equalsIgnoreCase(peek().value)) {
            consume(); // TO
            if (peek().type == SqlToken.Type.KEYWORD && "SAVEPOINT".equalsIgnoreCase(peek().value)) {
                consume(); // SAVEPOINT
            }
            expect(SqlToken.Type.IDENTIFIER, "Expected savepoint name in ROLLBACK TO");
            String name = tokens.get(pos - 1).value;
            return new Command.RollbackTo(name);
        }
        return new Command.Rollback();
    }

    private Command parseSavepoint() throws SqlSyntaxException {
        consume(); // SAVEPOINT
        expect(SqlToken.Type.IDENTIFIER, "Expected savepoint name");
        String name = tokens.get(pos - 1).value;
        return new Command.Savepoint(name);
    }

    private String parseRestOfDdl(String prefix) {
        StringBuilder ddlBuilder = new StringBuilder();
        ddlBuilder.append(prefix);
        while (peek().type != SqlToken.Type.EOF) {
            SqlToken tok = peek();
            if (tok.type == SqlToken.Type.EOF || (tok.type == SqlToken.Type.SYMBOL && ";".equals(tok.value) && pos == tokens.size() - 1)) {
                break;
            }
            if (tok.type == SqlToken.Type.STRING) {
                ddlBuilder.append("'").append(tok.value.replace("'", "\\'")).append("' ");
            } else {
                ddlBuilder.append(tok.value).append(" ");
            }
            consume();
        }
        return ddlBuilder.toString().trim();
    }

    private String parseDbPattern() throws SqlSyntaxException {
        String dbPart = "";
        String tablePart = "";
        
        SqlToken firstToken = peek();
        if (firstToken.type == SqlToken.Type.SYMBOL && "*".equals(firstToken.value)) {
            consume();
            dbPart = "*";
        } else if (firstToken.type == SqlToken.Type.IDENTIFIER || firstToken.type == SqlToken.Type.KEYWORD) {
            consume();
            dbPart = firstToken.value;
        } else {
            throw new SqlSyntaxException("Expected database pattern (e.g. *.* or db.*)", firstToken.position);
        }

        if (matchSymbol(".")) {
            SqlToken secondToken = peek();
            if (secondToken.type == SqlToken.Type.SYMBOL && "*".equals(secondToken.value)) {
                consume();
                tablePart = "*";
            } else if (secondToken.type == SqlToken.Type.IDENTIFIER || secondToken.type == SqlToken.Type.KEYWORD) {
                consume();
                tablePart = secondToken.value;
            } else {
                throw new SqlSyntaxException("Expected table pattern after '.' (e.g. * or table_name)", secondToken.position);
            }
        } else {
            tablePart = "*";
        }

        return dbPart + "." + tablePart;
    }

    private String[] parseUserSpec() throws SqlSyntaxException {
        SqlToken userToken = peek();
        if (userToken.type != SqlToken.Type.STRING && userToken.type != SqlToken.Type.IDENTIFIER) {
            throw new SqlSyntaxException("Expected username", userToken.position);
        }
        consume();
        String username = userToken.value;

        expectSymbol("@", "Expected '@' after username");

        SqlToken hostToken = peek();
        if (hostToken.type != SqlToken.Type.STRING && hostToken.type != SqlToken.Type.IDENTIFIER) {
            throw new SqlSyntaxException("Expected host name", hostToken.position);
        }
        consume();
        String host = hostToken.value;
        
        return new String[]{username, host};
    }

    private List<String> parsePrivileges() throws SqlSyntaxException {
        List<String> privileges = new ArrayList<>();
        do {
            SqlToken privToken = peek();
            if (privToken.type != SqlToken.Type.KEYWORD && privToken.type != SqlToken.Type.IDENTIFIER) {
                throw new SqlSyntaxException("Expected privilege (e.g. SELECT, INSERT, ALL)", privToken.position);
            }
            consume();
            privileges.add(privToken.value.toUpperCase());
        } while (matchSymbol(","));
        return privileges;
    }

    private Map<String, Object> parseCheckConstraintBody() throws SqlSyntaxException {
        expectSymbol("(", "Expected '(' after CHECK");
        SqlToken colToken = peek();
        if (!isIdentifier(colToken)) {
            throw new SqlSyntaxException("Expected column name in CHECK constraint", colToken.position);
        }
        String checkCol = colToken.value;
        consume();
        
        SqlToken opToken = peek();
        String op = opToken.value.toUpperCase();
        consume();
        
        Map<String, Object> check = new HashMap<>();
        check.put("column", checkCol);
        check.put("operator", op);
        
        if ("BETWEEN".equals(op)) {
            SqlToken lowToken = peek();
            consume();
            Object low = lowToken.value;
            if (lowToken.type == SqlToken.Type.NUMBER) {
                if (lowToken.value.contains(".")) low = Double.parseDouble(lowToken.value);
                else low = Long.parseLong(lowToken.value);
            }
            
            expectKeyword("AND", "Expected 'AND' in BETWEEN clause");
            SqlToken highToken = peek();
            consume();
            Object high = highToken.value;
            if (highToken.type == SqlToken.Type.NUMBER) {
                if (highToken.value.contains(".")) high = Double.parseDouble(highToken.value);
                else high = Long.parseLong(highToken.value);
            }
            
            List<Object> range = new ArrayList<>();
            range.add(low);
            range.add(high);
            check.put("values", range);
        } else if ("IN".equals(op)) {
            expectSymbol("(", "Expected '(' after IN");
            List<Object> inValues = new ArrayList<>();
            do {
                SqlToken itemToken = peek();
                Object val = itemToken.value;
                if (itemToken.type == SqlToken.Type.NUMBER) {
                    if (itemToken.value.contains(".")) val = Double.parseDouble(itemToken.value);
                    else val = Long.parseLong(itemToken.value);
                }
                inValues.add(val);
                consume();
            } while (matchSymbol(","));
            expectSymbol(")", "Expected ')' to close IN list");
            check.put("values", inValues);
        } else {
            SqlToken valToken = peek();
            Object val = valToken.value;
            if (valToken.type == SqlToken.Type.NUMBER) {
                if (valToken.value.contains(".")) val = Double.parseDouble(valToken.value);
                else val = Long.parseLong(valToken.value);
            }
            check.put("value", val);
            consume();
        }
        expectSymbol(")", "Expected ')' to close CHECK constraint");
        return check;
    }

    private void skipParentheses() throws SqlSyntaxException {
        expectSymbol("(", "Expected '('");
        int depth = 1;
        while (depth > 0 && peek().type != SqlToken.Type.EOF) {
            SqlToken t = consume();
            if (t.type == SqlToken.Type.SYMBOL && "(".equals(t.value)) {
                depth++;
            } else if (t.type == SqlToken.Type.SYMBOL && ")".equals(t.value)) {
                depth--;
            }
        }
        if (depth > 0) {
            throw new SqlSyntaxException("Expected closing ')'", peek().position);
        }
    }

    private void skipPartitioning() throws SqlSyntaxException {
        if (matchName("PARTITION")) {
            expectKeyword("BY", "Expected 'BY' after 'PARTITION'");
            while (peek().type != SqlToken.Type.SYMBOL || !peek().value.equals("(")) {
                if (peek().type == SqlToken.Type.EOF) {
                    throw new SqlSyntaxException("Expected '(' for partition expression", peek().position);
                }
                consume();
            }
            skipParentheses();
            if (peek().type == SqlToken.Type.SYMBOL && "(".equals(peek().value)) {
                skipParentheses();
            }
        }
    }

    private String extractDefinition(int startPos) {
        if (sql != null && startPos < tokens.size()) {
            int startChar = tokens.get(startPos).position;
            int endChar = (pos < tokens.size()) ? tokens.get(pos).position : sql.length();
            if (startChar >= 0 && endChar <= sql.length() && startChar <= endChar) {
                return sql.substring(startChar, endChar).trim();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startPos; i < pos; i++) {
            SqlToken tok = tokens.get(i);
            if (tok.type == SqlToken.Type.STRING) {
                sb.append("'").append(tok.value.replace("'", "\\'")).append("' ");
            } else {
                sb.append(tok.value).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
