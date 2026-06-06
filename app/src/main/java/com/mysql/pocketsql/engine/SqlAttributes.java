package com.mysql.pocketsql.engine;

import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

public class SqlAttributes {
    public boolean unsigned = false;
    public boolean signed = false;
    public boolean zerofill = false;
    public int zerofillWidth = 0;
    public boolean binaryAttr = false;
    public boolean visible = true;
    public String generatedExpr = null;
    public String generatedType = null; // "VIRTUAL" or "STORED"
    
    public String referencesTable = null;
    public String referencesColumn = null;
    public String onDeleteAction = null; // "CASCADE", "SET NULL", "RESTRICT"
    public String onUpdateAction = null; // "CASCADE", "SET NULL", "RESTRICT"

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            if (unsigned) obj.put("unsigned", true);
            if (signed) obj.put("signed", true);
            if (zerofill) {
                obj.put("zerofill", true);
                obj.put("zerofillWidth", zerofillWidth);
            }
            if (binaryAttr) obj.put("binary", true);
            if (!visible) obj.put("visible", false);
            if (generatedExpr != null) {
                obj.put("generatedExpr", generatedExpr);
                obj.put("generatedType", generatedType);
            }
            if (referencesTable != null) {
                obj.put("referencesTable", referencesTable);
                obj.put("referencesColumn", referencesColumn);
                if (onDeleteAction != null) obj.put("onDeleteAction", onDeleteAction);
                if (onUpdateAction != null) obj.put("onUpdateAction", onUpdateAction);
            }
        } catch (Exception ignored) {}
        return obj;
    }

    public static SqlAttributes fromJsonObject(JSONObject obj) {
        SqlAttributes attrs = new SqlAttributes();
        if (obj == null) return attrs;
        attrs.unsigned = obj.optBoolean("unsigned", false);
        attrs.signed = obj.optBoolean("signed", false);
        attrs.zerofill = obj.optBoolean("zerofill", false);
        attrs.zerofillWidth = obj.optInt("zerofillWidth", 0);
        attrs.binaryAttr = obj.optBoolean("binary", false);
        attrs.visible = obj.optBoolean("visible", true);
        if (obj.has("generatedExpr")) {
            attrs.generatedExpr = obj.optString("generatedExpr");
            attrs.generatedType = obj.optString("generatedType", "VIRTUAL");
        }
        if (obj.has("referencesTable")) {
            attrs.referencesTable = obj.optString("referencesTable");
            attrs.referencesColumn = obj.optString("referencesColumn");
            attrs.onDeleteAction = obj.optString("onDeleteAction", null);
            attrs.onUpdateAction = obj.optString("onUpdateAction", null);
        }
        return attrs;
    }

    public static SqlAttributes parse(SqlParser parser) throws SqlSyntaxException {
        SqlAttributes attrs = new SqlAttributes();
        
        while (true) {
            SqlToken t = parser.peek();
            if (t.type != SqlToken.Type.KEYWORD && t.type != SqlToken.Type.IDENTIFIER) {
                break;
            }
            
            String val = t.value.toUpperCase();
            if ("UNSIGNED".equals(val)) {
                parser.consume();
                attrs.unsigned = true;
            } else if ("SIGNED".equals(val)) {
                parser.consume();
                attrs.signed = true;
            } else if ("ZEROFILL".equals(val)) {
                parser.consume();
                attrs.zerofill = true;
            } else if ("BINARY".equals(val)) {
                parser.consume();
                attrs.binaryAttr = true;
            } else if ("VISIBLE".equals(val)) {
                parser.consume();
                attrs.visible = true;
            } else if ("INVISIBLE".equals(val)) {
                parser.consume();
                attrs.visible = false;
            } else if ("GENERATED".equals(val)) {
                parser.consume();
                parser.expectKeyword("ALWAYS", "Expected 'ALWAYS' after 'GENERATED'");
                parser.expectKeyword("AS", "Expected 'AS' after 'ALWAYS'");
                parser.expectSymbol("(", "Expected '(' after 'AS'");
                
                StringBuilder exprBuilder = new StringBuilder();
                int depth = 1;
                while (depth > 0 && parser.peek().type != SqlToken.Type.EOF) {
                    SqlToken tk = parser.peek();
                    if (tk.type == SqlToken.Type.SYMBOL && tk.value.equals("(")) {
                        depth++;
                        exprBuilder.append("(");
                    } else if (tk.type == SqlToken.Type.SYMBOL && tk.value.equals(")")) {
                        depth--;
                        if (depth > 0) exprBuilder.append(")");
                    } else if (tk.type == SqlToken.Type.STRING) {
                        exprBuilder.append("'").append(tk.value).append("'");
                    } else {
                        exprBuilder.append(tk.value);
                    }
                    parser.consume();
                }
                attrs.generatedExpr = exprBuilder.toString();
                attrs.generatedType = "VIRTUAL"; // default
                
                SqlToken nextT = parser.peek();
                if (nextT.type == SqlToken.Type.KEYWORD || nextT.type == SqlToken.Type.IDENTIFIER) {
                    String nextVal = nextT.value.toUpperCase();
                    if ("VIRTUAL".equals(nextVal) || "STORED".equals(nextVal)) {
                        attrs.generatedType = nextVal;
                        parser.consume();
                    }
                }
            } else if ("REFERENCES".equals(val)) {
                parser.consume();
                attrs.referencesTable = parser.parseTableName("Expected parent table name after REFERENCES");
                
                parser.expectSymbol("(", "Expected '(' after parent table name");
                parser.expect(SqlToken.Type.IDENTIFIER, "Expected parent column name");
                attrs.referencesColumn = parser.tokens.get(parser.pos - 1).value;
                parser.expectSymbol(")", "Expected ')' after parent column name");
                
                // Parse optional ON DELETE or ON UPDATE action
                while (true) {
                    if (parser.matchKeyword("ON")) {
                        if (parser.matchKeyword("DELETE")) {
                            if (parser.matchKeyword("CASCADE")) {
                                attrs.onDeleteAction = "CASCADE";
                            } else if (parser.matchKeyword("SET")) {
                                parser.expectKeyword("NULL", "Expected 'NULL' after 'SET'");
                                attrs.onDeleteAction = "SET NULL";
                            } else if (parser.matchKeyword("RESTRICT") || parser.matchKeyword("NO")) {
                                if (parser.matchKeyword("ACTION")) {
                                    // skip
                                }
                                attrs.onDeleteAction = "RESTRICT";
                            }
                        } else if (parser.matchKeyword("UPDATE")) {
                            if (parser.matchKeyword("CASCADE")) {
                                attrs.onUpdateAction = "CASCADE";
                            } else if (parser.matchKeyword("SET")) {
                                parser.expectKeyword("NULL", "Expected 'NULL' after 'SET'");
                                attrs.onUpdateAction = "SET NULL";
                            } else if (parser.matchKeyword("RESTRICT") || parser.matchKeyword("NO")) {
                                if (parser.matchKeyword("ACTION")) {
                                    // skip
                                }
                                attrs.onUpdateAction = "RESTRICT";
                            }
                        }
                    } else {
                        break;
                    }
                }
            } else {
                break;
            }
        }
        return attrs;
    }

    public static void validateUnsigned(String colName, Object val) throws Exception {
        if (val == null) return;
        double numVal = 0;
        boolean isNumeric = false;
        if (val instanceof Number) {
            numVal = ((Number) val).doubleValue();
            isNumeric = true;
        } else {
            try {
                numVal = Double.parseDouble(val.toString());
                isNumeric = true;
            } catch (NumberFormatException ignored) {}
        }
        if (isNumeric && numVal < 0) {
            throw new Exception("Column '" + colName + "' is UNSIGNED and cannot be negative");
        }
    }

    public static void validateValue(String colName, Object val, SqlAttributes attrs) throws Exception {
        if (attrs == null || val == null) return;
        
        if (attrs.unsigned) {
            validateUnsigned(colName, val);
        }
    }

    public static Object formatValue(Object val, SqlAttributes attrs, String colType) {
        if (attrs == null || val == null) return val;
        if (attrs.zerofill) {
            int width = attrs.zerofillWidth > 0 ? attrs.zerofillWidth : getWidthFromType(colType);
            if (width > 0) {
                try {
                    long num = (val instanceof Number) ? ((Number) val).longValue() : Long.parseLong(val.toString().trim());
                    if (num >= 0) {
                        return String.format("%0" + width + "d", num);
                    }
                } catch (Exception ignored) {}
            }
        }
        return val;
    }

    public static int getWidthFromType(String type) {
        if (type == null) return 0;
        int open = type.indexOf('(');
        int close = type.indexOf(')');
        if (open != -1 && close != -1 && close > open + 1) {
            try {
                return Integer.parseInt(type.substring(open + 1, close).trim());
            } catch (NumberFormatException ignored) {}
        }
        // Defaults
        String upper = type.toUpperCase();
        if (upper.startsWith("TINYINT")) return 3;
        if (upper.startsWith("SMALLINT")) return 5;
        if (upper.startsWith("MEDIUMINT")) return 8;
        if (upper.startsWith("INT") || upper.startsWith("INTEGER")) return 10;
        if (upper.startsWith("BIGINT")) return 20;
        return 0;
    }
}
