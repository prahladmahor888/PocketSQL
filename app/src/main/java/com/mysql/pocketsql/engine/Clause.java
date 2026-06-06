package com.mysql.pocketsql.engine;

import java.util.List;
import java.util.Map;

public class Clause {

    public static class Where {
        public final String column;
        public final String operator; // =, !=, <>, >, <, >=, <=, LIKE, IN, BETWEEN, IS NULL, IS NOT NULL
        public final Object value;    // Single value (String, Long, Double, etc.)
        public final List<Object> listValues; // For IN clause
        public final Object lowValue;  // For BETWEEN clause
        public final Object highValue; // For BETWEEN clause
        public final boolean isValueColumn;
        public final String logicalOperator; // AND, OR, NOT
        public final List<Where> subConditions;

        public Where(String logicalOperator, List<Where> subConditions) {
            this.column = null;
            this.operator = null;
            this.value = null;
            this.listValues = null;
            this.lowValue = null;
            this.highValue = null;
            this.isValueColumn = false;
            this.logicalOperator = logicalOperator.toUpperCase();
            this.subConditions = subConditions;
        }

        public Where(String column, String operator, Object value) {
            this(column, operator, value, false);
        }

        public Where(String column, String operator, Object value, boolean isValueColumn) {
            this.column = column;
            this.operator = operator.toUpperCase();
            this.value = value;
            this.listValues = null;
            this.lowValue = null;
            this.highValue = null;
            this.isValueColumn = isValueColumn;
            this.logicalOperator = null;
            this.subConditions = null;
        }

        public Where(String column, String operator, List<Object> listValues) {
            this.column = column;
            this.operator = operator.toUpperCase();
            this.value = null;
            this.listValues = listValues;
            this.lowValue = null;
            this.highValue = null;
            this.isValueColumn = false;
            this.logicalOperator = null;
            this.subConditions = null;
        }

        public Where(String column, String operator, Object lowValue, Object highValue) {
            this.column = column;
            this.operator = operator.toUpperCase();
            this.value = null;
            this.listValues = null;
            this.lowValue = lowValue;
            this.highValue = highValue;
            this.isValueColumn = false;
            this.logicalOperator = null;
            this.subConditions = null;
        }

        public boolean evaluate(Map<String, Object> row) {
            return evaluate(row, null, null);
        }

        public boolean evaluate(Map<String, Object> row, String collation) {
            return evaluate(row, collation, null);
        }

        public boolean evaluate(Map<String, Object> row, String collation, DatabaseEngine engine) {
            if (logicalOperator != null) {
                if ("AND".equals(logicalOperator)) {
                    for (Where sub : subConditions) {
                        if (!sub.evaluate(row, collation, engine)) return false;
                    }
                    return true;
                }
                if ("OR".equals(logicalOperator)) {
                    for (Where sub : subConditions) {
                        if (sub.evaluate(row, collation, engine)) return true;
                    }
                    return false;
                }
                if ("NOT".equals(logicalOperator)) {
                    return !subConditions.get(0).evaluate(row, collation, engine);
                }
                return false;
            }

            Object rowVal = DatabaseEngine.getRowValue(row, column);

            // Handle IS NULL / IS NOT NULL
            if ("IS NULL".equals(operator)) {
                return rowVal == null;
            }
            if ("IS NOT NULL".equals(operator)) {
                return rowVal != null;
            }

            Object comparisonValue = value;
            if (isValueColumn && value instanceof String) {
                String valCol = (String) value;
                if (valCol.startsWith("@")) {
                    comparisonValue = engine != null ? engine.getUserVariable(valCol) : null;
                } else if (valCol.contains("(")) {
                    comparisonValue = SqlFunctions.evaluate(valCol, row, engine);
                } else {
                    comparisonValue = DatabaseEngine.getRowValue(row, valCol);
                }
            }

            if (rowVal == null) {
                if (comparisonValue == null || "NULL".equals(comparisonValue)) {
                    return "=".equals(operator);
                } else {
                    return "!=".equals(operator) || "<>".equals(operator);
                }
            }

            // Handle IN operator
            if ("IN".equals(operator)) {
                return SqlOperator.evaluateIn(rowVal, listValues, collation);
            }

            // Handle BETWEEN operator
            if ("BETWEEN".equals(operator)) {
                return SqlOperator.evaluateBetween(rowVal, lowValue, highValue, collation);
            }

            // Handle LIKE operator
            if ("LIKE".equals(operator)) {
                return SqlOperator.compare(rowVal, "LIKE", comparisonValue, collation);
            }

            if (comparisonValue == null || "NULL".equals(comparisonValue)) {
                return "!=".equals(operator) || "<>".equals(operator);
            }

            // Delegate comparison to SqlOperator
            return SqlOperator.compare(rowVal, operator, comparisonValue, collation);
        }
    }

    public static class OrderBy {
        public final String column;
        public final boolean asc;

        public OrderBy(String column, boolean asc) {
            this.column = column;
            this.asc = asc;
        }
    }

    public static class Limit {
        public final int limit;

        public Limit(int limit) {
            this.limit = limit;
        }
    }

    public static class GroupBy {
        public final String column;

        public GroupBy(String column) {
            this.column = column;
        }
    }

    public static class Having {
        public final String aggregateFunc;
        public final String operator;
        public final Object value;

        public Having(String aggregateFunc, String operator, Object value) {
            this.aggregateFunc = aggregateFunc;
            this.operator = operator.toUpperCase();
            this.value = value;
        }

        public boolean evaluate(Map<String, Object> groupRow) {
            Object rowVal = groupRow.get(aggregateFunc);
            if (rowVal == null) return false;

            double rNum;
            double vNum;
            try {
                rNum = Double.parseDouble(rowVal.toString());
                vNum = Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return false;
            }

            switch (operator) {
                case "=": return rNum == vNum;
                case "!=":
                case "<>": return rNum != vNum;
                case ">": return rNum > vNum;
                case "<": return rNum < vNum;
                case ">=": return rNum >= vNum;
                case "<=": return rNum <= vNum;
                default: return false;
            }
        }
    }

    public static class Join {
        public final String table;
        public final String type; // INNER, LEFT, RIGHT, CROSS
        public final String leftCol;
        public final String rightCol;
        public final String alias;
        public final List<Clause.Where> extraConditions;

        public Join(String table, String type, String leftCol, String rightCol) {
            this(table, type, leftCol, rightCol, null);
        }

        public Join(String table, String type, String leftCol, String rightCol, String alias) {
            this(table, type, leftCol, rightCol, alias, new java.util.ArrayList<Clause.Where>());
        }

        public Join(String table, String type, String leftCol, String rightCol, String alias, List<Clause.Where> extraConditions) {
            this.table = table;
            this.type = type.toUpperCase();
            this.leftCol = leftCol;
            this.rightCol = rightCol;
            this.alias = alias;
            this.extraConditions = extraConditions;
        }
    }

    public static class Union {
        public final Command.Select selectQuery;
        public final boolean all;

        public Union(Command.Select selectQuery, boolean all) {
            this.selectQuery = selectQuery;
            this.all = all;
        }
    }
}
