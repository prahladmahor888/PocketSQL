package com.mysql.pocketsql.engine;

import java.util.List;
import java.util.ArrayList;

/**
 * SqlOperator — Centralized SQL Operator Evaluation Engine
 *
 * MySQL me SQL operators teen tarikon se kaam karte hain:
 *   1. Arithmetic  : +, -, *, /, %
 *   2. Comparison  : =, !=, <>, >, <, >=, <=
 *   3. Logical     : AND, OR, NOT
 *   4. Special     : LIKE, IN, BETWEEN, IS NULL, IS NOT NULL
 *
 * Ye class inhe centralize karti hai taaki DatabaseEngine,
 * SqlFunctions, aur Clause sab ek hi jagah se evaluate karein.
 */
public class SqlOperator {

    // ──────────────────────────────────────────────────────────
    // Operator type enum
    // ──────────────────────────────────────────────────────────

    public enum Type {
        /** +  -  *  /  % */
        ARITHMETIC,
        /** |  &  ^  <<  >>  ~ */
        BITWISE,
        /** =  !=  <>  >  <  >=  <= */
        COMPARISON,
        /** AND  OR  NOT */
        LOGICAL,
        /** LIKE  IN  BETWEEN  IS NULL  IS NOT NULL */
        SPECIAL
    }

    // ──────────────────────────────────────────────────────────
    // Classify an operator string
    // ──────────────────────────────────────────────────────────

    public static Type classify(String op) {
        if (op == null) return null;
        switch (op.toUpperCase()) {
            case "+": case "-": case "*": case "/": case "%":
                return Type.ARITHMETIC;
            case "|": case "&": case "^": case "<<": case ">>": case "~":
                return Type.BITWISE;
            case "=": case "!=": case "<>":
            case ">": case "<": case ">=": case "<=":
                return Type.COMPARISON;
            case "AND": case "OR": case "NOT":
                return Type.LOGICAL;
            case "LIKE": case "IN": case "BETWEEN":
            case "IS NULL": case "IS NOT NULL":
                return Type.SPECIAL;
            default:
                return null;
        }
    }

    // ──────────────────────────────────────────────────────────
    // Binary evaluation: left  op  right
    // ──────────────────────────────────────────────────────────

    /**
     * Do values evaluate karta hai ek operator ke saath.
     *
     * @param op    Operator string (e.g. "+", "=", "AND", "LIKE")
     * @param left  Left operand
     * @param right Right operand
     * @return      Evaluated result (Number for arithmetic, Boolean for comparisons/logical)
     */
    public static Object evaluateBinary(String op, Object left, Object right) {
        return evaluateBinary(op, left, right, null);
    }

    public static Object evaluateBinary(String op, Object left, Object right, String collation) {
        if (op == null) return null;
        String opUpper = op.toUpperCase();
        Type type = classify(op);

        if (type == Type.LOGICAL) {
            if ("AND".equals(opUpper)) return isTruthy(left) && isTruthy(right);
            if ("OR".equals(opUpper))  return isTruthy(left) || isTruthy(right);
        }

        if (type == Type.BITWISE) {
            if (left == null || right == null) return null;
            long l = (long) toDouble(left);
            long r = (long) toDouble(right);
            switch (op) {
                case "|": return l | r;
                case "&": return l & r;
                case "^": return l ^ r;
                case "<<": return l << r;
                case ">>": return l >> r;
            }
        }

        if (type == Type.ARITHMETIC) {
            if (left == null || right == null) return null;
            if ("-".equals(op) || "+".equals(op)) {
                String rStr = right.toString().trim();
                if (rStr.toUpperCase().startsWith("INTERVAL ") || rStr.toUpperCase().contains(" DAY") || rStr.toUpperCase().contains(" MONTH") || rStr.toUpperCase().contains(" YEAR")) {
                    String funcName = "-".equals(op) ? "DATE_SUB" : "DATE_ADD";
                    List<Object> args = new ArrayList<>();
                    args.add(left);
                    args.add(rStr);
                    return SqlFunctions.evaluateScalarFunction(funcName, args, null);
                }
            }
            double l = toDouble(left);
            double r = toDouble(right);
            switch (op) {
                case "+": return l + r;
                case "-": return l - r;
                case "*": return l * r;
                case "/": return (r == 0) ? null : l / r;
                case "%": return (r == 0) ? null : l % r;
            }
        }

        if (type == Type.SPECIAL && "LIKE".equals(opUpper)) {
            return compareLike(left, right, collation);
        }

        if (type == Type.COMPARISON) {
            return compareValues(opUpper, left, right, collation);
        }

        return null;
    }

