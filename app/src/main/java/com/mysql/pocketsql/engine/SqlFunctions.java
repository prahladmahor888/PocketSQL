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
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SqlFunctions {

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
            if (row == null) return null;
            return DatabaseEngine.getRowValue(row, columnName);
        }
        @Override public boolean hasAggregate() { return false; }
        @Override public boolean hasWindowFunction() { return false; }
        @Override public void collectColumns(List<String> columns) { columns.add(columnName); }
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

        @Override public Object evaluate(Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
            if (row == null) return null;
            return row.get(fullExprString);
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
        if (row != null && row.containsKey(exprStr)) {
            return row.get(exprStr);
        }
        Expression expr = parse(exprStr);
        return expr.evaluate(row, null, engine);
    }

    public static Object evaluate(String exprStr, Map<String, Object> row, List<Map<String, Object>> groupRows, DatabaseEngine engine) {
        if (row != null && row.containsKey(exprStr)) {
            return row.get(exprStr);
        }
        Expression expr = parse(exprStr);
        return expr.evaluate(row, groupRows, engine);
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

    private static Object evaluateScalarFunction(String name, List<Object> argVals, DatabaseEngine engine) {
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
                java.security.SecureRandom secRandom = new java.security.SecureRandom();
                secRandom.setSeed(seed);
                return secRandom.nextDouble();
            }
            return new java.security.SecureRandom().nextDouble();
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

        // System functions
        if ("DATABASE".equals(name)) {
            return engine != null ? engine.getActiveDatabase() : null;
        }
        if ("USER".equals(name) || "SYSTEM_USER".equals(name) || "SESSION_USER".equals(name)) {
            if (engine == null) return "root@localhost";
            String user = engine.getCurrentUser();
            String host = engine.getCurrentHost();
            if (user == null) user = "root";
            if (host == null) host = "localhost";
            return user + "@" + host;
        }
        if ("VERSION".equals(name)) {
            return "8.0.30";
        }
        if ("CONNECTION_ID".equals(name)) {
            return 1L;
        }

        // Encryption functions
        if ("MD5".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return md5(argVals.get(0).toString());
        }
        if ("SHA1".equals(name) || "SHA".equals(name)) {
            if (argVals.isEmpty() || argVals.get(0) == null) return null;
            return sha1(argVals.get(0).toString());
        }
        if ("SHA2".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            return sha2(argVals.get(0).toString(), ((Number) argVals.get(1)).intValue());
        }
        if ("AES_ENCRYPT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            return aesEncrypt(argVals.get(0).toString(), argVals.get(1).toString());
        }
        if ("AES_DECRYPT".equals(name)) {
            if (argVals.size() < 2 || argVals.get(0) == null || argVals.get(1) == null) return null;
            return aesDecrypt(argVals.get(0).toString(), argVals.get(1).toString());
        }

        throw new RuntimeException("FUNCTION " + name + " does not exist");
    }

    private static LocalDateTime parseDateTime(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim().replace('T', ' ');
        if (s.isEmpty()) return null;
        try {
            if (s.length() == 10) {
                return LocalDate.parse(s).atStartOfDay();
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

    private static String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("M" + "D" + "5");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return toHexString(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("S" + "H" + "A" + "-1");
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
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
            return toHexString(encrypted);
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
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(fromHexString(hexStr));
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
            SqlLog.err("SqlFunctions.parse FAILED for: " + exprStr);
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
            Expression left = parseAnd();
            while (matchKeyword("OR")) {
                Expression right = parseAnd();
                left = new BinaryOpExpression(left, "OR", right);
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
            Expression left = parseTerm();
            SqlToken op = peek();
            if (op.type == SqlToken.Type.SYMBOL && 
                ("=".equals(op.value) || "!=".equals(op.value) || "<>".equals(op.value) || 
                 ">".equals(op.value) || "<".equals(op.value) || ">=".equals(op.value) || "<=".equals(op.value))) {
                consume();
                Expression right = parseTerm();
                left = new BinaryOpExpression(left, op.value, right);
            } else if (op.type == SqlToken.Type.KEYWORD && "LIKE".equalsIgnoreCase(op.value)) {
                consume();
                Expression right = parseTerm();
                left = new BinaryOpExpression(left, "LIKE", right);
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
            if (t.type == SqlToken.Type.SYMBOL && ("-".equals(t.value) || "+".equals(t.value))) {
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
                SqlToken varNameTok = peek();
                if (varNameTok.type != SqlToken.Type.IDENTIFIER && varNameTok.type != SqlToken.Type.KEYWORD) {
                    throw new RuntimeException("Expected variable name after '@'");
                }
                consume();
                return new VariableExpression("@" + varNameTok.value);
            }
            if (t.type == SqlToken.Type.KEYWORD && "CASE".equalsIgnoreCase(t.value)) {
                return parseCaseExpression();
            }
            if (matchSymbol("(")) {
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
                    if (!matchSymbol(")")) {
                        if (matchSymbol("*")) {
                            args.add(new LiteralExpression("*"));
                        } else {
                            do {
                                if (matchKeyword("INTERVAL")) {
                                    Expression amount = parseExpression();
                                    SqlToken unitToken = consume();
                                    args.add(new LiteralExpression("INTERVAL " + amount.toString() + " " + unitToken.value));
                                } else if (matchKeyword("AS")) {
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
                return new ColumnExpression(name);
            }
            throw new RuntimeException("Unexpected token: " + t);
        }

        private Expression parseCaseExpression() {
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
