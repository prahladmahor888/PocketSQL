package com.mysql.pocketsql.engine;

import java.util.List;

/**
 * SqlKey — Database Key aur Index Structure Models
 *
 * MySQL me alag-alag types ke keys hote hain jo data ko
 * identify, link, aur optimize karne ke liye use hote hain:
 *
 *   PRIMARY KEY  — Unique row identifier
 *   FOREIGN KEY  — Dusri table se link
 *   UNIQUE KEY   — Duplicate values allow nahi
 *   INDEX KEY    — Query performance ke liye
 *   FULLTEXT KEY — Text search ke liye
 *   SPATIAL KEY  — Geographic data ke liye
 *
 * Ye class inke metadata models define karti hai.
 */
public class SqlKey {

    // ──────────────────────────────────────────────────────────
    // Key type enum
    // ──────────────────────────────────────────────────────────

    public enum Type {
        /** Table me primary identifier column(s) */
        PRIMARY,
        /** Dusri table ke PRIMARY KEY se reference */
        FOREIGN,
        /** Unique value constraint with index */
        UNIQUE,
        /** Regular query-optimization index */
        INDEX,
        /** Full-text search index */
        FULLTEXT,
        /** Spatial/geographic data index */
        SPATIAL
    }

    // ──────────────────────────────────────────────────────────
    // PrimaryKey — ek ya zyada columns jo row uniquely identify kare
    // ──────────────────────────────────────────────────────────

    public static class PrimaryKey {
        /** Primary key columns (composite support ke liye list) */
        public final List<String> columns;

        public PrimaryKey(List<String> columns) {
            this.columns = columns;
        }

        /**
         * Single-column primary key hai ya nahi check karta hai.
         */
        public boolean isComposite() {
            return columns != null && columns.size() > 1;
        }

        @Override
        public String toString() {
            return "PRIMARY KEY (" + String.join(", ", columns) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // ForeignKey — parent table ke column se reference karta hai
    // ──────────────────────────────────────────────────────────

    public static class ForeignKey {
        /** Child table ka column jo reference karta hai */
        public final String column;
        /** Parent table ka naam */
        public final String parentTable;
        /** Parent table ka referenced column */
        public final String parentColumn;

        public ForeignKey(String column, String parentTable, String parentColumn) {
            this.column      = column;
            this.parentTable = parentTable;
            this.parentColumn = parentColumn;
        }

        /**
         * Reference string return karta hai: "parentTable.parentColumn"
         */
        public String getReference() {
            return parentTable + "." + parentColumn;
        }

        @Override
        public String toString() {
            return "FOREIGN KEY (" + column + ") REFERENCES " + parentTable + "(" + parentColumn + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // UniqueKey — duplicate values allow nahi karta
    // ──────────────────────────────────────────────────────────

    public static class UniqueKey {
        /** Optional index name */
        public final String indexName;
        /** Unique constraint ke columns */
        public final List<String> columns;

        public UniqueKey(String indexName, List<String> columns) {
            this.indexName = indexName;
            this.columns   = columns;
        }

        @Override
        public String toString() {
            String name = (indexName != null && !indexName.isEmpty()) ? indexName + " " : "";
            return "UNIQUE KEY " + name + "(" + String.join(", ", columns) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // IndexKey — query performance improve karne ke liye
    // ──────────────────────────────────────────────────────────

    public static class IndexKey {
        /** Key type (INDEX, FULLTEXT, SPATIAL) */
        public final Type type;
        /** Optional index name */
        public final String indexName;
        /** Indexed columns */
        public final List<String> columns;

        public IndexKey(Type type, String indexName, List<String> columns) {
            this.type      = type;
            this.indexName = indexName;
            this.columns   = columns;
        }

        @Override
        public String toString() {
            String name = (indexName != null && !indexName.isEmpty()) ? indexName + " " : "";
            return type.name() + " KEY " + name + "(" + String.join(", ", columns) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────
    // CompositeKey — multiple columns ka ek key
    // ──────────────────────────────────────────────────────────

    public static class CompositeKey {
        /** Key type (PRIMARY or UNIQUE) */
        public final Type type;
        /** Composite key columns */
        public final List<String> columns;

        public CompositeKey(Type type, List<String> columns) {
            this.type    = type;
            this.columns = columns;
        }

        @Override
        public String toString() {
            return type.name() + " KEY (" + String.join(", ", columns) + ")";
        }
    }
}
