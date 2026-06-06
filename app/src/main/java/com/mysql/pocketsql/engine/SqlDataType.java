package com.mysql.pocketsql.engine;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public abstract class SqlDataType {
    public abstract Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception;

    protected static long parseLongStrict(String colName, Object val) throws Exception {
        if (val instanceof Number) {
            double d = ((Number) val).doubleValue();
            if (d % 1 != 0)
                throw new Exception("Cannot store decimal " + val + " into integer column '" + colName + "'");
            return ((Number) val).longValue();
        }
        try { return Long.parseLong(val.toString().trim()); }
        catch (NumberFormatException e) {
            throw new Exception("Cannot convert '" + val + "' to integer for column '" + colName + "'");
        }
    }

    private static final Map<String, SqlDataType> REGISTRY = new HashMap<>();

    static {
        // Numeric
        REGISTRY.put("TINYINT", new TinyIntType());
        REGISTRY.put("SMALLINT", new SmallIntType());
        REGISTRY.put("MEDIUMINT", new MediumIntType());
        REGISTRY.put("INT", new IntType());
        REGISTRY.put("INTEGER", new IntType());
        REGISTRY.put("BIGINT", new BigIntType());
        REGISTRY.put("BIT", new BitType());
        REGISTRY.put("YEAR", new YearType());
        REGISTRY.put("FLOAT", new FloatType());
        REGISTRY.put("DOUBLE", new DoubleType());
        REGISTRY.put("REAL", new FloatType());
        REGISTRY.put("DECIMAL", new DecimalType());
        REGISTRY.put("NUMERIC", new DecimalType());

        // Strings
        REGISTRY.put("CHAR", new CharType());
        REGISTRY.put("VARCHAR", new VarCharType());
        
        TextType text = new TextType();
        REGISTRY.put("TEXT", text);
        REGISTRY.put("TINYTEXT", text);
        REGISTRY.put("MEDIUMTEXT", text);
        REGISTRY.put("LONGTEXT", text);

        BinaryType binary = new BinaryType();
        REGISTRY.put("BINARY", binary);
        REGISTRY.put("VARBINARY", binary);

        BlobType blob = new BlobType();
        REGISTRY.put("BLOB", blob);
        REGISTRY.put("TINYBLOB", blob);
        REGISTRY.put("MEDIUMBLOB", blob);
        REGISTRY.put("LONGBLOB", blob);

        REGISTRY.put("JSON", new JsonType());

        GeometryType geom = new GeometryType();
        REGISTRY.put("GEOMETRY", geom);
        REGISTRY.put("POINT", geom);
        REGISTRY.put("LINESTRING", geom);
        REGISTRY.put("POLYGON", geom);
        REGISTRY.put("MULTIPOINT", geom);
        REGISTRY.put("MULTILINESTRING", geom);
        REGISTRY.put("MULTIPOLYGON", geom);
        REGISTRY.put("GEOMETRYCOLLECTION", geom);

        REGISTRY.put("ENUM", new EnumType());
        REGISTRY.put("SET", new SetType());

        // Dates / Times
        REGISTRY.put("DATE", new DateType());
        REGISTRY.put("TIME", new TimeType());
        
        DateTimeType dt = new DateTimeType();
        REGISTRY.put("DATETIME", dt);
        REGISTRY.put("TIMESTAMP", dt);
    }

    public static Object validateAndConvertType(String colName, Object val, String rawType) throws Exception {
        if (val == null) return null;

        String type = rawType.toUpperCase();
        String baseType = type.contains("(") ? type.substring(0, type.indexOf('(')).trim() : type;
        String sizeArg = "";
        if (type.contains("(") && type.contains(")")) {
            sizeArg = type.substring(type.indexOf('(') + 1, type.lastIndexOf(')'));
        }

        // Handle types containing "UNSIGNED" (e.g. "INT UNSIGNED" or "INT(10) UNSIGNED")
        boolean isUnsigned = false;
        if (baseType.endsWith(" UNSIGNED")) {
            baseType = baseType.substring(0, baseType.length() - " UNSIGNED".length()).trim();
            isUnsigned = true;
        } else if (baseType.contains("UNSIGNED")) {
            baseType = baseType.replace("UNSIGNED", "").trim();
            isUnsigned = true;
        }

        SqlDataType dataType = REGISTRY.get(baseType);
        if (dataType != null) {
            Object converted = dataType.validateAndConvert(colName, val, sizeArg);
            if (isUnsigned) {
                SqlAttributes.validateUnsigned(colName, converted);
            }
            return converted;
        }
        
        return val.toString();
    }

    // --- Static Nested Classes for Datatypes ---

    private static class TinyIntType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            long v = parseLongStrict(colName, val);
            if (v < -128 || v > 255)
                throw new Exception("TINYINT out of range (-128..255) for column '" + colName + "': " + v);
            return v;
        }
    }

    private static class SmallIntType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            long v = parseLongStrict(colName, val);
            if (v < -32768 || v > 65535)
                throw new Exception("SMALLINT out of range for column '" + colName + "': " + v);
            return v;
        }
    }

    private static class MediumIntType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            long v = parseLongStrict(colName, val);
            if (v < -8388608 || v > 16777215)
                throw new Exception("MEDIUMINT out of range for column '" + colName + "': " + v);
            return v;
        }
    }

    private static class IntType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return parseLongStrict(colName, val);
        }
    }

    private static class BigIntType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return parseLongStrict(colName, val);
        }
    }

    private static class BitType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return parseLongStrict(colName, val);
        }
    }

    private static class YearType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            long v = parseLongStrict(colName, val);
            if (v < 1901 || v > 2155)
                throw new Exception("YEAR out of range (1901..2155) for column '" + colName + "': " + v);
            return v;
        }
    }

    private static class FloatType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(val.toString()); }
            catch (NumberFormatException e) {
                throw new Exception("Cannot convert '" + val + "' to numeric for column '" + colName + "'");
            }
        }
    }

    private static class DoubleType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(val.toString()); }
            catch (NumberFormatException e) {
                throw new Exception("Cannot convert '" + val + "' to DOUBLE for column '" + colName + "'");
            }
        }
    }

    private static class DecimalType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            double d;
            if (val instanceof Number) d = ((Number) val).doubleValue();
            else try { d = Double.parseDouble(val.toString()); }
                 catch (NumberFormatException e) {
                     throw new Exception("Cannot convert '" + val + "' to DECIMAL for column '" + colName + "'");
                 }
            if (!sizeArg.isEmpty() && sizeArg.contains(",")) {
                int scale = Integer.parseInt(sizeArg.split(",")[1].trim());
                double factor = Math.pow(10, scale);
                d = Math.round(d * factor) / factor;
            }
            return d;
        }
    }

    private static class CharType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString();
            if (!sizeArg.isEmpty()) {
                int max = Integer.parseInt(sizeArg.trim());
                if (s.length() > max)
                    throw new Exception("Data too long for CHAR(" + max + ") in column '" + colName + "'");
            }
            return s;
        }
    }

    private static class VarCharType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString();
            if (!sizeArg.isEmpty()) {
                int max = Integer.parseInt(sizeArg.trim());
                if (s.length() > max)
                    throw new Exception("Data too long for VARCHAR(" + max + ") in column '" + colName + "'");
            }
            return s;
        }
    }

    private static class TextType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return val.toString();
        }
    }

    private static class BinaryType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return val.toString();
        }
    }

    private static class BlobType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return val.toString();
        }
    }

    private static class JsonType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return val.toString();
        }
    }

    private static class GeometryType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            return val.toString();
        }
    }

    private static class EnumType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString().trim();
            if (!sizeArg.isEmpty()) {
                boolean found = false;
                for (String opt : sizeArg.split(",")) {
                    String clean = opt.trim().replaceAll("^'|'$", "");
                    if (clean.equalsIgnoreCase(s)) { s = clean; found = true; break; }
                }
                if (!found)
                    throw new Exception("Invalid ENUM value '" + s + "' for column '" + colName + "'. Allowed: " + sizeArg);
            }
            return s;
        }
    }

    private static class SetType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString();
            if (!sizeArg.isEmpty()) {
                Set<String> allowed = new HashSet<>();
                for (String opt : sizeArg.split(","))
                    allowed.add(opt.trim().replaceAll("^'|'$", "").toUpperCase());
                for (String part : s.split(",")) {
                    if (!allowed.contains(part.trim().toUpperCase()))
                        throw new Exception("Invalid SET value '" + part.trim() + "' for column '" + colName + "'");
                }
            }
            return s;
        }
    }

    private static class DateType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString().trim();
            if (!s.matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new Exception("Invalid DATE format for '" + colName + "'. Use YYYY-MM-DD, got: " + s);
            return s;
        }
    }

    private static class TimeType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString().trim();
            if (!s.matches("\\d{2}:\\d{2}(:\\d{2})?"))
                throw new Exception("Invalid TIME format for '" + colName + "'. Use HH:MM:SS, got: " + s);
            return s;
        }
    }

    private static class DateTimeType extends SqlDataType {
        @Override
        public Object validateAndConvert(String colName, Object val, String sizeArg) throws Exception {
            String s = val.toString().trim();
            if (!s.matches("\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?"))
                throw new Exception("Invalid DATETIME format for '" + colName + "'. Use YYYY-MM-DD HH:MM:SS, got: " + s);
            return s;
        }
    }
}