    // ──────────────────────────────────────────────────────────
    // Unary evaluation: NOT val
    // ──────────────────────────────────────────────────────────

    public static class BinaryVal {
        public final String value;
        public BinaryVal(String value) { this.value = value; }
        @Override public String toString() { return value == null ? "" : value; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;
            return value != null && value.equals(o.toString());
        }
        @Override public int hashCode() { return value != null ? value.hashCode() : 0; }
    }

    public static Object evaluateUnary(String op, Object val) {
        if (op == null) return null;
        if ("NOT".equalsIgnoreCase(op)) return !isTruthy(val);
        if ("BINARY".equalsIgnoreCase(op)) return val != null ? new BinaryVal(val.toString()) : null;
        if ("~".equals(op)) {
            if (val == null) return null;
            return ~(long) toDouble(val);
        }
        if ("-".equals(op)) {
            if (val == null) return null;
            return -toDouble(val);
        }
        return val;
    }

    // ──────────────────────────────────────────────────────────
    // IN operator: value list ke andar hai ya nahi
    // ──────────────────────────────────────────────────────────

    public static boolean evaluateIn(Object rowVal, List<Object> listValues) {
        return evaluateIn(rowVal, listValues, null);
    }

    public static boolean evaluateIn(Object rowVal, List<Object> listValues, String collation) {
        if (listValues == null || rowVal == null) return false;
        String rStr = rowVal.toString().trim().replaceAll("^'|'$", "");
        for (Object item : listValues) {
            if (item != null) {
                String iStr = item.toString().trim().replaceAll("^'|'$", "");
                if (SqlCollation.compare(rStr, iStr, collation) == 0) return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────
    // BETWEEN operator: low <= value <= high
    // ──────────────────────────────────────────────────────────

    public static boolean evaluateBetween(Object rowVal, Object lowValue, Object highValue) {
        return evaluateBetween(rowVal, lowValue, highValue, null);
    }

    public static boolean evaluateBetween(Object rowVal, Object lowValue, Object highValue, String collation) {
        if (rowVal == null || lowValue == null || highValue == null) return false;
        try {
            double r   = Double.parseDouble(rowVal.toString());
            double low = Double.parseDouble(lowValue.toString());
            double high = Double.parseDouble(highValue.toString());
            return r >= low && r <= high;
        } catch (NumberFormatException e) {
            String r   = rowVal.toString();
            String low = lowValue.toString();
            String high = highValue.toString();
            return SqlCollation.compare(r, low, collation) >= 0 && SqlCollation.compare(r, high, collation) <= 0;
        }
    }

    // ──────────────────────────────────────────────────────────
    // Core compare helper (used by Clause.Where, BinaryOpExpression)
    // ──────────────────────────────────────────────────────────

    public static boolean compare(Object lVal, String op, Object rVal) {
        return compare(lVal, op, rVal, null);
    }

    public static boolean compare(Object lVal, String op, Object rVal, String collation) {
        if (op == null) return false;
        String opUpper = op.toUpperCase();
        Type type = classify(op);
        if (type == Type.SPECIAL && "LIKE".equals(opUpper)) {
            return compareLike(lVal, rVal, collation);
        }
        if (type == Type.COMPARISON) {
            return compareValues(opUpper, lVal, rVal, collation);
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────
    // isTruthy: koi bhi value truthy hai ya nahi check karta hai
    // ──────────────────────────────────────────────────────────

    public static boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).doubleValue() != 0.0;
        String s = val.toString().trim();
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        try { return Double.parseDouble(s) != 0.0; }
        catch (NumberFormatException e) { return !s.isEmpty(); }
    }

    // ──────────────────────────────────────────────────────────
    // toDouble helper: Object ko double me convert karta hai
    // ──────────────────────────────────────────────────────────

    public static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    // ──────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────

    public static boolean compareLike(Object lVal, Object rVal) {
        return compareLike(lVal, rVal, null);
    }

    public static boolean compareLike(Object lVal, Object rVal, String collation) {
        if (lVal == null || rVal == null) return false;
        String collLower = (collation != null) ? collation.toLowerCase().trim() : "utf8mb4_0900_ai_ci";
        boolean accentInsensitive = collLower.contains("_ai") || collLower.contains("general_ci") || collLower.contains("unicode_ci");
        boolean caseInsensitive = collLower.contains("_ci");

        String lStr  = lVal.toString();
        String patStr = rVal.toString().replaceAll("^'|'$|\"|\"", "");
        
        if (accentInsensitive) {
            lStr = SqlCollation.stripAccents(lStr);
            patStr = SqlCollation.stripAccents(patStr);
        }
        
        if (caseInsensitive) {
            lStr = lStr.toLowerCase();
            patStr = patStr.toLowerCase();
        }

        if (patStr.startsWith("%") && patStr.endsWith("%")) {
            return lStr.contains(patStr.substring(1, patStr.length() - 1));
        } else if (patStr.startsWith("%")) {
            return lStr.endsWith(patStr.substring(1));
        } else if (patStr.endsWith("%")) {
            return lStr.startsWith(patStr.substring(0, patStr.length() - 1));
        } else {
            return lStr.equals(patStr);
        }
    }

    public static boolean compareValues(String op, Object lVal, Object rVal) {
        return compareValues(op, lVal, rVal, null);
    }

    public static boolean compareValues(String op, Object lVal, Object rVal, String collation) {
        // NULL handling
        if (lVal == null || rVal == null) {
            if ("=".equals(op))  return lVal == rVal;
            if ("!=".equals(op) || "<>".equals(op)) return lVal != rVal;
            return false;
        }

        // Try numeric comparison
        boolean isNumeric = false;
        double lNum = 0.0, rNum = 0.0;
        if (lVal instanceof Number) {
            isNumeric = true;
            lNum = ((Number) lVal).doubleValue();
            if (rVal instanceof Number) rNum = ((Number) rVal).doubleValue();
            else { try { rNum = Double.parseDouble(rVal.toString()); } catch (Exception e) { isNumeric = false; } }
        } else if (rVal instanceof Number) {
            try { lNum = Double.parseDouble(lVal.toString()); rNum = ((Number) rVal).doubleValue(); isNumeric = true; }
            catch (Exception e) { isNumeric = false; }
        } else {
            try { lNum = Double.parseDouble(lVal.toString()); rNum = Double.parseDouble(rVal.toString()); isNumeric = true; }
            catch (Exception e) { isNumeric = false; }
        }

        if (isNumeric) {
            switch (op) {
                case "=":  return lNum == rNum;
                case "!=":
                case "<>": return lNum != rNum;
                case ">":  return lNum > rNum;
                case "<":  return lNum < rNum;
                case ">=": return lNum >= rNum;
                case "<=": return lNum <= rNum;
            }
        } else {
            String lStr = lVal.toString();
            String rStr = rVal.toString();
            int cmp = (lVal instanceof BinaryVal || rVal instanceof BinaryVal)
                    ? lStr.compareTo(rStr)
                    : SqlCollation.compare(lStr, rStr, collation);
            switch (op) {
                case "=":  return cmp == 0;
                case "!=":
                case "<>": return cmp != 0;
                case ">":  return cmp > 0;
                case "<":  return cmp < 0;
                case ">=": return cmp >= 0;
                case "<=": return cmp <= 0;
            }
        }
        return false;
    }
}
