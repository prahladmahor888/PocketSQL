package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableData {
    public final String tableName;
    public final List<String> columns;
    public final List<String> types;
    public final List<Map<String, Object>> rows;
    public boolean isDirty;

    // Cache maps for O(1) validations and increments
    public final Map<String, Long> autoIncrementCounters = new HashMap<>();
    public final Map<List<String>, UniqueIndex> uniqueIndexes = new HashMap<>();

    public static class UniqueIndex {
        public final List<String> columns;
        public final List<String> collations;
        public final Set<List<Object>> keys = new HashSet<>();

        public UniqueIndex(List<String> columns, List<String> collations) {
            this.columns = columns;
            this.collations = collations;
        }
    }

    public TableData(String tableName, List<String> columns, List<String> types) {
        this.tableName = tableName;
        this.columns = columns;
        this.types = types;
        this.rows = new IndexedRowList();
        this.isDirty = false;
    }

    public void loadFromJSON(JSONArray array) throws Exception {
        rows.clear();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Map<String, Object> row = new HashMap<>();
            for (String col : columns) {
                if (obj.has(col)) {
                    Object val = obj.get(col);
                    if (val == JSONObject.NULL) {
                        row.put(col, null);
                    } else {
                        // Cast JSON types appropriately
                        if (val instanceof Integer) {
                            row.put(col, ((Integer) val).longValue());
                        } else {
                            row.put(col, val);
                        }
                    }
                } else {
                    row.put(col, null);
                }
            }
            rows.add(row);
        }
        isDirty = false;
    }

    public JSONArray toJSONArray() throws Exception {
        JSONArray array = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            for (String col : columns) {
                Object val = row.get(col);
                if (val == null) {
                    obj.put(col, JSONObject.NULL);
                } else {
                    obj.put(col, val);
                }
            }
            array.put(obj);
        }
        return array;
    }

    public UniqueIndex getOrCreateUniqueIndex(List<String> columns, String tableName, DatabaseEngine engine) {
        UniqueIndex index = uniqueIndexes.get(columns);
        if (index == null) {
            List<String> collations = new ArrayList<>(columns.size());
            for (String col : columns) {
                collations.add(engine.getColumnCollation(tableName, col));
            }
            index = new UniqueIndex(columns, collations);
            // Populate index
            for (Map<String, Object> row : rows) {
                List<Object> key = makeIndexKey(row, columns, collations);
                if (key != null) {
                    index.keys.add(key);
                }
            }
            uniqueIndexes.put(columns, index);
        }
        return index;
    }

    public List<Object> makeIndexKey(Map<String, Object> row, List<String> columns, List<String> collations) {
        List<Object> key = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            Object val = DatabaseEngine.getRowValue(row, col);
            if (val == null) {
                return null;
            }
            key.add(getCollationKey(val, collations.get(i)));
        }
        return key;
    }

    public static Object getCollationKey(Object val, String collation) {
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        String s = val.toString();
        String collLower = (collation != null) ? collation.toLowerCase().trim() : "utf8mb4_0900_ai_ci";
        boolean accentInsensitive = collLower.contains("_ai") || collLower.contains("general_ci") || collLower.contains("unicode_ci");
        boolean caseInsensitive = collLower.contains("_ci");
        boolean isBinary = collLower.contains("_bin") || "binary".equals(collLower);

        if (isBinary) {
            return s;
        }
        if (accentInsensitive) {
            s = SqlCollation.stripAccents(s);
        }
        if (caseInsensitive) {
            s = s.toLowerCase();
        }
        return s;
    }

    private void onRowAdded(Map<String, Object> row) {
        // Update auto-increment counters
        for (String col : columns) {
            Object val = row.get(col);
            if (val != null) {
                long numVal = -1;
                if (val instanceof Number) {
                    numVal = ((Number) val).longValue();
                } else {
                    try {
                        numVal = Long.parseLong(val.toString().trim());
                    } catch (NumberFormatException ignored) {}
                }
                if (numVal >= 0) {
                    Long currentMax = autoIncrementCounters.get(col);
                    if (currentMax == null || numVal > currentMax) {
                        autoIncrementCounters.put(col, numVal);
                    }
                }
            }
        }

        // Update unique indexes
        for (UniqueIndex index : uniqueIndexes.values()) {
            List<Object> key = makeIndexKey(row, index.columns, index.collations);
            if (key != null) {
                index.keys.add(key);
            }
        }
    }

    private void onRowRemoved(Map<String, Object> row) {
        // Update unique indexes
        for (UniqueIndex index : uniqueIndexes.values()) {
            List<Object> key = makeIndexKey(row, index.columns, index.collations);
            if (key != null) {
                index.keys.remove(key);
            }
        }
    }

    private class IndexedRowList extends AbstractList<Map<String, Object>> {
        private final List<Map<String, Object>> delegate = new ArrayList<>();

        @Override
        public Map<String, Object> get(int index) {
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Map<String, Object> set(int index, Map<String, Object> element) {
            Map<String, Object> old = delegate.set(index, element);
            onRowRemoved(old);
            onRowAdded(element);
            return old;
        }

        @Override
        public void add(int index, Map<String, Object> element) {
            delegate.add(index, element);
            onRowAdded(element);
        }

        @Override
        public boolean add(Map<String, Object> element) {
            boolean ret = delegate.add(element);
            if (ret) {
                onRowAdded(element);
            }
            return ret;
        }

        @Override
        public Map<String, Object> remove(int index) {
            Map<String, Object> removed = delegate.remove(index);
            onRowRemoved(removed);
            return removed;
        }

        @Override
        public boolean remove(Object o) {
            int index = delegate.indexOf(o);
            if (index >= 0) {
                remove(index);
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            delegate.clear();
            autoIncrementCounters.clear();
            uniqueIndexes.clear();
        }

        @Override
        public boolean addAll(java.util.Collection<? extends Map<String, Object>> c) {
            boolean modified = false;
            for (Map<String, Object> e : c) {
                if (add(e)) {
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean addAll(int index, java.util.Collection<? extends Map<String, Object>> c) {
            boolean modified = false;
            int i = index;
            for (Map<String, Object> e : c) {
                add(i++, e);
                modified = true;
            }
            return modified;
        }
    }
}
