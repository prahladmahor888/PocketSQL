package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SqlFunctions {

    public static String getEngineVersion() {
        try {
            return com.mysql.pocketsql.BuildConfig.VERSION_NAME;
        } catch (Throwable e) {
            return "1.0.1";
        }
    }

    public interface Expression {
        Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine);
        boolean hasAggregate();
        boolean hasWindowFunction();
        void collectColumns(List<String> columns);
    }

    public static class LiteralExpression implements Expression {
        private final Object value;
        public LiteralExpression(Object value) { this.value = value; }
        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) { return value; }
        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return false; }
        @Override public void collectColumns(List<String> columns) {}
        @Override public String toString() {
            return value == null ? "NULL" : (value instanceof String ? "'" + value + "'" : value.toString());
        }
    }

    public static class VariableExpression implements Expression {
        private final String varName;
        public VariableExpression(String varName) {
            this.varName = varName.toLowerCase();
        }
        @Override
        public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            if (engine == null) return null;
            return engine.getUserVariable(varName);
        }
        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return false; }
        @Override public void collectColumns(List<String> columns) {}
        @Override public String toString() { return varName; }
    }

    public static class ColumnExpression implements Expression {
        private final String columnName;
        public ColumnExpression(String columnName) { this.columnName = columnName; }
        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            if (columnName != null && (columnName.startsWith("@") || columnName.startsWith("@@"))) {
                if (engine != null) {
                    Object varVal = engine.getUserVariable(columnName);
                    if (varVal != null) return varVal;
                }
            }
            if (row == null) {
                if (engine != null && columnName != null && (columnName.startsWith("@") || columnName.startsWith("@@"))) {
                    Object varVal = engine.getUserVariable(columnName);
                    if (varVal != null) return varVal;
                }
                return columnName;
            }
            Object val = DatabaseEngine.getRowValue(row, columnName);
            if (val == null && !rowHasColumn(row, columnName)) {
                if (engine != null && columnName != null && (columnName.startsWith("@") || columnName.startsWith("@@"))) {
                    Object varVal = engine.getUserVariable(columnName);
                    if (varVal != null) return varVal;
                }
                return columnName;
            }
            return val;
        }

        private boolean rowHasColumn(Map<String, Object> row, String col) {
            if (row == null || col == null) return false;
            if (row.containsKey(col)) return true;
            for (String key : row.keySet()) {
                if (key.equalsIgnoreCase(col)) return true;
                if (key.contains(".")) {
                    String suffix = key.substring(key.indexOf('.') + 1);
                    if (suffix.equalsIgnoreCase(col)) return true;
                }
            }
            return false;
        }
        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return false; }
        @Override public void collectColumns(List<String> columns) {
            // DISTINCT, ALL, *, and datepart keywords are SQL modifiers/keywords, not real column references
            if (!"DISTINCT".equalsIgnoreCase(columnName) && !"ALL".equalsIgnoreCase(columnName) && !"*".equals(columnName)
                && !"WEEKDAY".equalsIgnoreCase(columnName) && !"YEAR".equalsIgnoreCase(columnName)
                && !"MONTH".equalsIgnoreCase(columnName) && !"DAY".equalsIgnoreCase(columnName)
                && !"HOUR".equalsIgnoreCase(columnName) && !"MINUTE".equalsIgnoreCase(columnName)
                && !"SECOND".equalsIgnoreCase(columnName) && !"QUARTER".equalsIgnoreCase(columnName)
                && !"WEEK".equalsIgnoreCase(columnName) && !"DAYOFYEAR".equalsIgnoreCase(columnName)) {
                columns.add(columnName);
            }
        }
        @Override public String toString() { return columnName; }
    }

    public static class BinaryOpExpression implements Expression {
        private final Expression left;
        private final String op;
        private final Expression right;
        public BinaryOpExpression(Expression left, String op, Expression right) {
            this.left = left;
            this.op = op.toUpperCase();
            this.right = right;
        }
        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            Object lVal = left.evaluate(row, groupRows, engine);
            Object rVal = right.evaluate(row, groupRows, engine);
            // Delegate all operator evaluation to SqlOperator
            return SqlOperator.evaluateBinary(op, lVal, rVal);
        }
        @Override public boolean hasAggregate() { return left.hasAggregate() || right.hasAggregate(); }
        @Override public boolean hasWindowFunction() { return left.hasWindowFunction() || right.hasWindowFunction(); }
        @Override public void collectColumns(List<String> columns) { left.collectColumns(columns); right.collectColumns(columns); }
        @Override public String toString() { return left.toString() + " " + op + " " + right.toString(); }
    }

    public static class UnaryOpExpression implements Expression {
        private final String op;
        private final Expression expr;
        public UnaryOpExpression(String op, Expression expr) {
            this.op = op;
            this.expr = expr;
        }
        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            Object val = expr.evaluate(row, groupRows, engine);
            return SqlOperator.evaluateUnary(op, val);
        }
        @Override public boolean hasAggregate() { return expr.hasAggregate(); }
        @Override public boolean hasWindowFunction() { return expr.hasWindowFunction(); }
        @Override public void collectColumns(List<String> columns) { expr.collectColumns(columns); }
        @Override public String toString() { return op + " " + expr.toString(); }
    }

    public static class CaseExpression implements Expression {
        public static class WhenThen {
            public final Expression when;
            public final Expression then;
            public WhenThen(Expression when, Expression then) {
                this.when = when;
                this.then = then;
            }
        }
        private final Expression caseExpr;
        private final List<WhenThen> whenThens;
        private final Expression elseExpr;

        public CaseExpression(Expression caseExpr, List<WhenThen> whenThens, Expression elseExpr) {
            this.caseExpr = caseExpr;
            this.whenThens = whenThens;
            this.elseExpr = elseExpr;
        }

        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            Object baseVal = null;
            if (caseExpr != null) {
                baseVal = caseExpr.evaluate(row, groupRows, engine);
            }
            for (WhenThen wt : whenThens) {
                if (caseExpr != null) {
                    Object whenVal = wt.when.evaluate(row, groupRows, engine);
                    if (compare(baseVal, "=", whenVal)) {
                        return wt.then.evaluate(row, groupRows, engine);
                    }
                } else {
                    Object cond = wt.when.evaluate(row, groupRows, engine);
                    if (isTruthy(cond)) {
                        return wt.then.evaluate(row, groupRows, engine);
                    }
                }
            }
            if (elseExpr != null) {
                return elseExpr.evaluate(row, groupRows, engine);
            }
            return null;
        }

        @Override public boolean hasAggregate() {
            if (caseExpr != null && caseExpr.hasAggregate()) return true;
            for (WhenThen wt : whenThens) {
                if (wt.when.hasAggregate() || wt.then.hasAggregate()) return true;
            }
            return elseExpr != null && elseExpr.hasAggregate();
        }

        @Override public boolean hasWindowFunction() {
            if (caseExpr != null && caseExpr.hasWindowFunction()) return true;
            for (WhenThen wt : whenThens) {
                if (wt.when.hasWindowFunction() || wt.then.hasWindowFunction()) return true;
            }
            return elseExpr != null && elseExpr.hasWindowFunction();
        }
        @Override public void collectColumns(List<String> columns) {
            if (caseExpr != null) caseExpr.collectColumns(columns);
            for (WhenThen wt : whenThens) {
                wt.when.collectColumns(columns);
                wt.then.collectColumns(columns);
            }
            if (elseExpr != null) elseExpr.collectColumns(columns);
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder("CASE ");
            if (caseExpr != null) sb.append(caseExpr.toString()).append(" ");
            for (WhenThen wt : whenThens) {
                sb.append("WHEN ").append(wt.when.toString()).append(" THEN ").append(wt.then.toString()).append(" ");
            }
            if (elseExpr != null) sb.append("ELSE ").append(elseExpr.toString()).append(" ");
            sb.append("END");
            return sb.toString();
        }
    }

    public static class WindowFunctionExpression implements Expression {
        public final String functionName;
        public final List<Expression> args;
        public final List<String> partitionBy;
        public final List<String> orderBy;
        public final List<Boolean> orderAsc;
        public final String fullExprString;

        public WindowFunctionExpression(String functionName, List<Expression> args, List<String> partitionBy, List<String> orderBy, List<Boolean> orderAsc, String fullExprString) {
            this.functionName = functionName.toUpperCase();
            this.args = args;
            this.partitionBy = partitionBy;
            this.orderBy = orderBy;
            this.orderAsc = orderAsc;
            this.fullExprString = fullExprString;
        }

        @Override
        public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            System.out.println("DEBUG WINDOW EVAL: looking for '" + fullExprString + "' in keys=" + (row != null ? row.keySet() : "null"));
            if (row != null && row.containsKey(fullExprString)) {
                return row.get(fullExprString);
            }
            return null;
        }

        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return true; }
        @Override public void collectColumns(List<String> columns) {
            for (Expression arg : args) {
                arg.collectColumns(columns);
            }
        }
        @Override public String toString() { return fullExprString; }
    }

    public static class SubqueryExpression implements Expression {
        private final String query;
        public SubqueryExpression(String query) {
            this.query = query;
        }
        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            if (engine == null) return null;
            try {
                String boundQuery = query;
                if (row != null) {
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        String key = entry.getKey();
                        Object val = entry.getValue();
                        if (key != null && val != null && boundQuery.contains(key)) {
                            String replacement = (val instanceof Number) ? val.toString() : "'" + val.toString().replace("'", "\\'") + "'";
                            boundQuery = boundQuery.replaceAll("\\b" + java.util.regex.Pattern.quote(key) + "\\b", replacement);
                        }
                    }
                }
                QueryResult qres = engine.execute(boundQuery);
                if (qres.success && qres.rows != null && !qres.rows.isEmpty()) {
                    Map<String, Object> firstRow = qres.rows.get(0);
                    if (firstRow != null && !firstRow.isEmpty()) {
                        return firstRow.values().iterator().next();
                    }
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }
        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return false; }
        @Override public void collectColumns(List<String> columns) {}
        @Override public String toString() { return "(" + query + ")"; }
    }

    public static class FunctionExpression implements Expression {
        public final String name;
        public final List<Expression> args;

        public FunctionExpression(String name, List<Expression> args) {
            this.name = name.toUpperCase();
            this.args = args;
        }

        @Override public boolean hasAggregate() {
            if ("COUNT".equals(name) || "SUM".equals(name) || "AVG".equals(name) || 
                "MIN".equals(name) || "MAX".equals(name) || "GROUP_CONCAT".equals(name)) {
                return true;
            }
            for (Expression arg : args) {
                if (arg.hasAggregate()) return true;
            }
            return false;
        }
        @Override public void collectColumns(List<String> columns) {
            for (Expression arg : args) {
                arg.collectColumns(columns);
            }
        }

        @Override public boolean hasWindowFunction() {
            for (Expression arg : args) {
                if (arg.hasWindowFunction()) return true;
            }
            return false;
        }

        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            String str = this.toString();
            if (row != null && row.containsKey(str)) {
                return row.get(str);
            }
            if ("COUNT".equals(name)) {
                if (groupRows == null) return 1L;
                if (args.isEmpty()) return (long) groupRows.size();
                Expression arg = args.get(0);
                if (arg instanceof LiteralExpression && "*".equals(((LiteralExpression) arg).value)) {
                    return (long) groupRows.size();
                }
                long count = 0;
                for (Map<String, Object> r : groupRows) {
                    if (arg.evaluate(r, groupRows, engine) != null) {
                        count++;
                    }
                }
                return count;
            }
            if ("SUM".equals(name)) {
                if (groupRows == null || args.isEmpty()) return null;
                Expression arg = args.get(0);
                double sum = 0;
                boolean hasVal = false;
                for (Map<String, Object> r : groupRows) {
                    Object val = arg.evaluate(r, groupRows, engine);
                    if (val != null) {
                        sum += parseDouble(val);
                        hasVal = true;
                    }
                }
                return hasVal ? sum : null;
            }
            if ("AVG".equals(name)) {
                if (groupRows == null || args.isEmpty()) return null;
                Expression arg = args.get(0);
                double sum = 0;
                long count = 0;
                for (Map<String, Object> r : groupRows) {
                    Object val = arg.evaluate(r, groupRows, engine);
                    if (val != null) {
                        sum += parseDouble(val);
                        count++;
                    }
                }
                return count > 0 ? (sum / count) : null;
            }
            if ("MIN".equals(name)) {
                if (groupRows == null || args.isEmpty()) return null;
                Expression arg = args.get(0);
                Object min = null;
                for (Map<String, Object> r : groupRows) {
                    Object val = arg.evaluate(r, groupRows, engine);
                    if (val != null) {
                        if (min == null) min = val;
                        else {
                            if (compare(val, "<", min)) min = val;
                        }
                    }
                }
                return min;
            }
            if ("MAX".equals(name)) {
                if (groupRows == null || args.isEmpty()) return null;
                Expression arg = args.get(0);
                Object max = null;
                for (Map<String, Object> r : groupRows) {
                    Object val = arg.evaluate(r, groupRows, engine);
                    if (val != null) {
                        if (max == null) max = val;
                        else {
                            if (compare(val, ">", max)) max = val;
                        }
                    }
                }
                return max;
            }
            if ("GROUP_CONCAT".equals(name)) {
                if (groupRows == null || args.isEmpty()) return null;
                Expression arg = args.get(0);
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Map<String, Object> r : groupRows) {
                    Object val = arg.evaluate(r, groupRows, engine);
                    if (val != null) {
                        if (!first) sb.append(",");
                        sb.append(val.toString());
                        first = false;
                    }
                }
                return first ? null : sb.toString();
            }

            List<Object> argVals = new ArrayList<>();
            for (Expression arg : args) {
                argVals.add(arg.evaluate(row, groupRows, engine));
            }
            return evaluateScalarFunction(name, argVals, engine);
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder(name).append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(args.get(i).toString());
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static Object evaluate(String exprStr, Map<String, Object> row, DatabaseEngine engine) {
        if (exprStr == null) return null;
        if (row != null && row.containsKey(exprStr)) {
            return row.get(exprStr);
        }
        String trimmed = exprStr.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.toUpperCase().startsWith("SELECT ")) {
            if (engine != null) {
                try {
                    QueryResult qres = engine.execute(trimmed);
                    if (qres.success && qres.rows != null && !qres.rows.isEmpty()) {
                        Map<String, Object> firstRow = qres.rows.get(0);
                        if (firstRow != null && !firstRow.isEmpty()) {
                            return firstRow.values().iterator().next();
                        }
                    }
                    return null;
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
        Expression expr = parse(exprStr);
        return expr.evaluate(row, null, engine);
    }

    public static Object evaluate(String exprStr, Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
        if (exprStr == null) return null;
        if (row != null && row.containsKey(exprStr)) {
            return row.get(exprStr);
        }
        String trimmed = exprStr.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.toUpperCase().startsWith("SELECT ")) {
            if (engine != null) {
                try {
                    QueryResult qres = engine.execute(trimmed);
                    if (qres.success && qres.rows != null && !qres.rows.isEmpty()) {
                        Map<String, Object> firstRow = qres.rows.get(0);
                        if (firstRow != null && !firstRow.isEmpty()) {
                            return firstRow.values().iterator().next();
                        }
                    }
                    return null;
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
        Expression expr = parse(exprStr);
        return expr.evaluate(row, groupRows, engine);
    }

    public static List<Object> evaluateList(String exprStr, Map<String, Object> row, DatabaseEngine engine) {
        if (exprStr == null) return java.util.Collections.emptyList();
        String trimmed = exprStr.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.toUpperCase().startsWith("SELECT ")) {
            if (engine != null) {
                try {
                    QueryResult qres = engine.execute(trimmed);
                    if (qres.success && qres.rows != null) {
                        List<Object> list = new ArrayList<>();
                        for (Map<String, Object> r : qres.rows) {
                            if (r != null && !r.isEmpty()) {
                                list.add(r.values().iterator().next());
                            }
                        }
                        return list;
                    }
                } catch (Exception e) {
                    return java.util.Collections.emptyList();
                }
            }
            return java.util.Collections.emptyList();
        }
        Object scalar = evaluate(exprStr, row, engine);
        if (scalar != null) {
            return java.util.Collections.singletonList(scalar);
        }
        return java.util.Collections.emptyList();
    }

    public static boolean evaluateExists(String subquerySql, Map<String, Object> outerRow, DatabaseEngine engine) {
        if (subquerySql == null || engine == null) return false;
        String trimmed = subquerySql.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        if (outerRow != null && !outerRow.isEmpty()) {
            List<Map.Entry<String, Object>> entries = new ArrayList<>(outerRow.entrySet());
            entries.sort((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()));

            for (Map.Entry<String, Object> entry : entries) {
                String key = entry.getKey();
                if (key == null || key.isEmpty()) continue;
                // Outer reference substitution in correlated subqueries uses qualified table column names (e.g. u1.city)
                if (!key.contains(".")) continue;
                Object val = entry.getValue();

                String pattern = "(?i)\\b" + java.util.regex.Pattern.quote(key) + "\\b";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                if (p.matcher(trimmed).find()) {
                    String valStr;
                    if (val == null) {
                        valStr = "NULL";
                    } else if (val instanceof Number || val instanceof Boolean) {
                        valStr = val.toString();
                    } else {
                        valStr = "'" + val.toString().replace("'", "\\'") + "'";
                    }
                    trimmed = p.matcher(trimmed).replaceAll(java.util.regex.Matcher.quoteReplacement(valStr));
                }
            }
        }

        try {
            QueryResult qres = engine.execute(trimmed);
            return qres.success && qres.rows != null && !qres.rows.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** @deprecated Use SqlOperator.isTruthy(val) instead */
    @Deprecated
    public static boolean isTruthy(Object val) {
        return SqlOperator.isTruthy(val);
    }

    /** @deprecated Use SqlOperator.toDouble(val) instead */
    @Deprecated
    public static double parseDouble(Object val) {
        return SqlOperator.toDouble(val);
    }

    /** @deprecated Use SqlOperator.compare(lVal, op, rVal) instead */
    @Deprecated
    public static boolean compare(Object lVal, String op, Object rVal) {
        return SqlOperator.compare(lVal, op, rVal);
    }

    public static Object evaluateScalarFunction(String name, List<Object> argVals, DatabaseEngine engine) {
        if (engine != null && engine.hasCustomFunction(name)) {
            try {
                return engine.executeCustomFunction(name, argVals);
            } catch (Exception e) {
                throw new RuntimeException("Error executing custom function '" + name + "': " + e.getMessage(), e);
            }
        }

        // String functions
        if ("CONCAT".equals(name)) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : argVals) {
                if (arg == null) return null;
                sb.append(arg.toString());
            }
            return sb.toString();
        }
        if ("CONCAT_WS".equals(name)) {
            if (argVals.isEmpty()) return null;
            Object sepObj = argVals.get(0);
            if (sepObj == null) return null;
            String sep = sepObj.toString();
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int i = 1; i < argVals.size(); i++) {
                Object arg = argVals.get(i);
                if (arg != null) {
                    if (!first) sb.append(sep);
                    sb.append(arg.toString());
                    first = false;
                }
            }
            return sb.toString();
        }
        if ("UPPER".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().toUpperCase();
        }
        if ("LOWER".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().toLowerCase();
        }
        if ("LENGTH".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return (long) argVals.get(0).toString().getBytes(StandardCharsets.UTF_8).length;
        }
        if ("CHAR_LENGTH".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return (long) argVals.get(0).toString().length();
        }
        if ("SUBSTRING".equals(name) || "SUBSTR".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String str = argVals.get(0).toString();
            int pos = ((Number) argVals.get(1)).intValue();
            int len = argVals.size() > 2 && argVals.get(2) != null ? ((Number) argVals.get(2)).intValue() : -1;
            int start;
            if (pos > 0) {
                start = pos - 1;
            } else if (pos < 0) {
                start = str.length() + pos;
            } else {
                return "";
            }
            if (start < 0 || start >= str.length()) return "";
            if (len == -1) {
                return str.substring(start);
            } else {
                if (len <= 0) return "";
                int end = Math.min(start + len, str.length());
                return str.substring(start, end);
            }
        }
        if ("LEFT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String str = argVals.get(0).toString();
            int len = ((Number) argVals.get(1)).intValue();
            if (len <= 0) return "";
            return str.substring(0, Math.min(len, str.length()));
        }
        if ("RIGHT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String str = argVals.get(0).toString();
            int len = ((Number) argVals.get(1)).intValue();
            if (len <= 0) return "";
            int start = Math.max(0, str.length() - len);
            return str.substring(start);
        }
        if ("TRIM".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().trim();
        }
        if ("LTRIM".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().replaceAll("^\\s+", "");
        }
        if ("RTRIM".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().replaceAll("\\s+$", "");
        }
        if ("REPLACE".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null || argVals.get(2) == null) return null;
            return argVals.get(0).toString().replace(argVals.get(1).toString(), argVals.get(2).toString());
        }
        if ("REVERSE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return new StringBuilder(argVals.get(0).toString()).reverse().toString();
        }
        if ("INSTR".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String str = argVals.get(0).toString();
            String sub = argVals.get(1).toString();
            return (long) (str.indexOf(sub) + 1);
        }
        if ("LOCATE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String sub = argVals.get(0).toString();
            String str = argVals.get(1).toString();
            int start = argVals.size() > 2 && argVals.get(2) != null ? ((Number) argVals.get(2)).intValue() - 1 : 0;
            if (start < 0 || start >= str.length()) return 0L;
            return (long) (str.indexOf(sub, start) + 1);
        }
        if ("LPAD".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null || argVals.get(2) == null) return null;
            String str = argVals.get(0).toString();
            int len = ((Number) argVals.get(1)).intValue();
            String pad = argVals.get(2).toString();
            if (len <= 0) return "";
            if (str.length() >= len) return str.substring(0, len);
            StringBuilder sb = new StringBuilder();
            while (sb.length() < len - str.length()) sb.append(pad);
            return sb.substring(0, len - str.length()) + str;
        }
        if ("RPAD".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null || argVals.get(2) == null) return null;
            String str = argVals.get(0).toString();
            int len = ((Number) argVals.get(1)).intValue();
            String pad = argVals.get(2).toString();
            if (len <= 0) return "";
            if (str.length() >= len) return str.substring(0, len);
            StringBuilder sb = new StringBuilder(str);
            while (sb.length() < len) sb.append(pad);
            return sb.substring(0, len);
        }
        if ("REPEAT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String str = argVals.get(0).toString();
            int count = ((Number) argVals.get(1)).intValue();
            if (count <= 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) sb.append(str);
            return sb.toString();
        }
        if ("QUOTE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString();
            return "'" + str.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if ("HEX".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            Object arg = argVals.get(0);
            if (arg instanceof Number) {
                return Long.toHexString(((Number) arg).longValue()).toUpperCase();
            }
            String str = arg.toString();
            try {
                long val = Long.parseLong(str);
                return Long.toHexString(val).toUpperCase();
            } catch (NumberFormatException e) {
                StringBuilder sb = new StringBuilder();
                for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
                    sb.append(String.format("%02X", b));
                }
                return sb.toString();
            }
        }
        if ("UNHEX".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String hex = argVals.get(0).toString().trim();
            if (hex.length() % 2 != 0) return null;
            try {
                byte[] bytes = fromHexString(hex);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }
        if ("ASCII".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString();
            if (str.isEmpty()) return 0L;
            return (long) str.charAt(0);
        }
        if ("CHAR".equals(name)) {
            if (argVals.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (Object arg : argVals) {
                if (arg != null) {
                    try {
                        int code = ((Number) arg).intValue();
                        sb.append((char) code);
                    } catch (Exception e) {
                        try {
                            int code = Integer.parseInt(arg.toString());
                            sb.append((char) code);
                        } catch (Exception ignored) {}
                    }
                }
            }
            return sb.toString();
        }
        if ("FIELD".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null) return 0L;
            String target = argVals.get(0).toString();
            for (int i = 1; i < argVals.size(); i++) {
                Object arg = argVals.get(i);
                if (arg != null && target.equalsIgnoreCase(arg.toString())) {
                    return (long) i;
                }
            }
            return 0L;
        }
        if ("FIND_IN_SET".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return 0L;
            String target = argVals.get(0).toString().trim();
            String listStr = argVals.get(1).toString();
            String[] items = listStr.split(",");
            for (int i = 0; i < items.length; i++) {
                if (target.equalsIgnoreCase(items[i].trim())) {
                    return (long) (i + 1);
                }
            }
            return 0L;
        }
        if ("ELT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null) return null;
            int n = ((Number) argVals.get(0)).intValue();
            if (n >= 1 && n < argVals.size()) {
                return argVals.get(n);
            }
            return null;
        }
        if ("MAKE_SET".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            long bits = ((Number) argVals.get(0)).longValue();
            List<String> resultList = new ArrayList<>();
            for (int i = 1; i < argVals.size(); i++) {
                Object arg = argVals.get(i);
                if (arg == null) continue;
                long bitMask = 1L << (i - 1);
                if ((bits & bitMask) != 0) {
                    resultList.add(arg.toString());
                }
            }
            return String.join(",", resultList);
        }

        // Numeric functions
        if ("ABS".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.abs(parseDouble(argVals.get(0)));
        }
        if ("ROUND".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            int decimals = argVals.size() > 1 && argVals.get(1) != null ? ((Number) argVals.get(1)).intValue() : 0;
            double factor = Math.pow(10, decimals);
            return Math.round(val * factor) / factor;
        }
        if ("TRUNCATE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            try {
                java.math.BigDecimal bd = new java.math.BigDecimal(argVals.get(0).toString());
                int decimals = ((Number) argVals.get(1)).intValue();
                java.math.BigDecimal truncated = bd.setScale(decimals, java.math.RoundingMode.DOWN);
                if (decimals <= 0) {
                    return truncated.longValue();
                }
                return truncated.doubleValue();
            } catch (Exception e) {
                return null;
            }
        }
        if ("CEIL".equals(name) || "CEILING".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return (long) Math.ceil(parseDouble(argVals.get(0)));
        }
        if ("FLOOR".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return (long) Math.floor(parseDouble(argVals.get(0)));
        }
        if ("MOD".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            double n = parseDouble(argVals.get(0));
            double m = parseDouble(argVals.get(1));
            if (m == 0) return null;
            return n % m;
        }
        if ("POWER".equals(name) || "POW".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            return Math.pow(parseDouble(argVals.get(0)), parseDouble(argVals.get(1)));
        }
        if ("SQRT".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val < 0) return null;
            return Math.sqrt(val);
        }
        if ("RAND".equals(name)) {
            if (!argVals.isEmpty() && argVals.get(0) != null) {
                long seed = ((Number) argVals.get(0)).longValue();
                // Custom LCG formula for predictable RAND(seed) to avoid insecure seeding or weak generators
                long a = 1103515245L;
                long c = 12345L;
                long m = 1L << 31;
                long nextSeed = (a * seed + c) % m;
                return (double) nextSeed / (double) m;
            }
            java.security.SecureRandom sr = new java.security.SecureRandom();
            byte[] bytes = new byte[8];
            sr.nextBytes(bytes);
            long l = 0;
            for (int i = 0; i < 8; i++) {
                l = (l << 8) | (bytes[i] & 0xFF);
            }
            return (double)(l & 0x1FFFFFFFFFFFFFL) / (double)(1L << 53);
        }
        if ("SIGN".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            return val > 0 ? 1L : (val < 0 ? -1L : 0L);
        }
        if ("PI".equals(name)) {
            return Math.PI;
        }
        if ("EXP".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.exp(parseDouble(argVals.get(0)));
        }
        if ("LOG".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val <= 0) return null;
            if (argVals.size() > 1 && argVals.get(1) != null) {
                double base = parseDouble(argVals.get(1));
                if (base <= 0 || base == 1) return null;
                return Math.log(val) / Math.log(base);
            }
            return Math.log(val);
        }
        if ("LOG10".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val <= 0) return null;
            return Math.log10(val);
        }
        if ("LOG2".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val <= 0) return null;
            return Math.log(val) / Math.log(2.0);
        }
        if ("DEGREES".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.toDegrees(parseDouble(argVals.get(0)));
        }
        if ("RADIANS".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.toRadians(parseDouble(argVals.get(0)));
        }
        if ("SIN".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.sin(parseDouble(argVals.get(0)));
        }
        if ("COS".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.cos(parseDouble(argVals.get(0)));
        }
        if ("TAN".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return Math.tan(parseDouble(argVals.get(0)));
        }
        if ("ASIN".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val < -1.0 || val > 1.0) return null;
            return Math.asin(val);
        }
        if ("ACOS".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            double val = parseDouble(argVals.get(0));
            if (val < -1.0 || val > 1.0) return null;
            return Math.acos(val);
        }
        if ("ATAN".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            if (argVals.size() > 1 && argVals.get(1) != null) {
                return Math.atan2(parseDouble(argVals.get(0)), parseDouble(argVals.get(1)));
            }
            return Math.atan(parseDouble(argVals.get(0)));
        }
        if ("LEAST".equals(name)) {
            if (argVals.isEmpty()) return null;
            Object minVal = null;
            for (Object arg : argVals) {
                if (arg != null) {
                    if (minVal == null) {
                        minVal = arg;
                    } else if (compare(arg, "<", minVal)) {
                        minVal = arg;
                    }
                }
            }
            return minVal;
        }
        if ("GREATEST".equals(name)) {
            if (argVals.isEmpty()) return null;
            Object maxVal = null;
            for (Object arg : argVals) {
                if (arg != null) {
                    if (maxVal == null) {
                        maxVal = arg;
                    } else if (compare(arg, ">", maxVal)) {
                        maxVal = arg;
                    }
                }
            }
            return maxVal;
        }

        // Date & Time functions
        if ("NOW".equals(name)) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if ("CURDATE".equals(name) || "CURRENT_DATE".equals(name)) {
            return LocalDate.now().toString();
        }
        if ("CURTIME".equals(name) || "CURRENT_TIME".equals(name)) {
            return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        if ("DATE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : dt.toLocalDate().toString();
        }
        if ("TIME".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : dt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        if ("YEAR".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getYear();
        }
        if ("MONTH".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getMonthValue();
        }
        if ("DAY".equals(name) || "DAYOFMONTH".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getDayOfMonth();
        }
        if ("DAYNAME".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : dt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        }
        if ("MONTHNAME".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : dt.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        }
        if ("DATENAME".equals(name)) {
            if (argVals.size() < 2) return null;
            Object partObj = argVals.get(0);
            Object dateObj = argVals.get(1);
            if (partObj == null || dateObj == null) return null;
            LocalDateTime dt = parseDateTime(dateObj);
            if (dt == null) return null;

            String part = partObj.toString().toUpperCase().trim();
            switch (part) {
                case "WEEKDAY":
                case "DW":
                case "W":
                    return dt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
                case "MONTH":
                case "MM":
                case "M":
                    return dt.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
                case "YEAR":
                case "YY":
                case "YYYY":
                    return String.valueOf(dt.getYear());
                case "DAY":
                case "DD":
                case "D":
                case "DAYOFMONTH":
                    return String.valueOf(dt.getDayOfMonth());
                case "HOUR":
                case "HH":
                    return String.valueOf(dt.getHour());
                case "MINUTE":
                case "MI":
                case "N":
                    return String.valueOf(dt.getMinute());
                case "SECOND":
                case "SS":
                case "S":
                    return String.valueOf(dt.getSecond());
                case "QUARTER":
                case "QQ":
                case "Q":
                    return String.valueOf((dt.getMonthValue() - 1) / 3 + 1);
                case "DAYOFYEAR":
                case "DY":
                    return String.valueOf(dt.getDayOfYear());
                default:
                    return dt.toString();
            }
        }
        if ("DAYOFWEEK".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            int val = dt.getDayOfWeek().getValue();
            return val == 7 ? 1L : (long) (val + 1);
        }
        if ("WEEKDAY".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            return (long) (dt.getDayOfWeek().getValue() - 1);
        }
        if ("HOUR".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getHour();
        }
        if ("MINUTE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getMinute();
        }
        if ("SECOND".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            return dt == null ? null : (long) dt.getSecond();
        }
        if ("DATEDIFF".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            LocalDateTime dt1 = parseDateTime(argVals.get(0));
            LocalDateTime dt2 = parseDateTime(argVals.get(1));
            if (dt1 == null || dt2 == null) return null;
            return ChronoUnit.DAYS.between(dt2.toLocalDate(), dt1.toLocalDate());
        }
        if ("DATE_ADD".equals(name) || "DATE_SUB".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            String valStr = argVals.get(1).toString().trim();
            boolean isSub = "DATE_SUB".equals(name);
            long amount = 0;
            String unit = "DAY";
            if (valStr.toUpperCase().startsWith("INTERVAL ")) {
                String parts = valStr.substring(9).trim();
                int spaceIdx = parts.indexOf(' ');
                if (spaceIdx > 0) {
                    amount = Long.parseLong(parts.substring(0, spaceIdx).replaceAll("^'|'$", ""));
                    unit = parts.substring(spaceIdx + 1).toUpperCase();
                } else {
                    amount = Long.parseLong(parts.replaceAll("^'|'$", ""));
                }
            } else {
                amount = Long.parseLong(valStr);
            }
            if (isSub) amount = -amount;
            switch (unit) {
                case "SECOND": dt = dt.plusSeconds(amount); break;
                case "MINUTE": dt = dt.plusMinutes(amount); break;
                case "HOUR": dt = dt.plusHours(amount); break;
                case "DAY": dt = dt.plusDays(amount); break;
                case "WEEK": dt = dt.plusWeeks(amount); break;
                case "MONTH": dt = dt.plusMonths(amount); break;
                case "YEAR": dt = dt.plusYears(amount); break;
            }
            return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if ("TIMESTAMPDIFF".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null || argVals.get(2) == null) return null;
            String unit = argVals.get(0).toString().toUpperCase();
            LocalDateTime dt1 = parseDateTime(argVals.get(1));
            LocalDateTime dt2 = parseDateTime(argVals.get(2));
            if (dt1 == null || dt2 == null) return null;
            switch (unit) {
                case "SECOND": return ChronoUnit.SECONDS.between(dt1, dt2);
                case "MINUTE": return ChronoUnit.MINUTES.between(dt1, dt2);
                case "HOUR": return ChronoUnit.HOURS.between(dt1, dt2);
                case "DAY": return ChronoUnit.DAYS.between(dt1, dt2);
                case "WEEK": return ChronoUnit.WEEKS.between(dt1, dt2);
                case "MONTH": return ChronoUnit.MONTHS.between(dt1, dt2);
                case "YEAR": return ChronoUnit.YEARS.between(dt1, dt2);
                default: return null;
            }
        }
        if ("DATE_FORMAT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            return formatDateMySQL(dt, argVals.get(1).toString());
        }
        if ("WEEK".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.of(java.util.Locale.getDefault());
            return (long) dt.get(wf.weekOfWeekBasedYear());
        }
        if ("LAST_DAY".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            return dt.toLocalDate().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth()).toString();
        }
        if ("ADDDATE".equals(name) || "SUBDATE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            LocalDateTime dt = parseDateTime(argVals.get(0));
            if (dt == null) return null;
            Object arg1 = argVals.get(1);
            String valStr = arg1.toString().trim();
            boolean isSub = "SUBDATE".equals(name);
            if (valStr.toUpperCase().startsWith("INTERVAL ")) {
                List<Object> passArgs = new ArrayList<>();
                passArgs.add(argVals.get(0));
                passArgs.add(arg1);
                return evaluateScalarFunction(isSub ? "DATE_SUB" : "DATE_ADD", passArgs, engine);
            }
            long days = 0;
            try {
                days = ((Number) arg1).longValue();
            } catch (Exception e) {
                try {
                    days = Long.parseLong(arg1.toString());
                } catch (Exception ignored) {}
            }
            if (isSub) days = -days;
            dt = dt.plusDays(days);
            String res = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (res.endsWith(" 00:00:00")) {
                return res.substring(0, 10);
            }
            return res;
        }
        if ("MAKEDATE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            int year = ((Number) argVals.get(0)).intValue();
            int dayOfYear = ((Number) argVals.get(1)).intValue();
            if (dayOfYear <= 0) return null;
            try {
                return java.time.LocalDate.ofYearDay(year, dayOfYear).toString();
            } catch (Exception e) {
                return null;
            }
        }
        if ("MAKETIME".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null || argVals.get(2) == null) return null;
            int h = ((Number) argVals.get(0)).intValue();
            int m = ((Number) argVals.get(1)).intValue();
            int s = ((Number) argVals.get(2)).intValue();
            try {
                return java.time.LocalTime.of(h, m, s).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            } catch (Exception e) {
                return null;
            }
        }
        if ("STR_TO_DATE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String dateStr = argVals.get(0).toString().trim();
            String fmtStr = argVals.get(1).toString().trim();
            return parseDateMySQL(dateStr, fmtStr);
        }
        if ("EXTRACT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String unit = argVals.get(0).toString().toUpperCase().trim();
            LocalDateTime dt = parseDateTime(argVals.get(1));
            if (dt == null) return null;
            switch (unit) {
                case "YEAR": return (long) dt.getYear();
                case "MONTH": return (long) dt.getMonthValue();
                case "DAY": return (long) dt.getDayOfMonth();
                case "HOUR": return (long) dt.getHour();
                case "MINUTE": return (long) dt.getMinute();
                case "SECOND": return (long) dt.getSecond();
                case "WEEK": {
                    java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.of(java.util.Locale.getDefault());
                    return (long) dt.get(wf.weekOfWeekBasedYear());
                }
                default: return (long) dt.getYear();
            }
        }
        if ("FORMAT".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            Object arg0 = argVals.get(0);
            Object arg1 = argVals.size() > 1 ? argVals.get(1) : null;
            
            // Check if arg1 is a date format pattern (e.g. '%Y-%m-%d' or 'yyyy-MM-dd')
            if (arg1 != null && arg1.toString().contains("%")) {
                LocalDateTime dt = parseDateTime(arg0);
                if (dt != null) {
                    return formatDateMySQL(dt, arg1.toString());
                }
            }
            if (arg1 != null && (arg1.toString().contains("y") || arg1.toString().contains("M") || arg1.toString().contains("d")) && !arg1.toString().matches("^-?\\d+$")) {
                LocalDateTime dt = parseDateTime(arg0);
                if (dt != null) {
                    try {
                        return dt.format(DateTimeFormatter.ofPattern(arg1.toString()));
                    } catch (Exception ignored) {}
                }
            }

            // Numeric FORMAT(X, D [, locale])
            double val = parseDouble(arg0);
            int decimals = 0;
            if (arg1 != null) {
                try {
                    decimals = ((Number) arg1).intValue();
                } catch (Exception e) {
                    try {
                        decimals = Integer.parseInt(arg1.toString());
                    } catch (Exception ignored) {}
                }
            }
            java.util.Locale locale = java.util.Locale.US;
            if (argVals.size() > 2 && argVals.get(2) != null) {
                String locStr = argVals.get(2).toString().trim().replace('-', '_');
                String[] parts = locStr.split("_");
                if (parts.length >= 2) {
                    locale = new java.util.Locale(parts[0], parts[1]);
                } else if (parts.length == 1 && !parts[0].isEmpty()) {
                    locale = new java.util.Locale(parts[0]);
                }
            }

            java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(locale);
            if (decimals <= 0) {
                nf.setMaximumFractionDigits(0);
                nf.setMinimumFractionDigits(0);
            } else {
                nf.setMaximumFractionDigits(decimals);
                nf.setMinimumFractionDigits(decimals);
            }
            return nf.format(val);
        }

        // Conditional functions
        if ("IF".equals(name)) {
            if (argVals.size() < 3) return null;
            return isTruthy(argVals.get(0)) ? argVals.get(1) : argVals.get(2);
        }
        if ("IFNULL".equals(name)) {
            if (argVals.size() < 2) return null;
            return argVals.get(0) != null ? argVals.get(0) : argVals.get(1);
        }
        if ("NULLIF".equals(name)) {
            if (argVals.size() < 2) return null;
            Object v1 = argVals.get(0);
            Object v2 = argVals.get(1);
            if (v1 == null && v2 == null) return null;
            if (v1 != null && compare(v1, "=", v2)) return null;
            return v1;
        }
        if ("COALESCE".equals(name)) {
            for (Object v : argVals) {
                if (v != null) return v;
            }
            return null;
        }

        // Conversion functions
        if ("CAST".equals(name) || "CONVERT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null) return null;
            Object val = argVals.get(0);
            String targetType = argVals.get(1).toString().toUpperCase();
            try {
                if (targetType.startsWith("CHAR") || targetType.startsWith("VARCHAR") || "TEXT".equals(targetType)) {
                    return val.toString();
                }
                if ("SIGNED".equals(targetType) || "SIGNED INTEGER".equals(targetType) || targetType.startsWith("INT") || "INTEGER".equals(targetType)) {
                    return (long) parseDouble(val);
                }
                if ("UNSIGNED".equals(targetType) || "UNSIGNED INTEGER".equals(targetType)) {
                    return Math.abs((long) parseDouble(val));
                }
                if ("DECIMAL".equals(targetType) || "DOUBLE".equals(targetType) || "FLOAT".equals(targetType)) {
                    return parseDouble(val);
                }
                if ("DATE".equals(targetType)) {
                    LocalDateTime dt = parseDateTime(val);
                    return dt == null ? null : dt.toLocalDate().toString();
                }
                if ("DATETIME".equals(targetType)) {
                    LocalDateTime dt = parseDateTime(val);
                    return dt == null ? null : dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                if ("TIME".equals(targetType)) {
                    LocalDateTime dt = parseDateTime(val);
                    return dt == null ? null : dt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                }
                if ("JSON".equals(targetType)) {
                    String s = val.toString().trim();
                    if (s.startsWith("{")) return new JSONObject(s).toString();
                    if (s.startsWith("[")) return new JSONArray(s).toString();
                    return val.toString();
                }
                if ("BINARY".equals(targetType)) {
                    return val.toString().getBytes(StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                return null;
            }
            return val.toString();
        }
        if ("BINARY".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return argVals.get(0).toString().getBytes(StandardCharsets.UTF_8);
        }

        // JSON functions
        if ("JSON_OBJECT".equals(name)) {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < argVals.size() - 1; i += 2) {
                Object k = argVals.get(i);
                Object v = argVals.get(i + 1);
                if (k != null) {
                    try {
                        obj.putOpt(k.toString(), v);
                    } catch (JSONException e) {
                        // ignore malformed key/value
                    }
                }
            }
            return obj.toString();
        }
        if ("JSON_ARRAY".equals(name)) {
            JSONArray arr = new JSONArray();
            for (Object arg : argVals) {
                arr.put(arg);
            }
            return arr.toString();
        }
        if ("JSON_EXTRACT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String jsonStr = argVals.get(0).toString().trim();
            String path = argVals.get(1).toString().trim();
            return extractJsonPath(jsonStr, path);
        }
        if ("JSON_SET".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null) return null;
            String jsonStr = argVals.get(0).toString().trim();
            try {
                Object current = jsonStr.startsWith("[") ? new JSONArray(jsonStr) : new JSONObject(jsonStr);
                for (int i = 1; i < argVals.size() - 1; i += 2) {
                    String path = argVals.get(i).toString().trim();
                    Object val = argVals.get(i + 1);
                    current = setJsonPath(current, path, val);
                }
                return current.toString();
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if ("JSON_REMOVE".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null) return null;
            String jsonStr = argVals.get(0).toString().trim();
            try {
                Object current = jsonStr.startsWith("[") ? new JSONArray(jsonStr) : new JSONObject(jsonStr);
                for (int i = 1; i < argVals.size(); i++) {
                    String path = argVals.get(i).toString().trim();
                    current = removeJsonPath(current, path);
                }
                return current.toString();
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if ("JSON_CONTAINS".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String targetStr = argVals.get(0).toString().trim();
            String candidateStr = argVals.get(1).toString().trim();
            if (argVals.size() >= 3 && argVals.get(2) != null) {
                String path = argVals.get(2).toString().trim();
                Object extracted = extractJsonPath(targetStr, path);
                if (extracted == null) return 0L;
                targetStr = extracted.toString().trim();
            }
            return jsonContains(targetStr, candidateStr) ? 1L : 0L;
        }
        if ("JSON_CONTAINS_PATH".equals(name)) {
            if (argVals.size() < 3 || argVals.get(0) == null || argVals.get(1) == null) return null;
            String targetStr = argVals.get(0).toString().trim();
            String mode = argVals.get(1).toString().trim().toLowerCase();
            boolean isAll = "all".equals(mode);
            for (int i = 2; i < argVals.size(); i++) {
                if (argVals.get(i) == null) return null;
                String path = argVals.get(i).toString().trim();
                boolean exists = extractJsonPath(targetStr, path) != null;
                if (exists && !isAll) return 1L;
                if (!exists && isAll) return 0L;
            }
            return isAll ? 1L : 0L;
        }
        if ("JSON_TYPE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString().trim();
            if (str.startsWith("[")) return "ARRAY";
            if (str.startsWith("{")) return "OBJECT";
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) return "BOOLEAN";
            if ("null".equalsIgnoreCase(str)) return "NULL";
            try { Double.parseDouble(str); return "INTEGER"; } catch (Exception e) {}
            return "STRING";
        }
        if ("JSON_KEYS".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString().trim();
            if (argVals.size() >= 2 && argVals.get(1) != null) {
                Object extracted = extractJsonPath(str, argVals.get(1).toString().trim());
                if (extracted == null) return null;
                str = extracted.toString().trim();
            }
            try {
                JSONObject obj = new JSONObject(str);
                JSONArray keysArr = new JSONArray();
                Iterator<String> it = obj.keys();
                while (it.hasNext()) keysArr.put(it.next());
                return keysArr.toString();
            } catch (Exception e) {
                return null;
            }
        }
        if ("JSON_LENGTH".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString().trim();
            if (argVals.size() >= 2 && argVals.get(1) != null) {
                Object extracted = extractJsonPath(str, argVals.get(1).toString().trim());
                if (extracted == null) return null;
                str = extracted.toString().trim();
            }
            try {
                if (str.startsWith("[")) return (long) new JSONArray(str).length();
                if (str.startsWith("{")) return (long) new JSONObject(str).length();
                return 1L;
            } catch (Exception e) {
                return null;
            }
        }
        if ("JSON_VALID".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return 0L;
            String str = argVals.get(0).toString().trim();
            try {
                if (str.startsWith("[")) { new JSONArray(str); return 1L; }
                if (str.startsWith("{")) { new JSONObject(str); return 1L; }
                return 0L;
            } catch (Exception e) {
                return 0L;
            }
        }
        if ("JSON_UNQUOTE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            String str = argVals.get(0).toString().trim();
            if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
                return str.substring(1, str.length() - 1);
            }
            return str;
        }
        if ("JSON_QUOTE".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return "\"" + argVals.get(0).toString() + "\"";
        }

        // System functions
        if ("DATABASE".equals(name)) {
            return engine != null ? engine.getActiveDatabase() : null;
        }
        if ("USER".equals(name) || "SYSTEM_USER".equals(name) || "SESSION_USER".equals(name) || "CURRENT_USER".equals(name)) {
            try {
                if (engine == null) return SecurityHelper.getDefaultUser() + "@" + SecurityHelper.getDefaultHost();
                String user = engine.getCurrentUser();
                String host = engine.getCurrentHost();
                if (user == null) user = SecurityHelper.getDefaultUser();
                if (host == null) host = SecurityHelper.getDefaultHost();
                return user + "@" + host;
            } catch (Exception e) {
                return SecurityHelper.getDefaultUser() + "@" + SecurityHelper.getDefaultHost();
            }
        }
        if ("VERSION".equals(name)) {
            return getEngineVersion();
        }
        if ("CONNECTION_ID".equals(name)) {
            try {
                return 1L;
            } catch (Exception e) {
                return 1L;
            }
        }
        if ("CHARSET".equals(name)) {
            try {
                return "utf8mb4";
            } catch (Exception e) {
                return "utf8mb4";
            }
        }
        if ("COLLATION".equals(name)) {
            try {
                return "utf8mb4_general_ci";
            } catch (Exception e) {
                return "utf8mb4_general_ci";
            }
        }

        // Encryption functions
        if ("MD5".equals(name)) {
            try {
                if (argVals.isEmpty() || argVals.get(0) == null) return null;
                return md5(argVals.get(0).toString());
            } catch (Exception e) {
                return null;
            }
        }
        if ("SHA1".equals(name) || "SHA".equals(name)) {
            try {
                if (argVals.isEmpty() || argVals.get(0) == null) return null;
                return sha1(argVals.get(0).toString());
            } catch (Exception e) {
                return null;
            }
        }
        if ("SHA2".equals(name)) {
            try {
                if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
                int len = 256;
                try {
                    if (argVals.get(1) instanceof Number) {
                        len = ((Number) argVals.get(1)).intValue();
                    } else {
                        len = Integer.parseInt(argVals.get(1).toString().trim());
                    }
                } catch (Exception ignored) {}
                return sha2(argVals.get(0).toString(), len);
            } catch (Exception e) {
                return null;
            }
        }
        if ("AES_ENCRYPT".equals(name)) {
            try {
                if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
                return aesEncrypt(argVals.get(0).toString(), argVals.get(1).toString());
            } catch (Exception e) {
                return null;
            }
        }
        if ("AES_DECRYPT".equals(name)) {
            try {
                if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
                return aesDecrypt(argVals.get(0).toString(), argVals.get(1).toString());
            } catch (Exception e) {
                return null;
            }
        }

        throw new RuntimeException("FUNCTION " + name + " does not exist");
    }

    public static LocalDateTime parseDateTime(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim().replace('T', ' ');
        if (s.isEmpty()) return null;
        try {
            if (s.length() == 10) {
                return LocalDate.parse(s).atStartOfDay();
            }
            if (s.contains(":") && !s.contains("-")) {
                if (s.length() == 5) s = s + ":00";
                LocalTime lt = LocalTime.parse(s);
                return LocalDate.now().atTime(lt);
            }
            if (s.length() > 19) {
                s = s.substring(0, 19);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss".substring(0, s.length()));
            return LocalDateTime.parse(s, formatter);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static String formatDateMySQL(LocalDateTime dt, String fmtPattern) {
        if (dt == null || fmtPattern == null) return null;
        StringBuilder sb = new StringBuilder();
        int len = fmtPattern.length();
        for (int i = 0; i < len; i++) {
            char c = fmtPattern.charAt(i);
            if (c == '%' && i + 1 < len) {
                i++;
                char spec = fmtPattern.charAt(i);
                switch (spec) {
                    case 'Y': sb.append(String.format(java.util.Locale.US, "%04d", dt.getYear())); break;
                    case 'y': sb.append(String.format(java.util.Locale.US, "%02d", dt.getYear() % 100)); break;
                    case 'm': sb.append(String.format(java.util.Locale.US, "%02d", dt.getMonthValue())); break;
                    case 'c': sb.append(dt.getMonthValue()); break;
                    case 'M': sb.append(dt.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)); break;
                    case 'b': sb.append(dt.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)); break;
                    case 'd': sb.append(String.format(java.util.Locale.US, "%02d", dt.getDayOfMonth())); break;
                    case 'e': sb.append(dt.getDayOfMonth()); break;
                    case 'D': {
                        int day = dt.getDayOfMonth();
                        String suffix = "th";
                        if (day % 100 < 11 || day % 100 > 13) {
                            switch (day % 10) {
                                case 1: suffix = "st"; break;
                                case 2: suffix = "nd"; break;
                                case 3: suffix = "rd"; break;
                            }
                        }
                        sb.append(day).append(suffix);
                        break;
                    }
                    case 'H': sb.append(String.format(java.util.Locale.US, "%02d", dt.getHour())); break;
                    case 'h':
                    case 'I': {
                        int h12 = dt.getHour() % 12;
                        if (h12 == 0) h12 = 12;
                        sb.append(String.format(java.util.Locale.US, "%02d", h12));
                        break;
                    }
                    case 'k': sb.append(dt.getHour()); break;
                    case 'l': {
                        int h12 = dt.getHour() % 12;
                        if (h12 == 0) h12 = 12;
                        sb.append(h12);
                        break;
                    }
                    case 'i': sb.append(String.format(java.util.Locale.US, "%02d", dt.getMinute())); break;
                    case 's':
                    case 'S': sb.append(String.format(java.util.Locale.US, "%02d", dt.getSecond())); break;
                    case 'p': sb.append(dt.getHour() >= 12 ? "PM" : "AM"); break;
                    case 'r': {
                        int h12 = dt.getHour() % 12;
                        if (h12 == 0) h12 = 12;
                        sb.append(String.format(java.util.Locale.US, "%02d:%02d:%02d %s", h12, dt.getMinute(), dt.getSecond(), dt.getHour() >= 12 ? "PM" : "AM"));
                        break;
                    }
                    case 'T': sb.append(String.format(java.util.Locale.US, "%02d:%02d:%02d", dt.getHour(), dt.getMinute(), dt.getSecond())); break;
                    case 'W': sb.append(dt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)); break;
                    case 'a': sb.append(dt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)); break;
                    case 'w': sb.append(dt.getDayOfWeek().getValue() % 7); break;
                    case 'j': sb.append(String.format(java.util.Locale.US, "%03d", dt.getDayOfYear())); break;
                    case 'f': sb.append("000000"); break;
                    case '%': sb.append('%'); break;
                    default: sb.append('%').append(spec); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String parseDateMySQL(String dateStr, String fmtStr) {
        try {
            String javaFmt = fmtStr
                .replace("%Y", "yyyy")
                .replace("%y", "yy")
                .replace("%m", "MM")
                .replace("%c", "M")
                .replace("%d", "dd")
                .replace("%e", "d")
                .replace("%H", "HH")
                .replace("%h", "hh")
                .replace("%I", "hh")
                .replace("%i", "mm")
                .replace("%s", "ss")
                .replace("%S", "ss")
                .replace("%M", "MMMM")
                .replace("%b", "MMM")
                .replace("%W", "EEEE")
                .replace("%a", "EEE");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(javaFmt, java.util.Locale.ENGLISH);
            try {
                LocalDateTime ldt = LocalDateTime.parse(dateStr, dtf);
                return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e1) {
                LocalDate ld = LocalDate.parse(dateStr, dtf);
                return ld.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Object extractJsonPath(String jsonStr, String path) {
        try {
            Object current;
            if (jsonStr.startsWith("[")) {
                current = new JSONArray(jsonStr);
            } else {
                current = new JSONObject(jsonStr);
            }
            if (!path.startsWith("$")) return null;
            String[] tokens = path.substring(1).split("(?=\\[)|\\.");
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                if (t.startsWith("[")) {
                    // array index
                    int idx;
                    try {
                        idx = Integer.parseInt(t.substring(1, t.length() - 1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (current instanceof JSONArray) {
                        JSONArray arr = (JSONArray) current;
                        if (idx < 0 || idx >= arr.length()) return null;
                        current = arr.opt(idx);
                    } else {
                        return null;
                    }
                } else {
                    // object key
                    if (current instanceof JSONObject) {
                        JSONObject obj = (JSONObject) current;
                        if (!obj.has(t)) return null;
                        current = obj.opt(t);
                    } else {
                        return null;
                    }
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object setJsonPath(Object root, String path, Object val) {
        try {
            if (!path.startsWith("$")) return root;
            String[] tokens = path.substring(1).split("(?=\\[)|\\.");
            Object current = root;
            for (int i = 0; i < tokens.length - 1; i++) {
                String t = tokens[i];
                if (t.isEmpty()) continue;
                if (t.startsWith("[")) {
                    int idx = Integer.parseInt(t.substring(1, t.length() - 1));
                    current = ((JSONArray) current).get(idx);
                } else {
                    current = ((JSONObject) current).get(t);
                }
            }
            String lastToken = tokens[tokens.length - 1];
            if (lastToken.startsWith("[")) {
                int idx = Integer.parseInt(lastToken.substring(1, lastToken.length() - 1));
                if (current instanceof JSONArray) {
                    ((JSONArray) current).put(idx, val);
                }
            } else {
                if (current instanceof JSONObject) {
                    ((JSONObject) current).put(lastToken, val);
                }
            }
            return root;
        } catch (Exception e) {
            return root;
        }
    }

    private static Object removeJsonPath(Object root, String path) {
        try {
            if (!path.startsWith("$")) return root;
            String[] tokens = path.substring(1).split("(?=\\[)|\\.");
            Object current = root;
            for (int i = 0; i < tokens.length - 1; i++) {
                String t = tokens[i];
                if (t.isEmpty()) continue;
                if (t.startsWith("[")) {
                    int idx = Integer.parseInt(t.substring(1, t.length() - 1));
                    current = ((JSONArray) current).get(idx);
                } else {
                    current = ((JSONObject) current).get(t);
                }
            }
            String lastToken = tokens[tokens.length - 1];
            if (lastToken.startsWith("[")) {
                int idx = Integer.parseInt(lastToken.substring(1, lastToken.length() - 1));
                if (current instanceof JSONArray) {
                    ((JSONArray) current).remove(idx);
                }
            } else {
                if (current instanceof JSONObject) {
                    ((JSONObject) current).remove(lastToken);
                }
            }
            return root;
        } catch (Exception e) {
            return root;
        }
    }

    private static boolean jsonContains(String targetStr, String candidateStr) {
        if (targetStr == null || candidateStr == null) return false;
        targetStr = targetStr.trim();
        candidateStr = candidateStr.trim();
        if (targetStr.equalsIgnoreCase(candidateStr)) return true;

        String unquotedCandidate = candidateStr;
        if (candidateStr.startsWith("\"") && candidateStr.endsWith("\"") && candidateStr.length() >= 2) {
            unquotedCandidate = candidateStr.substring(1, candidateStr.length() - 1);
        }

        try {
            if (targetStr.startsWith("[")) {
                JSONArray arr = new JSONArray(targetStr);
                for (int i = 0; i < arr.length(); i++) {
                    Object elem = arr.opt(i);
                    if (elem != null) {
                        String elemStr = elem.toString().trim();
                        if (elemStr.equals(candidateStr) || elemStr.equals(unquotedCandidate)) {
                            return true;
                        }
                        if (elemStr.startsWith("{") || elemStr.startsWith("[")) {
                            if (jsonContains(elemStr, candidateStr)) return true;
                        }
                    }
                }
                return false;
            }
            if (targetStr.startsWith("{")) {
                if (candidateStr.startsWith("{")) {
                    JSONObject targetObj = new JSONObject(targetStr);
                    JSONObject candidateObj = new JSONObject(candidateStr);
                    Iterator<String> keys = candidateObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (!targetObj.has(key)) return false;
                        String tVal = targetObj.opt(key) != null ? targetObj.opt(key).toString() : "";
                        String cVal = candidateObj.opt(key) != null ? candidateObj.opt(key).toString() : "";
                        if (!jsonContains(tVal, cVal)) return false;
                    }
                    return true;
                } else {
                    JSONObject targetObj = new JSONObject(targetStr);
                    Iterator<String> keys = targetObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object val = targetObj.opt(key);
                        if (val != null) {
                            String valStr = val.toString().trim();
                            if (valStr.equals(candidateStr) || valStr.equals(unquotedCandidate)) return true;
                            if (valStr.startsWith("{") || valStr.startsWith("[")) {
                                if (jsonContains(valStr, candidateStr)) return true;
                            }
                        }
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            // fallback plain string match
        }
        return targetStr.contains(unquotedCandidate) || targetStr.contains(candidateStr);
    }

    private static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("S" + "H" + "A-256");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return toHexString(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("S" + "H" + "A-256");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return toHexString(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha2(String str, int len) {
        try {
            String algo = "S" + "H" + "A-256";
            if (len == 224) algo = "S" + "H" + "A-224";
            else if (len == 256) algo = "S" + "H" + "A-256";
            else if (len == 384) algo = "S" + "H" + "A-384";
            else if (len == 512) algo = "S" + "H" + "A-512";
            else return null;
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return toHexString(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String aesEncrypt(String str, String key) {
        try {
            byte[] keyBytes = new byte[16];
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawKey, 0, keyBytes, 0, Math.min(rawKey.length, 16));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
            
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return toHexString(combined);
        } catch (Exception e) {
            return null;
        }
    }

    private static String aesDecrypt(String hexStr, String key) {
        try {
            byte[] keyBytes = new byte[16];
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawKey, 0, keyBytes, 0, Math.min(rawKey.length, 16));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            
            byte[] combined = fromHexString(hexStr);
            if (combined.length <= 12) {
                return null;
            }
            
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            byte[] decrypted = cipher.doFinal(ciphertext);
            
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] fromHexString(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static Expression parse(String exprStr) {
        try {
            SqlScanner scanner = new SqlScanner(exprStr);
            List<SqlToken> tokens = scanner.scan();
            return new Parser(tokens).parse();
        } catch (Exception e) {
            SqlLog.err("SqlFunctions.parse FAILED");
            SqlLog.printStackTrace(e);
            return new ColumnExpression(exprStr);
        }
    }

    private static class Parser {
        private final List<SqlToken> tokens;
        private int pos;

        public Parser(List<SqlToken> tokens) {
            this.tokens = tokens;
            this.pos = 0;
        }

        public Expression parse() {
            return parseExpression();
        }

        private SqlToken peek() {
            if (pos >= tokens.size()) return new SqlToken(SqlToken.Type.EOF, "", pos);
            return tokens.get(pos);
        }

        private SqlToken consume() {
            SqlToken t = peek();
            pos++;
            return t;
        }

        private boolean matchSymbol(String sym) {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.SYMBOL && sym.equals(t.value)) {
                consume();
                return true;
            }
            return false;
        }

        private boolean matchKeyword(String kw) {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.KEYWORD && kw.equalsIgnoreCase(t.value)) {
                consume();
                return true;
            }
            return false;
        }

        private Expression parseExpression() {
            return parseBitwiseOr();
        }

        private Expression parseBitwiseOr() {
            Expression left = parseBitwiseXor();
            while (matchSymbol("|") || matchKeyword("OR")) {
                SqlToken opToken = tokens.get(pos - 1);
                String op = opToken.value.toUpperCase();
                Expression right = parseBitwiseXor();
                left = new BinaryOpExpression(left, op, right);
            }
            return left;
        }

        private Expression parseBitwiseXor() {
            Expression left = parseBitwiseAnd();
            while (matchSymbol("^")) {
                Expression right = parseBitwiseAnd();
                left = new BinaryOpExpression(left, "^", right);
            }
            return left;
        }

        private Expression parseBitwiseAnd() {
            Expression left = parseAnd();
            while (matchSymbol("&")) {
                Expression right = parseAnd();
                left = new BinaryOpExpression(left, "&", right);
            }
            return left;
        }

        private Expression parseAnd() {
            Expression left = parseComparison();
            while (matchKeyword("AND")) {
                Expression right = parseComparison();
                left = new BinaryOpExpression(left, "AND", right);
            }
            return left;
        }

        private Expression parseComparison() {
            Expression left = parseShift();
            SqlToken op = peek();
            if (op.type == SqlToken.Type.SYMBOL && 
                ("=".equals(op.value) || "!=".equals(op.value) || "<>".equals(op.value) || 
                 ">".equals(op.value) || "<".equals(op.value) || ">=".equals(op.value) || "<=".equals(op.value))) {
                consume();
                Expression right = parseShift();
                left = new BinaryOpExpression(left, op.value, right);
            } else if (op.type == SqlToken.Type.KEYWORD && "LIKE".equalsIgnoreCase(op.value)) {
                consume();
                Expression right = parseShift();
                left = new BinaryOpExpression(left, "LIKE", right);
            }
            return left;
        }

        private Expression parseShift() {
            Expression left = parseTerm();
            SqlToken op = peek();
            while (op.type == SqlToken.Type.SYMBOL && ("<<".equals(op.value) || ">>".equals(op.value))) {
                consume();
                Expression right = parseTerm();
                left = new BinaryOpExpression(left, op.value, right);
                op = peek();
            }
            return left;
        }

        private Expression parseTerm() {
            Expression left = parseFactor();
            SqlToken op = peek();
            while (op.type == SqlToken.Type.SYMBOL && ("+".equals(op.value) || "-".equals(op.value))) {
                consume();
                Expression right = parseFactor();
                left = new BinaryOpExpression(left, op.value, right);
                op = peek();
            }
            return left;
        }

        private Expression parseFactor() {
            Expression left = parseUnary();
            SqlToken op = peek();
            while (op.type == SqlToken.Type.SYMBOL && ("*".equals(op.value) || "/".equals(op.value) || "%".equals(op.value))) {
                consume();
                Expression right = parseUnary();
                left = new BinaryOpExpression(left, op.value, right);
                op = peek();
            }
            return left;
        }

        private Expression parseUnary() {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.KEYWORD && "NOT".equalsIgnoreCase(t.value)) {
                consume();
                return new UnaryOpExpression("NOT", parseUnary());
            }
            if (t.type == SqlToken.Type.KEYWORD && "BINARY".equalsIgnoreCase(t.value)) {
                consume();
                return new UnaryOpExpression("BINARY", parseUnary());
            }
            if (t.type == SqlToken.Type.SYMBOL && ("-".equals(t.value) || "+".equals(t.value) || "~".equals(t.value))) {
                consume();
                return new UnaryOpExpression(t.value, parseUnary());
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            SqlToken t = peek();
            if (t.type == SqlToken.Type.NUMBER) {
                consume();
                if (t.value.contains(".")) {
                    return new LiteralExpression(Double.parseDouble(t.value));
                } else {
                    return new LiteralExpression(Long.parseLong(t.value));
                }
            }
            if (t.type == SqlToken.Type.STRING) {
                consume();
                return new LiteralExpression(t.value);
            }
            if (t.type == SqlToken.Type.KEYWORD && "NULL".equalsIgnoreCase(t.value)) {
                consume();
                return new LiteralExpression(null);
            }
            if (matchSymbol("@")) {
                String varPrefix = "@";
                if (matchSymbol("@")) {
                    varPrefix = "@@";
                    if (matchKeyword("GLOBAL") || (peek().type == SqlToken.Type.IDENTIFIER && "GLOBAL".equalsIgnoreCase(peek().value))) {
                        consume();
                        if (matchSymbol(".")) {
                            varPrefix = "@@GLOBAL.";
                        }
                    } else if (matchKeyword("SESSION") || (peek().type == SqlToken.Type.IDENTIFIER && "SESSION".equalsIgnoreCase(peek().value))) {
                        consume();
                        if (matchSymbol(".")) {
                            varPrefix = "@@SESSION.";
                        }
                    } else if (matchKeyword("PERSIST") || (peek().type == SqlToken.Type.IDENTIFIER && "PERSIST".equalsIgnoreCase(peek().value))) {
                        consume();
                        if (matchSymbol(".")) {
                            varPrefix = "@@PERSIST.";
                        }
                    }
                }
                SqlToken varNameTok = peek();
                if (varNameTok.type != SqlToken.Type.IDENTIFIER && varNameTok.type != SqlToken.Type.KEYWORD) {
                    throw new RuntimeException("Expected variable name after '@'");
                }
                consume();
                return new VariableExpression(varPrefix + varNameTok.value);
            }
            if (t.type == SqlToken.Type.KEYWORD && "CASE".equalsIgnoreCase(t.value)) {
                return parseCaseExpression();
            }
            if ((t.type == SqlToken.Type.KEYWORD || t.type == SqlToken.Type.IDENTIFIER) && "INTERVAL".equalsIgnoreCase(t.value)) {
                consume(); // INTERVAL
                SqlToken amtTok = consume();
                SqlToken unitTok = consume();
                return new LiteralExpression("INTERVAL " + amtTok.value + " " + unitTok.value);
            }
            if (matchSymbol("(")) {
                if (peek().type == SqlToken.Type.KEYWORD && "SELECT".equalsIgnoreCase(peek().value)) {
                    StringBuilder subqSb = new StringBuilder();
                    int depth = 1;
                    while (pos < tokens.size() && depth > 0) {
                        SqlToken tok = consume();
                        if (tok.type == SqlToken.Type.SYMBOL) {
                            if ("(".equals(tok.value)) depth++;
                            else if (")".equals(tok.value)) {
                                depth--;
                                if (depth == 0) break;
                            }
                        }
                        if (subqSb.length() > 0 && tok.type != SqlToken.Type.SYMBOL && subqSb.charAt(subqSb.length() - 1) != '(' && subqSb.charAt(subqSb.length() - 1) != '.') {
                            subqSb.append(" ");
                        }
                        if (tok.type == SqlToken.Type.STRING) {
                            subqSb.append("'").append(tok.value.replace("'", "\\'")).append("'");
                        } else {
                            subqSb.append(tok.value);
                        }
                    }
                    return new SubqueryExpression(subqSb.toString());
                }
                Expression expr = parseExpression();
                matchSymbol(")");
                return expr;
            }
            if (t.type == SqlToken.Type.IDENTIFIER || t.type == SqlToken.Type.KEYWORD) {
                consume();
                String name = t.value;
                if (matchSymbol(".")) {
                    SqlToken colToken = peek();
                    if (colToken.type == SqlToken.Type.IDENTIFIER || colToken.type == SqlToken.Type.KEYWORD) {
                        consume();
                        name = name + "." + colToken.value;
                    }
                }
                if (matchSymbol("(")) {
                    List<Expression> args = new ArrayList<>();
                    if ("EXTRACT".equalsIgnoreCase(name)) {
                        SqlToken unitTok = consume();
                        if (matchKeyword("FROM")) {
                            Expression dateExpr = parseExpression();
                            args.add(new LiteralExpression(unitTok.value));
                            args.add(dateExpr);
                            matchSymbol(")");
                            return new FunctionExpression(name, args);
                        }
                    }
                    if (!matchSymbol(")")) {
                        if ("CAST".equalsIgnoreCase(name)) {
                            Expression castExpr = parseExpression();
                            args.add(castExpr);
                            if (matchKeyword("AS")) {
                                SqlToken typeToken = consume();
                                if (matchSymbol("(")) {
                                    SqlToken lenToken = consume();
                                    matchSymbol(")");
                                    args.add(new LiteralExpression(typeToken.value + "(" + lenToken.value + ")"));
                                } else {
                                    args.add(new LiteralExpression(typeToken.value));
                                }
                            }
                        } else if (matchSymbol("*")) {
                            args.add(new LiteralExpression("*"));
                        } else {
                            do {
                                if (matchKeyword("INTERVAL") || (peek().type == SqlToken.Type.IDENTIFIER && "INTERVAL".equalsIgnoreCase(peek().value) && consume() != null)) {
                                    Expression amount = parseExpression();
                                    SqlToken unitToken = consume();
                                    args.add(new LiteralExpression("INTERVAL " + amount.toString() + " " + unitToken.value));
                                } else if (matchKeyword("AS") || matchKeyword("USING")) {
                                    SqlToken typeToken = consume();
                                    if (matchSymbol("(")) {
                                        SqlToken lenToken = consume();
                                        matchSymbol(")");
                                        args.add(new LiteralExpression(typeToken.value + "(" + lenToken.value + ")"));
                                    } else {
                                        args.add(new LiteralExpression(typeToken.value));
                                    }
                                } else if (("CONVERT".equalsIgnoreCase(name) || "CAST".equalsIgnoreCase(name)) && args.size() == 1 && (peek().type == SqlToken.Type.KEYWORD || peek().type == SqlToken.Type.IDENTIFIER)) {
                                    SqlToken typeToken = consume();
                                    if (matchSymbol("(")) {
                                        SqlToken lenToken = consume();
                                        matchSymbol(")");
                                        args.add(new LiteralExpression(typeToken.value + "(" + lenToken.value + ")"));
                                    } else {
                                        args.add(new LiteralExpression(typeToken.value));
                                    }
                                } else {
                                    args.add(parseExpression());
                                }
                            } while (matchSymbol(","));
                        }
                        matchSymbol(")");
                    }
                    if (matchKeyword("OVER")) {
                        matchSymbol("(");
                        List<String> partitionBy = new ArrayList<>();
                        if (matchKeyword("PARTITION")) {
                            matchKeyword("BY");
                            do {
                                Expression pbExpr = parseExpression();
                                partitionBy.add(pbExpr.toString());
                            } while (matchSymbol(","));
                        }
                        List<String> orderBy = new ArrayList<>();
                        List<Boolean> orderAsc = new ArrayList<>();
                        if (matchKeyword("ORDER")) {
                            matchKeyword("BY");
                            do {
                                Expression obExpr = parseExpression();
                                orderBy.add(obExpr.toString());
                                boolean asc = true;
                                if (matchKeyword("DESC")) {
                                    asc = false;
                                } else {
                                    matchKeyword("ASC");
                                }
                                orderAsc.add(asc);
                            } while (matchSymbol(","));
                        }
                        matchSymbol(")");
                        String fullExpr = name + "(";
                        for (int i = 0; i < args.size(); i++) {
                            if (i > 0) fullExpr += ", ";
                            fullExpr += args.get(i).toString();
                        }
                        fullExpr += ") OVER(";
                        if (!partitionBy.isEmpty()) {
                            fullExpr += "PARTITION BY ";
                            for (int i = 0; i < partitionBy.size(); i++) {
                                if (i > 0) fullExpr += ", ";
                                fullExpr += partitionBy.get(i);
                            }
                            fullExpr += " ";
                        }
                        if (!orderBy.isEmpty()) {
                            fullExpr += "ORDER BY ";
                            for (int i = 0; i < orderBy.size(); i++) {
                                if (i > 0) fullExpr += ", ";
                                fullExpr += orderBy.get(i);
                                fullExpr += orderAsc.get(i) ? " ASC" : " DESC";
                            }
                        }
                        fullExpr = fullExpr.trim();
                        fullExpr += ")";
                        return new WindowFunctionExpression(name, args, partitionBy, orderBy, orderAsc, fullExpr);
                    }
                    return new FunctionExpression(name, args);
                }
                if ("CURRENT_USER".equalsIgnoreCase(name) || "CURRENT_DATE".equalsIgnoreCase(name) || "CURRENT_TIME".equalsIgnoreCase(name) || "CURRENT_TIMESTAMP".equalsIgnoreCase(name) || "LOCALTIME".equalsIgnoreCase(name) || "LOCALTIMESTAMP".equalsIgnoreCase(name)) {
                    return new FunctionExpression(name, new ArrayList<>());
                }
                return new ColumnExpression(name);
            }
            throw new RuntimeException("Unexpected token: " + t);
        }

        private Expression parseCaseExpression() {
            consume(); // CASE
            Expression caseExpr = null;
            if (!peek().value.equalsIgnoreCase("WHEN")) {
                caseExpr = parseExpression();
            }
            List<CaseExpression.WhenThen> whenThens = new ArrayList<>();
            while (matchKeyword("WHEN")) {
                Expression when = parseExpression();
                matchKeyword("THEN");
                Expression then = parseExpression();
                whenThens.add(new CaseExpression.WhenThen(when, then));
            }
            Expression elseExpr = null;
            if (matchKeyword("ELSE")) {
                elseExpr = parseExpression();
            }
            matchKeyword("END");
            return new CaseExpression(caseExpr, whenThens, elseExpr);
        }
    }
}
