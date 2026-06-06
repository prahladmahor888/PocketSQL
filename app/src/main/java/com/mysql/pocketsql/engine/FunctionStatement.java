package com.mysql.pocketsql.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface FunctionStatement {
    Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception;

    class ReturnWrapper {
        public final Object value;
        public ReturnWrapper(Object value) {
            this.value = value;
        }
    }

    class DeclareStatement implements FunctionStatement {
        private final String varName;
        private final String defaultValueExpression;

        public DeclareStatement(String varName) {
            this(varName, null);
        }

        public DeclareStatement(String varName, String defaultValueExpression) {
            this.varName = varName;
            this.defaultValueExpression = defaultValueExpression;
        }

        @Override
        public Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception {
            Object val = null;
            if (defaultValueExpression != null) {
                val = SqlFunctions.evaluate(defaultValueExpression, variables, engine);
            }
            variables.put(varName, val);
            return null;
        }
    }

    class SetStatement implements FunctionStatement {
        private final String varName;
        private final String expression;

        public SetStatement(String varName, String expression) {
            this.varName = varName;
            this.expression = expression;
        }

        @Override
        public Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception {
            Object val = SqlFunctions.evaluate(expression, variables, engine);
            variables.put(varName, val);
            return null;
        }
    }

    class ReturnStatement implements FunctionStatement {
        private final String expression;

        public ReturnStatement(String expression) {
            this.expression = expression;
        }

        @Override
        public Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception {
            Object val = SqlFunctions.evaluate(expression, variables, engine);
            return new ReturnWrapper(val);
        }
    }

    class IfStatement implements FunctionStatement {
        private final String condition;
        private final List<FunctionStatement> thenBranch;
        private final List<FunctionStatement> elseBranch;

        public IfStatement(String condition, List<FunctionStatement> thenBranch, List<FunctionStatement> elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception {
            Object condVal = SqlFunctions.evaluate(condition, variables, engine);
            if (SqlFunctions.isTruthy(condVal)) {
                for (FunctionStatement stmt : thenBranch) {
                    Object ret = stmt.execute(variables, engine);
                    if (ret instanceof ReturnWrapper) {
                        return ret;
                    }
                }
            } else {
                for (FunctionStatement stmt : elseBranch) {
                    Object ret = stmt.execute(variables, engine);
                    if (ret instanceof ReturnWrapper) {
                        return ret;
                    }
                }
            }
            return null;
        }
    }

    class UdfBodyParser {
        private final List<SqlToken> tokens;
        private int pos = 0;

        public UdfBodyParser(List<SqlToken> tokens) {
            this.tokens = tokens;
        }

        private SqlToken peek() {
            if (pos >= tokens.size()) {
                return new SqlToken(SqlToken.Type.EOF, "", 0);
            }
            return tokens.get(pos);
        }

        private SqlToken consume() {
            SqlToken t = peek();
            pos++;
            return t;
        }

        private boolean matchKeyword(String kw) {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.KEYWORD && kw.equalsIgnoreCase(t.value)) {
                consume();
                return true;
            }
            return false;
        }

        private boolean matchSymbol(String sym) {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.SYMBOL && sym.equals(t.value)) {
                consume();
                return true;
            }
            return false;
        }

        private void expectSymbol(String sym, String msg) throws Exception {
            if (!matchSymbol(sym)) {
                throw new Exception(msg);
            }
        }

        private void expectKeyword(String kw, String msg) throws Exception {
            if (!matchKeyword(kw)) {
                throw new Exception(msg);
            }
        }

        private String getExpressionString(SqlToken tok) {
            if (tok.type == SqlToken.Type.STRING) {
                return "'" + tok.value.replace("'", "\\'") + "'";
            }
            return tok.value;
        }

        public List<FunctionStatement> parse() throws Exception {
            List<FunctionStatement> statements = new ArrayList<>();
            while (pos < tokens.size() && peek().type != SqlToken.Type.EOF) {
                statements.add(parseStatement());
            }
            return statements;
        }

        private FunctionStatement parseStatement() throws Exception {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.KEYWORD) {
                if ("DECLARE".equalsIgnoreCase(t.value)) {
                    consume();
                    SqlToken nameToken = consume();
                    if (nameToken.type != SqlToken.Type.IDENTIFIER && nameToken.type != SqlToken.Type.KEYWORD) {
                        throw new Exception("Expected variable name after DECLARE");
                    }
                    
                    // Consume datatype keyword
                    consume();
                    
                    // Handle type parameters e.g. VARCHAR(50) or DECIMAL(10,2)
                    if (matchSymbol("(")) {
                        consume(); // length
                        if (matchSymbol(",")) {
                            consume(); // scale
                        }
                        expectSymbol(")", "Expected ')' after datatype arguments");
                    }
                    
                    String defaultValueExpression = null;
                    if (matchKeyword("DEFAULT")) {
                        StringBuilder defExpr = new StringBuilder();
                        while (pos < tokens.size() && !peek().value.equals(";")) {
                            defExpr.append(getExpressionString(consume())).append(" ");
                        }
                        defaultValueExpression = defExpr.toString().trim();
                    }
                    
                    matchSymbol(";"); // optional semicolon
                    return new DeclareStatement(nameToken.value, defaultValueExpression);
 
                } else if ("SET".equalsIgnoreCase(t.value)) {
                    consume();
                    SqlToken nameToken = consume();
                    if (nameToken.type != SqlToken.Type.IDENTIFIER && nameToken.type != SqlToken.Type.KEYWORD) {
                        throw new Exception("Expected variable name after SET");
                    }
                    expectSymbol("=", "Expected '=' in SET assignment");
                    
                    StringBuilder exprBuilder = new StringBuilder();
                    while (pos < tokens.size() && !matchSymbol(";")) {
                        exprBuilder.append(getExpressionString(consume())).append(" ");
                    }
                    return new SetStatement(nameToken.value, exprBuilder.toString().trim());

                } else if ("RETURN".equalsIgnoreCase(t.value)) {
                    consume();
                    StringBuilder exprBuilder = new StringBuilder();
                    while (pos < tokens.size() && !matchSymbol(";")) {
                        exprBuilder.append(getExpressionString(consume())).append(" ");
                    }
                    return new ReturnStatement(exprBuilder.toString().trim());

                } else if ("IF".equalsIgnoreCase(t.value)) {
                    consume();
                    StringBuilder condBuilder = new StringBuilder();
                    while (pos < tokens.size() && !matchKeyword("THEN")) {
                        condBuilder.append(getExpressionString(consume())).append(" ");
                    }
                    
                    List<FunctionStatement> thenStatements = new ArrayList<>();
                    List<FunctionStatement> elseStatements = new ArrayList<>();
                    boolean inElse = false;
                    
                    int ifDepth = 1;
                    while (pos < tokens.size()) {
                        SqlToken tok = peek();
                        if (tok.type == SqlToken.Type.KEYWORD) {
                            if ("IF".equalsIgnoreCase(tok.value)) {
                                ifDepth++;
                            } else if ("END".equalsIgnoreCase(tok.value)) {
                                if (pos + 1 < tokens.size() && "IF".equalsIgnoreCase(tokens.get(pos + 1).value)) {
                                    ifDepth--;
                                    if (ifDepth == 0) {
                                        consume(); // END
                                        consume(); // IF
                                        matchSymbol(";"); // optional semicolon
                                        break;
                                    }
                                }
                            } else if ("ELSE".equalsIgnoreCase(tok.value) && ifDepth == 1) {
                                consume(); // ELSE
                                inElse = true;
                                continue;
                            }
                        }
                        
                        if (inElse) {
                            elseStatements.add(parseStatement());
                        } else {
                            thenStatements.add(parseStatement());
                        }
                    }
                    return new IfStatement(condBuilder.toString().trim(), thenStatements, elseStatements);
                } else if ("SELECT".equalsIgnoreCase(t.value)) {
                    consume(); // SELECT
                    
                    // Consume projection list until we see INTO
                    StringBuilder projBuilder = new StringBuilder();
                    while (pos < tokens.size() && !peek().value.equalsIgnoreCase("INTO")) {
                        projBuilder.append(getExpressionString(consume())).append(" ");
                    }
                    expectKeyword("INTO", "Expected 'INTO' keyword in SELECT INTO statement");
                    
                    // Consume variables list
                    List<String> varNames = new ArrayList<>();
                    do {
                        SqlToken varToken = consume();
                        if (varToken.type != SqlToken.Type.IDENTIFIER && varToken.type != SqlToken.Type.KEYWORD) {
                            throw new Exception("Expected variable name after INTO in SELECT INTO statement");
                        }
                        varNames.add(varToken.value);
                    } while (matchSymbol(","));
                    
                    expectKeyword("FROM", "Expected 'FROM' keyword in SELECT INTO statement");
                    
                    // Consume table name
                    SqlToken tableTok = consume();
                    if (tableTok.type != SqlToken.Type.IDENTIFIER && tableTok.type != SqlToken.Type.KEYWORD) {
                        throw new Exception("Expected table name in SELECT INTO statement");
                    }
                    String tableName = tableTok.value;
                    if (matchSymbol(".")) {
                        SqlToken subTableTok = consume();
                        tableName = tableName + "." + subTableTok.value;
                    }
                    
                    // Optional WHERE clause
                    String whereCondition = null;
                    if (matchKeyword("WHERE")) {
                        StringBuilder whereBuilder = new StringBuilder();
                        while (pos < tokens.size() && !peek().value.equals(";")) {
                            whereBuilder.append(getExpressionString(consume())).append(" ");
                        }
                        whereCondition = whereBuilder.toString().trim();
                    }
                    
                    matchSymbol(";"); // optional semicolon
                    return new SelectIntoStatement(projBuilder.toString().trim(), varNames, tableName, whereCondition);
                }
            }
            throw new Exception("Unexpected token in function body: " + t.value);
        }
    }

    class SelectIntoStatement implements FunctionStatement {
        private final String projectionExpression;
        private final List<String> varNames;
        private final String tableName;
        private final String whereCondition;

        public SelectIntoStatement(String projectionExpression, List<String> varNames, String tableName, String whereCondition) {
            this.projectionExpression = projectionExpression;
            this.varNames = varNames;
            this.tableName = tableName;
            this.whereCondition = whereCondition;
        }

        private String replaceVariables(String sql, Map<String, Object> variables) {
            try {
                SqlScanner scanner = new SqlScanner(sql);
                List<SqlToken> tokens = scanner.scan();
                StringBuilder sb = new StringBuilder();
                for (SqlToken tok : tokens) {
                    String val = tok.value;
                    String matchedKey = null;
                    for (String key : variables.keySet()) {
                        if (key.equalsIgnoreCase(val)) {
                            matchedKey = key;
                            break;
                        }
                    }
                    if (matchedKey != null) {
                        Object value = variables.get(matchedKey);
                        if (value == null) {
                            sb.append("NULL");
                        } else if (value instanceof String) {
                            sb.append("'").append(value.toString().replace("'", "\\'")).append("'");
                        } else {
                            sb.append(value.toString());
                        }
                    } else {
                        if (tok.type == SqlToken.Type.STRING) {
                            sb.append("'").append(tok.value.replace("'", "\\'")).append("'");
                        } else {
                            sb.append(tok.value);
                        }
                    }
                    sb.append(" ");
                }
                return sb.toString().trim();
            } catch (Exception e) {
                return sql;
            }
        }

        @Override
        public Object execute(Map<String, Object> variables, DatabaseEngine engine) throws Exception {
            String replacedProj = replaceVariables(projectionExpression, variables);
            String replacedWhere = whereCondition != null ? replaceVariables(whereCondition, variables) : null;
            String query = "SELECT " + replacedProj + " FROM " + tableName + (replacedWhere != null && !replacedWhere.isEmpty() ? " WHERE " + replacedWhere : "") + ";";
            
            QueryResult qr = engine.execute(query);
            if (!qr.success) {
                throw new Exception("Error executing SELECT INTO statement inside function: " + qr.message);
            }
            if (qr.rows.isEmpty()) {
                for (String varName : varNames) {
                    variables.put(varName, null);
                }
            } else {
                Map<String, Object> firstRow = qr.rows.get(0);
                List<Object> values = new ArrayList<>(firstRow.values());
                for (int i = 0; i < varNames.size(); i++) {
                    String varName = varNames.get(i);
                    Object val = (i < values.size()) ? values.get(i) : null;
                    variables.put(varName, val);
                }
            }
            return null;
        }
    }
}
