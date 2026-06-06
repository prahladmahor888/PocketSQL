package com.mysql.pocketsql.engine;

import org.json.JSONObject;

public class SqlDefaults {

    public static boolean isCurrentTimestampFunction(String valStr) {
        if (valStr == null) return false;
        String upper = valStr.toUpperCase().trim();
        return "CURRENT_TIMESTAMP".equals(upper) ||
               "CURRENT_TIMESTAMP()".equals(upper) ||
               "NOW".equals(upper) ||
               "NOW()".equals(upper);
    }

    public static Object getDefaultValue(JSONObject tableSchema, String colName) {
        if (tableSchema == null) return null;

        try {
            JSONObject defaultsObj = tableSchema.optJSONObject("defaults");
            if (defaultsObj != null && defaultsObj.has(colName)) {
                Object raw = defaultsObj.get(colName);
                if (raw == JSONObject.NULL) {
                    return null;
                }
                String valStr = raw.toString();
                if (isCurrentTimestampFunction(valStr)) {
                    return getCurrentTimestampString();
                }
                if ("NULL".equalsIgnoreCase(valStr)) {
                    return null;
                }
                return valStr;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String getCurrentTimestampString() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }
}
