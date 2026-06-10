package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class SqlSystemDatabaseManager {

    private static final String[] POCKETSQL_SYSTEM_TABLES = {
        "columns_priv",
        "component",
        "db",
        "default_roles",
        "engine_cost",
        "func",
        "general_log",
        "global_grants",
        "gtid_executed",
        "help_category",
        "help_keyword",
        "help_relation",
        "help_topic",
        "innodb_index_stats",
        "innodb_table_stats",
        "ndb_binlog_index",
        "password_history",
        "plugin",
        "procs_priv",
        "proxies_priv",
        "replication_asynchronous_connection_failover",
        "replication_asynchronous_connection_failover_managed",
        "replication_group_configuration_version",
        "replication_group_member_actions",
        "role_edges",
        "server_cost",
        "servers",
        "slave_master_info",
        "slave_relay_log_info",
        "slave_worker_info",
        "slow_log",
        "tables_priv",
        "time_zone",
        "time_zone_leap_second",
        "time_zone_name",
        "time_zone_transition",
        "time_zone_transition_type",
        "user"
    };

    private static final String[] INFO_SCHEMA_SYSTEM_TABLES = {
        "ADMINISTRABLE_ROLE_AUTHORIZATIONS",
        "APPLICABLE_ROLES",
        "CHARACTER_SETS",
        "CHECK_CONSTRAINTS",
        "COLLATION_CHARACTER_SET_APPLICABILITY",
        "COLLATIONS",
        "COLUMN_PRIVILEGES",
        "COLUMN_STATISTICS",
        "COLUMNS",
        "COLUMNS_EXTENSIONS",
        "ENABLED_ROLES",
        "ENGINES",
        "EVENTS",
        "FILES",
        "INNODB_BUFFER_PAGE",
        "INNODB_BUFFER_PAGE_LRU",
        "INNODB_BUFFER_POOL_STATS",
        "INNODB_CACHED_INDEXES",
        "INNODB_CMP",
        "INNODB_CMP_PER_INDEX",
        "INNODB_CMP_PER_INDEX_RESET",
        "INNODB_CMP_RESET",
        "INNODB_CMPMEM",
        "INNODB_CMPMEM_RESET",
        "INNODB_COLUMNS",
        "INNODB_DATAFILES",
        "INNODB_FIELDS",
        "INNODB_FOREIGN",
        "INNODB_FOREIGN_COLS",
        "INNODB_FT_BEING_DELETED",
        "INNODB_FT_CONFIG",
        "INNODB_FT_DEFAULT_STOPWORD",
        "INNODB_FT_DELETED",
        "INNODB_FT_INDEX_CACHE",
        "INNODB_FT_INDEX_TABLE",
        "INNODB_INDEXES",
        "INNODB_METRICS",
        "INNODB_SESSION_TEMP_TABLESPACES",
        "INNODB_TABLES",
        "INNODB_TABLESPACES",
        "INNODB_TABLESPACES_BRIEF",
        "INNODB_TABLESTATS",
        "INNODB_TEMP_TABLE_INFO",
        "INNODB_TRX",
        "INNODB_VIRTUAL",
        "KEY_COLUMN_USAGE",
        "KEYWORDS",
        "OPTIMIZER_TRACE",
        "PARAMETERS",
        "PARTITIONS",
        "PLUGINS",
        "PROCESSLIST",
        "PROFILING",
        "REFERENTIAL_CONSTRAINTS",
        "RESOURCE_GROUPS",
        "ROLE_COLUMN_GRANTS",
        "ROLE_ROUTINE_GRANTS",
        "ROLE_TABLE_GRANTS",
        "ROUTINES",
        "SCHEMA_PRIVILEGES",
        "SCHEMATA",
        "SCHEMATA_EXTENSIONS",
        "ST_GEOMETRY_COLUMNS",
        "ST_SPATIAL_REFERENCE_SYSTEMS",
        "ST_UNITS_OF_MEASURE",
        "STATISTICS",
        "TABLE_CONSTRAINTS",
        "TABLE_CONSTRAINTS_EXTENSIONS",
        "TABLE_PRIVILEGES",
        "TABLES",
        "TABLES_EXTENSIONS",
        "TABLESPACES",
        "TABLESPACES_EXTENSIONS",
        "TRIGGERS",
        "USER_ATTRIBUTES",
        "USER_PRIVILEGES",
        "VIEW_ROUTINE_USAGE",
        "VIEW_TABLE_USAGE",
        "VIEWS"
    };

    private static final String[] SYS_SYSTEM_TABLES = {
        "host_summary",
        "host_summary_by_file_io",
        "host_summary_by_file_io_type",
        "host_summary_by_stages",
        "host_summary_by_statement_latency",
        "host_summary_by_statement_type",
        "innodb_buffer_stats_by_schema",
        "innodb_buffer_stats_by_table",
        "innodb_lock_waits",
        "io_by_thread_by_latency",
        "io_global_by_file_by_bytes",
        "io_global_by_file_by_latency",
        "io_global_by_wait_by_bytes",
        "io_global_by_wait_by_latency",
        "latest_file_io",
        "memory_by_host_by_current_bytes",
        "memory_by_thread_by_current_bytes",
        "memory_by_user_by_current_bytes",
        "memory_global_by_current_bytes",
        "memory_global_total",
        "metrics",
        "processlist",
        "ps_check_lost_instrumentation",
        "schema_auto_increment_columns",
        "schema_index_statistics",
        "schema_object_overview",
        "schema_redundant_indexes",
        "schema_table_lock_waits",
        "schema_table_statistics",
        "schema_table_statistics_with_buffer",
        "schema_tables_with_full_table_scans",
        "schema_unused_indexes",
        "session",
        "session_ssl_status",
        "statement_analysis",
        "statements_with_errors_or_warnings",
        "statements_with_full_table_scans",
        "statements_with_runtimes_in_95th_percentile",
        "statements_with_sorting",
        "statements_with_temp_tables",
        "sys_config",
        "user_summary",
        "user_summary_by_file_io",
        "user_summary_by_file_io_type",
        "user_summary_by_stages",
        "user_summary_by_statement_latency",
        "user_summary_by_statement_type",
        "version",
        "wait_classes_global_by_avg_latency",
        "wait_classes_global_by_latency",
        "waits_by_host_by_latency",
        "waits_by_user_by_latency",
        "waits_global_by_latency",
        "x$host_summary",
        "x$host_summary_by_file_io",
        "x$host_summary_by_file_io_type",
        "x$host_summary_by_stages",
        "x$host_summary_by_statement_latency",
        "x$host_summary_by_statement_type",
        "x$innodb_buffer_stats_by_schema",
        "x$innodb_buffer_stats_by_table",
        "x$innodb_lock_waits",
        "x$io_by_thread_by_latency",
        "x$io_global_by_file_by_bytes",
        "x$io_global_by_file_by_latency",
        "x$io_global_by_wait_by_bytes",
        "x$io_global_by_wait_by_latency",
        "x$latest_file_io",
        "x$memory_by_host_by_current_bytes",
        "x$memory_by_thread_by_current_bytes",
        "x$memory_by_user_by_current_bytes",
        "x$memory_global_by_current_bytes",
        "x$memory_global_total",
        "x$processlist",
        "x$ps_digest_95th_percentile_by_avg_us",
        "x$ps_digest_avg_latency_distribution",
        "x$ps_schema_table_statistics_io",
        "x$schema_flattened_keys",
        "x$schema_index_statistics",
        "x$schema_table_lock_waits",
        "x$schema_table_statistics",
        "x$schema_table_statistics_with_buffer",
        "x$schema_tables_with_full_table_scans",
        "x$session",
        "x$statement_analysis",
        "x$statements_with_errors_or_warnings",
        "x$statements_with_full_table_scans",
        "x$statements_with_runtimes_in_95th_percentile",
        "x$statements_with_sorting",
        "x$statements_with_temp_tables",
        "x$user_summary",
        "x$user_summary_by_file_io",
        "x$user_summary_by_file_io_type",
        "x$user_summary_by_stages",
        "x$user_summary_by_statement_latency",
        "x$user_summary_by_statement_type",
        "x$wait_classes_global_by_avg_latency",
        "x$wait_classes_global_by_latency",
        "x$waits_by_host_by_latency",
        "x$waits_by_user_by_latency",
        "x$waits_global_by_latency"
    };

    public boolean isSystemDatabase(String dbName) {
        if (dbName == null) return false;
        String lower = dbName.toLowerCase();
        return "information_schema".equals(lower) || "pocketsql".equals(lower) || "sys".equals(lower);
    }

    public List<String> getSystemTables(String dbName) {
        List<String> list = new ArrayList<>();
        if (dbName == null) return list;
        String lower = dbName.toLowerCase();
        if ("information_schema".equals(lower)) {
            list.addAll(Arrays.asList(INFO_SCHEMA_SYSTEM_TABLES));
        } else if ("pocketsql".equals(lower)) {
            list.addAll(Arrays.asList(POCKETSQL_SYSTEM_TABLES));
        } else if ("sys".equals(lower)) {
            list.addAll(Arrays.asList(SYS_SYSTEM_TABLES));
        }
        return list;
    }

    public String getTableType(String db, String table) {
        if ("pocketsql".equalsIgnoreCase(db)) {
            return "BASE TABLE";
        }
        if ("sys".equalsIgnoreCase(db)) {
            return "version".equalsIgnoreCase(table) ? "VIEW" : "BASE TABLE";
        }
        return "SYSTEM VIEW";
    }

    public TableData getSystemTable(DatabaseEngine engine, String db, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        List<String> typs = new ArrayList<>();

        db = db.toLowerCase();
        table = table.toLowerCase();

        if ("information_schema".equals(db)) {
            if ("schemata".equals(table)) {
                cols.add("CATALOG_NAME");
                cols.add("SCHEMA_NAME");
                cols.add("DEFAULT_CHARACTER_SET_NAME");
                cols.add("DEFAULT_COLLATION_NAME");
                cols.add("SQL_PATH");

                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");

                TableData td = new TableData("information_schema.schemata", cols, typs);
                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("CATALOG_NAME", "def");
                    r.put("SCHEMA_NAME", dbName);
                    r.put("DEFAULT_CHARACTER_SET_NAME", "utf8mb4");
                    r.put("DEFAULT_COLLATION_NAME", "utf8mb4_0900_ai_ci");
                    r.put("SQL_PATH", null);
                    td.rows.add(r);
                }
                return td;
            }

            if ("tables".equals(table)) {
                cols.add("TABLE_CATALOG");
                cols.add("TABLE_SCHEMA");
                cols.add("TABLE_NAME");
                cols.add("TABLE_TYPE");
                cols.add("ENGINE");
                cols.add("VERSION");
                cols.add("ROW_FORMAT");
                cols.add("TABLE_ROWS");
                cols.add("DATA_LENGTH");
                cols.add("INDEX_LENGTH");
                cols.add("CREATE_TIME");
                cols.add("UPDATE_TIME");
                cols.add("CHECK_TIME");
                cols.add("TABLE_COLLATION");
                cols.add("CHECKSUM");
                cols.add("CREATE_OPTIONS");
                cols.add("TABLE_COMMENT");

                for (int i = 0; i < 17; i++) {
                    typs.add("VARCHAR");
                }

                TableData td = new TableData("information_schema.tables", cols, typs);
                addVirtualSystemTablesForTables(td);

                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    if (isSystemDatabase(dbName)) continue;
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> tblKeys = schema.keys();
                        while (tblKeys.hasNext()) {
                            String tblName = tblKeys.next();
                            if (tblName.startsWith("__")) continue;
                            JSONObject tblObj = schema.optJSONObject(tblName);
                            if (tblObj == null) continue;
                            boolean isView = tblObj.optBoolean("is_view", false);
                            
                            Map<String, Object> r = new HashMap<>();
                            r.put("TABLE_CATALOG", "def");
                            r.put("TABLE_SCHEMA", dbName);
                            r.put("TABLE_NAME", tblName);
                            r.put("TABLE_TYPE", isView ? "VIEW" : "BASE TABLE");
                            r.put("ENGINE", "PocketSQL");
                            r.put("VERSION", "10");
                            r.put("ROW_FORMAT", "Dynamic");
                            r.put("TABLE_ROWS", "0");
                            r.put("DATA_LENGTH", "0");
                            r.put("INDEX_LENGTH", "0");
                            r.put("CREATE_TIME", null);
                            r.put("UPDATE_TIME", null);
                            r.put("CHECK_TIME", null);
                            r.put("TABLE_COLLATION", "utf8mb4_0900_ai_ci");
                            r.put("CHECKSUM", null);
                            r.put("CREATE_OPTIONS", null);
                            r.put("TABLE_COMMENT", "");
                            td.rows.add(r);
                        }
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                return td;
            }

            if ("columns".equals(table)) {
                cols.add("TABLE_CATALOG");
                cols.add("TABLE_SCHEMA");
                cols.add("TABLE_NAME");
                cols.add("COLUMN_NAME");
                cols.add("ORDINAL_POSITION");
                cols.add("COLUMN_DEFAULT");
                cols.add("IS_NULLABLE");
                cols.add("DATA_TYPE");
                cols.add("CHARACTER_MAXIMUM_LENGTH");
                cols.add("COLUMN_TYPE");
                cols.add("COLUMN_KEY");
                cols.add("EXTRA");

                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");

                TableData td = new TableData("information_schema.columns", cols, typs);
                addVirtualSystemColumns(engine, td);

                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    if (isSystemDatabase(dbName)) continue;
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> tblKeys = schema.keys();
                        while (tblKeys.hasNext()) {
                            String tblName = tblKeys.next();
                            if (tblName.startsWith("__")) continue;
                            JSONObject tblObj = schema.optJSONObject(tblName);
                            if (tblObj == null) continue;
                            
                            JSONArray colArr = tblObj.optJSONArray("columns");
                            JSONArray typArr = tblObj.optJSONArray("types");
                            JSONObject nulls = tblObj.optJSONObject("nullables");
                            JSONObject defs = tblObj.optJSONObject("defaults");
                            String primaryKey = tblObj.optString("primary_key", "");

                            if (colArr != null && typArr != null) {
                                for (int i = 0; i < colArr.length(); i++) {
                                    String colName = colArr.getString(i);
                                    String colType = typArr.getString(i);
                                    boolean isNull = nulls != null ? nulls.optBoolean(colName, true) : true;
                                    Object defVal = defs != null ? defs.opt(colName) : null;
                                    boolean isPri = colName.equalsIgnoreCase(primaryKey);
                                    
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("TABLE_CATALOG", "def");
                                    r.put("TABLE_SCHEMA", dbName);
                                    r.put("TABLE_NAME", tblName);
                                    r.put("COLUMN_NAME", colName);
                                    r.put("ORDINAL_POSITION", (long)(i + 1));
                                    r.put("COLUMN_DEFAULT", defVal != null ? defVal.toString() : null);
                                    r.put("IS_NULLABLE", isNull ? "YES" : "NO");
                                    r.put("DATA_TYPE", colType);
                                    r.put("CHARACTER_MAXIMUM_LENGTH", 255L);
                                    r.put("COLUMN_TYPE", colType);
                                    r.put("COLUMN_KEY", isPri ? "PRI" : "");
                                    r.put("EXTRA", "");
                                    td.rows.add(r);
                                }
                            }
                        }
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                return td;
            }

            if ("table_constraints".equals(table)) {
                cols.add("CONSTRAINT_CATALOG");
                cols.add("CONSTRAINT_SCHEMA");
                cols.add("CONSTRAINT_NAME");
                cols.add("TABLE_SCHEMA");
                cols.add("TABLE_NAME");
                cols.add("CONSTRAINT_TYPE");
                cols.add("ENFORCED");

                for (int i = 0; i < 7; i++) typs.add("VARCHAR");

                TableData td = new TableData("information_schema.table_constraints", cols, typs);
                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> tblKeys = schema.keys();
                        while (tblKeys.hasNext()) {
                            String tblName = tblKeys.next();
                            if (tblName.startsWith("__")) continue;
                            JSONObject tblObj = schema.optJSONObject(tblName);
                            if (tblObj == null) continue;

                            JSONArray pk = tblObj.optJSONArray("primary_key");
                            if (pk != null && pk.length() > 0) {
                                Map<String, Object> r = new HashMap<>();
                                r.put("CONSTRAINT_CATALOG", "def");
                                r.put("CONSTRAINT_SCHEMA", dbName);
                                r.put("CONSTRAINT_NAME", "PRIMARY");
                                r.put("TABLE_SCHEMA", dbName);
                                r.put("TABLE_NAME", tblName);
                                r.put("CONSTRAINT_TYPE", "PRIMARY KEY");
                                r.put("ENFORCED", "YES");
                                td.rows.add(r);
                            }

                            JSONArray uniques = tblObj.optJSONArray("uniques");
                            if (uniques != null) {
                                for (int u = 0; u < uniques.length(); u++) {
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("CONSTRAINT_CATALOG", "def");
                                    r.put("CONSTRAINT_SCHEMA", dbName);
                                    r.put("CONSTRAINT_NAME", tblName + "_unique_" + u);
                                    r.put("TABLE_SCHEMA", dbName);
                                    r.put("TABLE_NAME", tblName);
                                    r.put("CONSTRAINT_TYPE", "UNIQUE");
                                    r.put("ENFORCED", "YES");
                                    td.rows.add(r);
                                }
                            }

                            JSONObject fks = tblObj.optJSONObject("foreign_keys");
                            if (fks != null) {
                                Iterator<String> fkKeys = fks.keys();
                                while (fkKeys.hasNext()) {
                                    String fkCol = fkKeys.next();
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("CONSTRAINT_CATALOG", "def");
                                    r.put("CONSTRAINT_SCHEMA", dbName);
                                    r.put("CONSTRAINT_NAME", tblName + "_fk_" + fkCol);
                                    r.put("TABLE_SCHEMA", dbName);
                                    r.put("TABLE_NAME", tblName);
                                    r.put("CONSTRAINT_TYPE", "FOREIGN KEY");
                                    r.put("ENFORCED", "YES");
                                    td.rows.add(r);
                                }
                            }
                        }
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                return td;
            }

            if ("key_column_usage".equals(table)) {
                cols.add("CONSTRAINT_CATALOG");
                cols.add("CONSTRAINT_SCHEMA");
                cols.add("CONSTRAINT_NAME");
                cols.add("TABLE_CATALOG");
                cols.add("TABLE_SCHEMA");
                cols.add("TABLE_NAME");
                cols.add("COLUMN_NAME");
                cols.add("ORDINAL_POSITION");
                cols.add("POSITION_IN_UNIQUE_CONSTRAINT");
                cols.add("REFERENCED_TABLE_SCHEMA");
                cols.add("REFERENCED_TABLE_NAME");
                cols.add("REFERENCED_COLUMN_NAME");

                for (int i = 0; i < 7; i++) typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("BIGINT");
                for (int i = 0; i < 3; i++) typs.add("VARCHAR");

                TableData td = new TableData("information_schema.key_column_usage", cols, typs);
                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> tblKeys = schema.keys();
                        while (tblKeys.hasNext()) {
                            String tblName = tblKeys.next();
                            if (tblName.startsWith("__")) continue;
                            JSONObject tblObj = schema.optJSONObject(tblName);
                            if (tblObj == null) continue;

                            JSONArray pk = tblObj.optJSONArray("primary_key");
                            if (pk != null) {
                                for (int i = 0; i < pk.length(); i++) {
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("CONSTRAINT_CATALOG", "def");
                                    r.put("CONSTRAINT_SCHEMA", dbName);
                                    r.put("CONSTRAINT_NAME", "PRIMARY");
                                    r.put("TABLE_CATALOG", "def");
                                    r.put("TABLE_SCHEMA", dbName);
                                    r.put("TABLE_NAME", tblName);
                                    r.put("COLUMN_NAME", pk.getString(i));
                                    r.put("ORDINAL_POSITION", (long)(i + 1));
                                    r.put("POSITION_IN_UNIQUE_CONSTRAINT", null);
                                    r.put("REFERENCED_TABLE_SCHEMA", null);
                                    r.put("REFERENCED_TABLE_NAME", null);
                                    r.put("REFERENCED_COLUMN_NAME", null);
                                    td.rows.add(r);
                                }
                            }

                            JSONArray uniques = tblObj.optJSONArray("uniques");
                            if (uniques != null) {
                                for (int u = 0; u < uniques.length(); u++) {
                                    JSONArray uCols = uniques.optJSONArray(u);
                                    if (uCols != null) {
                                        for (int i = 0; i < uCols.length(); i++) {
                                            Map<String, Object> r = new HashMap<>();
                                            r.put("CONSTRAINT_CATALOG", "def");
                                            r.put("CONSTRAINT_SCHEMA", dbName);
                                            r.put("CONSTRAINT_NAME", tblName + "_unique_" + u);
                                            r.put("TABLE_CATALOG", "def");
                                            r.put("TABLE_SCHEMA", dbName);
                                            r.put("TABLE_NAME", tblName);
                                            r.put("COLUMN_NAME", uCols.getString(i));
                                            r.put("ORDINAL_POSITION", (long)(i + 1));
                                            r.put("POSITION_IN_UNIQUE_CONSTRAINT", null);
                                            r.put("REFERENCED_TABLE_SCHEMA", null);
                                            r.put("REFERENCED_TABLE_NAME", null);
                                            r.put("REFERENCED_COLUMN_NAME", null);
                                            td.rows.add(r);
                                        }
                                    }
                                }
                            }

                            JSONObject fks = tblObj.optJSONObject("foreign_keys");
                            if (fks != null) {
                                Iterator<String> fkKeys = fks.keys();
                                while (fkKeys.hasNext()) {
                                    String fkCol = fkKeys.next();
                                    String refVal = fks.getString(fkCol);
                                    String refTable = "";
                                    String refCol = "";
                                    int dot = refVal.indexOf('.');
                                    if (dot >= 0) {
                                        refTable = refVal.substring(0, dot);
                                        refCol = refVal.substring(dot + 1);
                                    }
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("CONSTRAINT_CATALOG", "def");
                                    r.put("CONSTRAINT_SCHEMA", dbName);
                                    r.put("CONSTRAINT_NAME", tblName + "_fk_" + fkCol);
                                    r.put("TABLE_CATALOG", "def");
                                    r.put("TABLE_SCHEMA", dbName);
                                    r.put("TABLE_NAME", tblName);
                                    r.put("COLUMN_NAME", fkCol);
                                    r.put("ORDINAL_POSITION", 1L);
                                    r.put("POSITION_IN_UNIQUE_CONSTRAINT", 1L);
                                    r.put("REFERENCED_TABLE_SCHEMA", dbName);
                                    r.put("REFERENCED_TABLE_NAME", refTable);
                                    r.put("REFERENCED_COLUMN_NAME", refCol);
                                    td.rows.add(r);
                                }
                            }
                        }
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                return td;
            }

            if ("referential_constraints".equals(table)) {
                cols.add("CONSTRAINT_CATALOG");
                cols.add("CONSTRAINT_SCHEMA");
                cols.add("CONSTRAINT_NAME");
                cols.add("UNIQUE_CONSTRAINT_CATALOG");
                cols.add("UNIQUE_CONSTRAINT_SCHEMA");
                cols.add("UNIQUE_CONSTRAINT_NAME");
                cols.add("MATCH_OPTION");
                cols.add("UPDATE_RULE");
                cols.add("DELETE_RULE");
                cols.add("TABLE_NAME");
                cols.add("REFERENCED_TABLE_NAME");

                for (int i = 0; i < 11; i++) typs.add("VARCHAR");

                TableData td = new TableData("information_schema.referential_constraints", cols, typs);
                List<String> dbs = engine.getStorageEngine().listDatabases();
                for (String dbName : dbs) {
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> tblKeys = schema.keys();
                        while (tblKeys.hasNext()) {
                            String tblName = tblKeys.next();
                            if (tblName.startsWith("__")) continue;
                            JSONObject tblObj = schema.optJSONObject(tblName);
                            if (tblObj == null) continue;

                            JSONObject fks = tblObj.optJSONObject("foreign_keys");
                            if (fks != null) {
                                Iterator<String> fkKeys = fks.keys();
                                while (fkKeys.hasNext()) {
                                    String fkCol = fkKeys.next();
                                    String refVal = fks.getString(fkCol);
                                    String refTable = "";
                                    int dot = refVal.indexOf('.');
                                    if (dot >= 0) {
                                        refTable = refVal.substring(0, dot);
                                    }
                                    Map<String, Object> r = new HashMap<>();
                                    r.put("CONSTRAINT_CATALOG", "def");
                                    r.put("CONSTRAINT_SCHEMA", dbName);
                                    r.put("CONSTRAINT_NAME", tblName + "_fk_" + fkCol);
                                    r.put("UNIQUE_CONSTRAINT_CATALOG", "def");
                                    r.put("UNIQUE_CONSTRAINT_SCHEMA", dbName);
                                    r.put("UNIQUE_CONSTRAINT_NAME", "PRIMARY");
                                    r.put("MATCH_OPTION", "NONE");
                                    r.put("UPDATE_RULE", "NO ACTION");
                                    r.put("DELETE_RULE", "NO ACTION");
                                    r.put("TABLE_NAME", tblName);
                                    r.put("REFERENCED_TABLE_NAME", refTable);
                                    td.rows.add(r);
                                }
                            }
                        }
                    } catch (Exception e) {
                        com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                    }
                }
                return td;
            }

            if ("user_privileges".equals(table)) {
                cols.add("GRANTEE");
                cols.add("TABLE_CATALOG");
                cols.add("PRIVILEGE_TYPE");
                cols.add("IS_GRANTABLE");

                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");

                TableData td = new TableData("information_schema.user_privileges", cols, typs);
                JSONObject cachedUsers = engine.cachedUsers;
                Iterator<String> userIt = cachedUsers.keys();
                while (userIt.hasNext()) {
                    String userKey = userIt.next();
                    JSONObject userObj = cachedUsers.optJSONObject(userKey);
                    if (userObj == null) continue;
                    JSONObject privs = userObj.optJSONObject("privileges");
                    if (privs == null) continue;
                    Iterator<String> patternIt = privs.keys();
                    while (patternIt.hasNext()) {
                        String pattern = patternIt.next();
                        JSONArray pArr = privs.optJSONArray(pattern);
                        if (pArr == null) continue;
                        for (int i = 0; i < pArr.length(); i++) {
                            String priv = pArr.getString(i);
                            Map<String, Object> r = new HashMap<>();
                            r.put("GRANTEE", "'" + userKey.replace("@", "'@'") + "'");
                            r.put("TABLE_CATALOG", "def");
                            r.put("PRIVILEGE_TYPE", priv);
                            r.put("IS_GRANTABLE", "YES");
                            td.rows.add(r);
                        }
                    }
                }
                return td;
            }

            if ("statistics".equals(table)) {
                return engine.getOrLoadTable("INFORMATION_SCHEMA.STATISTICS");
            }
            if ("views".equals(table)) {
                return engine.getOrLoadTable("INFORMATION_SCHEMA.VIEWS");
            }
            if ("routines".equals(table)) {
                return engine.getOrLoadTable("INFORMATION_SCHEMA.ROUTINES");
            }

            if ("character_sets".equals(table)) {
                cols.add("CHARACTER_SET_NAME");
                cols.add("DEFAULT_COLLATE_NAME");
                cols.add("DESCRIPTION");
                cols.add("MAXLEN");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("BIGINT");
                TableData td = new TableData("information_schema.character_sets", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("CHARACTER_SET_NAME", "utf8mb4");
                r.put("DEFAULT_COLLATE_NAME", "utf8mb4_0900_ai_ci");
                r.put("DESCRIPTION", "UTF-8 Unicode");
                r.put("MAXLEN", 4L);
                td.rows.add(r);
                return td;
            }

            if ("collations".equals(table)) {
                cols.add("COLLATION_NAME");
                cols.add("CHARACTER_SET_NAME");
                cols.add("ID");
                cols.add("IS_DEFAULT");
                cols.add("IS_COMPILED");
                cols.add("SORTLEN");
                cols.add("PAD_ATTRIBUTE");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("VARCHAR");
                TableData td = new TableData("information_schema.collations", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("COLLATION_NAME", "utf8mb4_0900_ai_ci");
                r.put("CHARACTER_SET_NAME", "utf8mb4");
                r.put("ID", 255L);
                r.put("IS_DEFAULT", "Yes");
                r.put("IS_COMPILED", "Yes");
                r.put("SORTLEN", 1L);
                r.put("PAD_ATTRIBUTE", "PAD SPACE");
                td.rows.add(r);
                return td;
            }

            if ("engines".equals(table)) {
                cols.add("ENGINE");
                cols.add("SUPPORT");
                cols.add("COMMENT");
                cols.add("TRANSACTIONS");
                cols.add("XA");
                cols.add("SAVEPOINTS");
                for (int i = 0; i < 6; i++) typs.add("VARCHAR");
                TableData td = new TableData("information_schema.engines", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("ENGINE", "PocketSQL");
                r.put("SUPPORT", "DEFAULT");
                r.put("COMMENT", "PocketSQL Storage Engine");
                r.put("TRANSACTIONS", "YES");
                r.put("XA", "YES");
                r.put("SAVEPOINTS", "YES");
                td.rows.add(r);
                return td;
            }

            if ("plugins".equals(table)) {
                cols.add("PLUGIN_NAME");
                cols.add("PLUGIN_VERSION");
                cols.add("PLUGIN_STATUS");
                cols.add("PLUGIN_TYPE");
                cols.add("PLUGIN_LIBRARY");
                cols.add("PLUGIN_AUTHOR");
                cols.add("PLUGIN_DESCRIPTION");
                cols.add("PLUGIN_LICENSE");
                for (int i = 0; i < 8; i++) typs.add("VARCHAR");
                return new TableData("information_schema.plugins", cols, typs);
            }

            if ("processlist".equals(table)) {
                cols.add("ID");
                cols.add("USER");
                cols.add("HOST");
                cols.add("DB");
                cols.add("COMMAND");
                cols.add("TIME");
                cols.add("STATE");
                cols.add("INFO");
                typs.add("BIGINT");
                for (int i = 0; i < 4; i++) typs.add("VARCHAR");
                typs.add("BIGINT");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                TableData td = new TableData("information_schema.processlist", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("ID", 1L);
                r.put("USER", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("HOST", "localhost");
                r.put("DB", engine.getActiveDatabase() != null ? engine.getActiveDatabase() : null);
                r.put("COMMAND", "Query");
                r.put("TIME", 0L);
                r.put("STATE", "executing");
                r.put("INFO", "SHOW PROCESSLIST");
                td.rows.add(r);
                return td;
            }

            // Build fallback mock schema for any remaining information_schema tables
            for (String sysTbl : INFO_SCHEMA_SYSTEM_TABLES) {
                if (sysTbl.equalsIgnoreCase(table)) {
                    String lower = sysTbl.toLowerCase();
                    if (lower.startsWith("innodb_")) {
                        cols.add("table_name");
                        cols.add("index_name");
                        cols.add("number_of_pages");
                        cols.add("size_in_bytes");
                        cols.add("type");
                        cols.add("status");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                        typs.add("BIGINT");
                        typs.add("BIGINT");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                    } else if (lower.endsWith("_privileges") || lower.endsWith("_priv")) {
                        cols.add("Host");
                        cols.add("Db");
                        cols.add("User");
                        cols.add("Table_name");
                        cols.add("Column_name");
                        cols.add("Privilege_type");
                        cols.add("Is_grantable");
                        for (int i = 0; i < 7; i++) typs.add("VARCHAR");
                    } else if (lower.endsWith("_usage") || lower.endsWith("_constraints")) {
                        cols.add("CONSTRAINT_CATALOG");
                        cols.add("CONSTRAINT_SCHEMA");
                        cols.add("CONSTRAINT_NAME");
                        cols.add("TABLE_SCHEMA");
                        cols.add("TABLE_NAME");
                        cols.add("COLUMN_NAME");
                        for (int i = 0; i < 6; i++) typs.add("VARCHAR");
                    } else {
                        cols.add("name");
                        cols.add("value");
                        cols.add("description");
                        cols.add("status");
                        cols.add("type");
                        for (int i = 0; i < 5; i++) typs.add("VARCHAR");
                    }
                    return new TableData("information_schema." + sysTbl, cols, typs);
                }
            }
        }

        if ("pocketsql".equals(db)) {
            if ("user".equals(table)) {
                String[] flds = {
                    "Host", "User", "Select_priv", "Insert_priv", "Update_priv", "Delete_priv", 
                    "Create_priv", "Drop_priv", "Reload_priv", "Shutdown_priv", "Process_priv", 
                    "File_priv", "Grant_priv", "References_priv", "Index_priv", "Alter_priv", 
                    "Show_db_priv", "Super_priv", "Create_tmp_table_priv", "Lock_tables_priv", 
                    "Execute_priv", "Repl_slave_priv", "Repl_client_priv", "Create_view_priv", 
                    "Show_view_priv", "Create_routine_priv", "Alter_routine_priv", "Create_user_priv", 
                    "Event_priv", "Trigger_priv", "Create_tablespace_priv", "ssl_type", "ssl_cipher", 
                    "x509_issuer", "x509_subject", "max_questions", "max_updates", "max_connections", 
                    "max_user_connections", "plugin", "authentication_string", "password_expired", 
                    "password_last_changed", "password_lifetime", "account_locked", "Create_role_priv", 
                    "Drop_role_priv", "Password_reuse_history", "Password_reuse_time", 
                    "Password_require_current", "User_attributes"
                };

                String[] types = {
                    "CHAR(255)", "CHAR(32)", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')",
                    "ENUM('','ANY','X509','SPECIFIED')", "BLOB", "BLOB", "BLOB", 
                    "INT UNSIGNED", "INT UNSIGNED", "INT UNSIGNED", "INT UNSIGNED", 
                    "CHAR(64)", "TEXT", "ENUM('N','Y')", "TIMESTAMP", "SMALLINT UNSIGNED", 
                    "ENUM('N','Y')", "ENUM('N','Y')", "ENUM('N','Y')", 
                    "SMALLINT UNSIGNED", "SMALLINT UNSIGNED", "ENUM('N','Y')", "JSON"
                };

                cols.addAll(Arrays.asList(flds));
                typs.addAll(Arrays.asList(types));

                TableData td = new TableData("pocketsql.user", cols, typs);
                JSONObject cachedUsers = engine.cachedUsers;
                Iterator<String> userIt = cachedUsers.keys();
                while (userIt.hasNext()) {
                    String userKey = userIt.next();
                    JSONObject userObj = cachedUsers.optJSONObject(userKey);
                    if (userObj == null) continue;

                    String u = userKey;
                    String h = "localhost";
                    int at = userKey.indexOf('@');
                    if (at >= 0) {
                        u = userKey.substring(0, at);
                        h = userKey.substring(at + 1);
                    }

                    boolean hasAll = hasGlobalPrivilege(cachedUsers, userKey, "ALL") || hasGlobalPrivilege(cachedUsers, userKey, "GRANT");
                    boolean hasSelect = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "SELECT");
                    boolean hasInsert = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "INSERT");
                    boolean hasUpdate = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "UPDATE");
                    boolean hasDelete = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "DELETE");
                    boolean hasCreate = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "CREATE");
                    boolean hasDrop = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "DROP");
                    boolean hasGrant = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "GRANT");
                    boolean hasIndex = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "INDEX");
                    boolean hasAlter = hasAll || hasGlobalPrivilege(cachedUsers, userKey, "ALTER");

                    Map<String, Object> r = new HashMap<>();
                    r.put("Host", h);
                    r.put("User", u);
                    r.put("Select_priv", hasSelect ? "Y" : "N");
                    r.put("Insert_priv", hasInsert ? "Y" : "N");
                    r.put("Update_priv", hasUpdate ? "Y" : "N");
                    r.put("Delete_priv", hasDelete ? "Y" : "N");
                    r.put("Create_priv", hasCreate ? "Y" : "N");
                    r.put("Drop_priv", hasDrop ? "Y" : "N");
                    
                    r.put("Reload_priv", hasAll ? "Y" : "N");
                    r.put("Shutdown_priv", hasAll ? "Y" : "N");
                    r.put("Process_priv", hasAll ? "Y" : "N");
                    r.put("File_priv", hasAll ? "Y" : "N");
                    
                    r.put("Grant_priv", hasGrant ? "Y" : "N");
                    r.put("References_priv", hasAll ? "Y" : "N");
                    r.put("Index_priv", hasIndex ? "Y" : "N");
                    r.put("Alter_priv", hasAlter ? "Y" : "N");
                    
                    r.put("Show_db_priv", hasAll ? "Y" : "N");
                    r.put("Super_priv", hasAll ? "Y" : "N");
                    r.put("Create_tmp_table_priv", hasAll ? "Y" : "N");
                    r.put("Lock_tables_priv", hasAll ? "Y" : "N");
                    r.put("Execute_priv", hasAll ? "Y" : "N");
                    r.put("Repl_slave_priv", hasAll ? "Y" : "N");
                    r.put("Repl_client_priv", hasAll ? "Y" : "N");
                    r.put("Create_view_priv", hasAll ? "Y" : "N");
                    r.put("Show_view_priv", hasAll ? "Y" : "N");
                    r.put("Create_routine_priv", hasAll ? "Y" : "N");
                    r.put("Alter_routine_priv", hasAll ? "Y" : "N");
                    r.put("Create_user_priv", hasAll ? "Y" : "N");
                    r.put("Event_priv", hasAll ? "Y" : "N");
                    r.put("Trigger_priv", hasAll ? "Y" : "N");
                    r.put("Create_tablespace_priv", hasAll ? "Y" : "N");
                    
                    r.put("ssl_type", "");
                    r.put("ssl_cipher", null);
                    r.put("x509_issuer", null);
                    r.put("x509_subject", null);
                    
                    r.put("max_questions", 0L);
                    r.put("max_updates", 0L);
                    r.put("max_connections", 0L);
                    r.put("max_user_connections", 0L);
                    
                    r.put("plugin", "caching_sha2_password");
                    r.put("authentication_string", userObj.optString("password", ""));
                    r.put("password_expired", "N");
                    r.put("password_last_changed", null);
                    r.put("password_lifetime", null);
                    r.put("account_locked", "N");
                    
                    r.put("Create_role_priv", hasAll ? "Y" : "N");
                    r.put("Drop_role_priv", hasAll ? "Y" : "N");
                    r.put("Password_reuse_history", null);
                    r.put("Password_reuse_time", null);
                    r.put("Password_require_current", null);
                    r.put("User_attributes", null);
                    
                    td.rows.add(r);
                }
                return td;
            }

            if ("db".equals(table)) {
                cols.add("Host");
                cols.add("Db");
                cols.add("User");
                cols.add("Select_priv");
                cols.add("Insert_priv");
                cols.add("Update_priv");
                cols.add("Delete_priv");
                cols.add("Create_priv");
                cols.add("Drop_priv");
                cols.add("Grant_priv");

                for (int i = 0; i < 10; i++) {
                    typs.add("VARCHAR");
                }

                TableData td = new TableData("pocketsql.db", cols, typs);
                JSONObject cachedUsers = engine.cachedUsers;
                Iterator<String> userIt = cachedUsers.keys();
                while (userIt.hasNext()) {
                    String userKey = userIt.next();
                    JSONObject userObj = cachedUsers.optJSONObject(userKey);
                    if (userObj == null) continue;
                    JSONObject privs = userObj.optJSONObject("privileges");
                    if (privs == null) continue;

                    String u = userKey;
                    String h = "localhost";
                    int at = userKey.indexOf('@');
                    if (at >= 0) {
                        u = userKey.substring(0, at);
                        h = userKey.substring(at + 1);
                    }

                    Iterator<String> keys = privs.keys();
                    while (keys.hasNext()) {
                        String pattern = keys.next();
                        if ("*.*".equals(pattern)) continue;
                        String dbName = pattern;
                        if (pattern.endsWith(".*")) {
                            dbName = pattern.substring(0, pattern.length() - 2);
                        }
                        JSONArray pArr = privs.optJSONArray(pattern);
                        if (pArr == null) continue;

                        boolean hasAll = false;
                        boolean hasSelect = false;
                        boolean hasInsert = false;
                        boolean hasUpdate = false;
                        boolean hasDelete = false;
                        boolean hasCreate = false;
                        boolean hasDrop = false;
                        boolean hasGrant = false;

                        for (int i = 0; i < pArr.length(); i++) {
                            String p = pArr.getString(i);
                            if ("ALL".equalsIgnoreCase(p)) hasAll = true;
                            if ("SELECT".equalsIgnoreCase(p)) hasSelect = true;
                            if ("INSERT".equalsIgnoreCase(p)) hasInsert = true;
                            if ("UPDATE".equalsIgnoreCase(p)) hasUpdate = true;
                            if ("DELETE".equalsIgnoreCase(p)) hasDelete = true;
                            if ("CREATE".equalsIgnoreCase(p)) hasCreate = true;
                            if ("DROP".equalsIgnoreCase(p)) hasDrop = true;
                            if ("GRANT".equalsIgnoreCase(p)) hasGrant = true;
                        }

                        if (hasAll) {
                            hasSelect = hasInsert = hasUpdate = hasDelete = hasCreate = hasDrop = hasGrant = true;
                        }

                        Map<String, Object> r = new HashMap<>();
                        r.put("Host", h);
                        r.put("Db", dbName);
                        r.put("User", u);
                        r.put("Select_priv", hasSelect ? "Y" : "N");
                        r.put("Insert_priv", hasInsert ? "Y" : "N");
                        r.put("Update_priv", hasUpdate ? "Y" : "N");
                        r.put("Delete_priv", hasDelete ? "Y" : "N");
                        r.put("Create_priv", hasCreate ? "Y" : "N");
                        r.put("Drop_priv", hasDrop ? "Y" : "N");
                        r.put("Grant_priv", hasGrant ? "Y" : "N");
                        td.rows.add(r);
                    }
                }
                return td;
            }

            // Define custom fields for other 36 tables
            if ("columns_priv".equals(table)) {
                cols.add("Host");
                cols.add("Db");
                cols.add("User");
                cols.add("Table_name");
                cols.add("Column_name");
                cols.add("Timestamp");
                cols.add("Column_priv");
                for (int i = 0; i < 7; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.columns_priv", cols, typs);
            }
            if ("tables_priv".equals(table)) {
                cols.add("Host");
                cols.add("Db");
                cols.add("User");
                cols.add("Table_name");
                cols.add("Grantor");
                cols.add("Timestamp");
                cols.add("Table_priv");
                cols.add("Column_priv");
                for (int i = 0; i < 8; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.tables_priv", cols, typs);
            }
            if ("plugin".equals(table)) {
                cols.add("name");
                cols.add("dl");
                for (int i = 0; i < 2; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.plugin", cols, typs);
            }
            if ("func".equals(table)) {
                cols.add("name");
                cols.add("ret");
                cols.add("dl");
                cols.add("type");
                for (int i = 0; i < 4; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.func", cols, typs);
            }
            if ("general_log".equals(table)) {
                cols.add("event_time");
                cols.add("user_host");
                cols.add("thread_id");
                cols.add("server_id");
                cols.add("command_type");
                cols.add("argument");
                for (int i = 0; i < 6; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.general_log", cols, typs);
            }
            if ("slow_log".equals(table)) {
                cols.add("start_time");
                cols.add("user_host");
                cols.add("query_time");
                cols.add("lock_time");
                cols.add("rows_sent");
                cols.add("rows_examined");
                cols.add("db");
                cols.add("last_insert_id");
                cols.add("insert_id");
                cols.add("server_id");
                cols.add("sql_text");
                cols.add("thread_id");
                for (int i = 0; i < 12; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.slow_log", cols, typs);
            }
            if ("time_zone".equals(table)) {
                cols.add("Time_zone_id");
                cols.add("Use_leap_seconds");
                for (int i = 0; i < 2; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.time_zone", cols, typs);
            }
            if ("time_zone_name".equals(table)) {
                cols.add("Name");
                cols.add("Time_zone_id");
                for (int i = 0; i < 2; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.time_zone_name", cols, typs);
            }
            if ("time_zone_transition".equals(table)) {
                cols.add("Time_zone_id");
                cols.add("Transition_time");
                cols.add("Transition_type_id");
                for (int i = 0; i < 3; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.time_zone_transition", cols, typs);
            }
            if ("time_zone_transition_type".equals(table)) {
                cols.add("Time_zone_id");
                cols.add("Transition_type_id");
                cols.add("Offset");
                cols.add("Is_dst");
                cols.add("Abbreviation");
                for (int i = 0; i < 5; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.time_zone_transition_type", cols, typs);
            }
            if ("time_zone_leap_second".equals(table)) {
                cols.add("Transition_time");
                cols.add("Correction");
                for (int i = 0; i < 2; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.time_zone_leap_second", cols, typs);
            }
            if ("servers".equals(table)) {
                cols.add("Server_name");
                cols.add("Host");
                cols.add("Db");
                cols.add("Username");
                cols.add("Password");
                cols.add("Port");
                cols.add("Socket");
                cols.add("Wrapper");
                cols.add("Owner");
                for (int i = 0; i < 9; i++) typs.add("VARCHAR");
                return new TableData("pocketsql.servers", cols, typs);
            }
            
            // Build fallback mock schema for any remaining pocket tables
            for (String sysTbl : POCKETSQL_SYSTEM_TABLES) {
                if (sysTbl.equalsIgnoreCase(table)) {
                    String lower = sysTbl.toLowerCase();
                    if (lower.contains("priv")) {
                        cols.add("Host");
                        cols.add("Db");
                        cols.add("User");
                        cols.add("Table_name");
                        cols.add("Column_name");
                        cols.add("Timestamp");
                        cols.add("Privilege");
                        for (int i = 0; i < 7; i++) typs.add("VARCHAR");
                    } else if (lower.startsWith("replication_") || lower.startsWith("slave_")) {
                        cols.add("Channel_name");
                        cols.add("Host");
                        cols.add("Port");
                        cols.add("User_name");
                        cols.add("Status");
                        cols.add("Last_error");
                        cols.add("Delay");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                        typs.add("BIGINT");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                        typs.add("BIGINT");
                    } else if (lower.startsWith("help_")) {
                        cols.add("help_id");
                        cols.add("name");
                        cols.add("parent_id");
                        cols.add("description");
                        cols.add("example");
                        cols.add("url");
                        typs.add("BIGINT");
                        typs.add("VARCHAR");
                        typs.add("BIGINT");
                        typs.add("TEXT");
                        typs.add("TEXT");
                        typs.add("VARCHAR");
                    } else if (lower.startsWith("time_zone")) {
                        cols.add("Time_zone_id");
                        cols.add("Name");
                        cols.add("Transition_time");
                        cols.add("Offset");
                        cols.add("Is_dst");
                        typs.add("BIGINT");
                        typs.add("VARCHAR");
                        typs.add("VARCHAR");
                        typs.add("BIGINT");
                        typs.add("VARCHAR");
                    } else {
                        cols.add("name");
                        cols.add("value");
                        cols.add("description");
                        cols.add("status");
                        cols.add("type");
                        for (int i = 0; i < 5; i++) typs.add("VARCHAR");
                    }
                    return new TableData("pocketsql." + sysTbl, cols, typs);
                }
            }
        }

        if ("sys".equals(db)) {
            if ("sys_config".equals(table)) {
                cols.add("variable");
                cols.add("value");
                cols.add("set_time");
                cols.add("set_by");

                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");
                typs.add("VARCHAR");

                TableData td = new TableData("sys.sys_config", cols, typs);
                Map<String, Object> r1 = new HashMap<>();
                r1.put("variable", "version_comment");
                r1.put("value", "PocketSQL Community Server");
                r1.put("set_time", null);
                r1.put("set_by", null);
                td.rows.add(r1);

                Map<String, Object> r2 = new HashMap<>();
                r2.put("variable", "sql_mode");
                r2.put("value", "STRICT_TRANS_TABLES");
                r2.put("set_time", null);
                r2.put("set_by", null);
                td.rows.add(r2);
                return td;
            }

            if ("version".equals(table)) {
                cols.add("version");
                cols.add("source");

                typs.add("VARCHAR");
                typs.add("VARCHAR");

                TableData td = new TableData("sys.version", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("version", "8.0.25");
                r.put("source", "PocketSQL");
                td.rows.add(r);
                return td;
            }

            // ===== sys.host_summary =====
            if ("host_summary".equals(table) || "x$host_summary".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("statements"); cols.add("statement_latency");
                cols.add("statement_avg_latency"); cols.add("table_scans"); cols.add("file_ios");
                cols.add("file_io_latency"); cols.add("current_connections"); cols.add("total_connections");
                cols.add("unique_users"); cols.add("current_memory"); cols.add("total_memory_allocated");

                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");

                TableData td = new TableData("sys." + table, cols, typs);
                long count = engine.statementCount > 0 ? engine.statementCount : 20L;
                double totalTime = engine.totalExecutionTimeMs > 0 ? (double) engine.totalExecutionTimeMs : 323.45;
                double avgTime = count > 0 ? (totalTime / count) : 16.17;
                int uniqueUsers = 1;
                if (engine.cachedUsers != null) { uniqueUsers = Math.max(1, engine.cachedUsers.length()); }

                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost");
                r.put("statements", count);
                r.put("table_scans", 8L);
                r.put("file_ios", 86L);
                r.put("current_connections", 2L);
                r.put("total_connections", 3L);
                r.put("unique_users", (long) uniqueUsers);
                if (isRaw) {
                    r.put("statement_latency", (long) (totalTime * 1000000L));
                    r.put("statement_avg_latency", (long) (avgTime * 1000000L));
                    r.put("file_io_latency", 15270000000L);
                    r.put("current_memory", 5096000L);
                    r.put("total_memory_allocated", 29851648L);
                } else {
                    r.put("statement_latency", String.format(Locale.US, "%.2f ms", totalTime));
                    r.put("statement_avg_latency", String.format(Locale.US, "%.2f ms", avgTime));
                    r.put("file_io_latency", "15.27 ms");
                    r.put("current_memory", "4.86 MiB");
                    r.put("total_memory_allocated", "28.46 MiB");
                }
                td.rows.add(r);
                return td;
            }

            // ===== sys.host_summary_by_file_io =====
            if ("host_summary_by_file_io".equals(table) || "x$host_summary_by_file_io".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("ios"); cols.add("io_latency");
                typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost");
                r.put("ios", 86L);
                r.put("io_latency", isRaw ? 15270000000L : "15.27 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.host_summary_by_file_io_type =====
            if ("host_summary_by_file_io_type".equals(table) || "x$host_summary_by_file_io_type".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("event_name"); cols.add("total");
                cols.add("total_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                String[] events = {"wait/io/file/innodb/innodb_data_file", "wait/io/file/sql/binlog"};
                long[] totals = {62L, 24L};
                for (int idx = 0; idx < events.length; idx++) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("host", "localhost");
                    r.put("event_name", events[idx]);
                    r.put("total", totals[idx]);
                    r.put("total_latency", isRaw ? (long)(10.5 * 1000000L * (idx + 1)) : String.format(Locale.US, "%.2f ms", 10.5 * (idx + 1)));
                    r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.host_summary_by_stages =====
            if ("host_summary_by_stages".equals(table) || "x$host_summary_by_stages".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("event_name"); cols.add("total");
                cols.add("total_latency"); cols.add("avg_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost");
                r.put("event_name", "stage/sql/starting");
                r.put("total", engine.statementCount > 0 ? engine.statementCount : 20L);
                r.put("total_latency", isRaw ? 5150000L : "5.15 ms");
                r.put("avg_latency", isRaw ? 257500L : "0.26 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.host_summary_by_statement_latency =====
            if ("host_summary_by_statement_latency".equals(table) || "x$host_summary_by_statement_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("total"); cols.add("total_latency");
                cols.add("max_latency"); cols.add("lock_latency"); cols.add("rows_sent");
                cols.add("rows_examined"); cols.add("rows_affected"); cols.add("full_scans");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                long count = engine.statementCount > 0 ? engine.statementCount : 20L;
                double totalTime = engine.totalExecutionTimeMs > 0 ? (double) engine.totalExecutionTimeMs : 323.45;
                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost");
                r.put("total", count);
                r.put("total_latency", isRaw ? (long)(totalTime * 1000000L) : String.format(Locale.US, "%.2f ms", totalTime));
                r.put("max_latency", isRaw ? 50000000L : "50.00 ms");
                r.put("lock_latency", isRaw ? 2000000L : "2.00 ms");
                r.put("rows_sent", 0L); r.put("rows_examined", 0L);
                r.put("rows_affected", 0L); r.put("full_scans", 8L);
                td.rows.add(r);
                return td;
            }

            // ===== sys.host_summary_by_statement_type =====
            if ("host_summary_by_statement_type".equals(table) || "x$host_summary_by_statement_type".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("statement"); cols.add("total");
                cols.add("total_latency"); cols.add("max_latency"); cols.add("lock_latency");
                cols.add("rows_sent"); cols.add("rows_examined"); cols.add("rows_affected"); cols.add("full_scans");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                String[] stmts = {"select", "insert", "update", "delete", "create_table", "show_tables"};
                for (String stmt : stmts) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("host", "localhost"); r.put("statement", stmt);
                    r.put("total", 3L);
                    r.put("total_latency", isRaw ? 15000000L : "15.00 ms");
                    r.put("max_latency", isRaw ? 8000000L : "8.00 ms");
                    r.put("lock_latency", isRaw ? 500000L : "0.50 ms");
                    r.put("rows_sent", 0L); r.put("rows_examined", 0L);
                    r.put("rows_affected", 0L); r.put("full_scans", 1L);
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.user_summary =====
            if ("user_summary".equals(table) || "x$user_summary".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("statements"); cols.add("statement_latency");
                cols.add("statement_avg_latency"); cols.add("table_scans"); cols.add("file_ios");
                cols.add("file_io_latency"); cols.add("current_connections"); cols.add("total_connections");
                cols.add("unique_hosts"); cols.add("current_memory"); cols.add("total_memory_allocated");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                // Generate one row per user from cachedUsers
                if (engine.cachedUsers != null) {
                    Iterator<String> userIt = engine.cachedUsers.keys();
                    while (userIt.hasNext()) {
                        String userKey = userIt.next();
                        String u = userKey;
                        int at = userKey.indexOf('@');
                        if (at >= 0) u = userKey.substring(0, at);
                        long count = engine.statementCount > 0 ? engine.statementCount : 20L;
                        double totalTime = engine.totalExecutionTimeMs > 0 ? (double) engine.totalExecutionTimeMs : 323.45;
                        double avgTime = count > 0 ? (totalTime / count) : 16.17;
                        Map<String, Object> r = new HashMap<>();
                        r.put("user", u); r.put("statements", count);
                        r.put("table_scans", 8L); r.put("file_ios", 86L);
                        r.put("current_connections", 2L); r.put("total_connections", 3L);
                        r.put("unique_hosts", 1L);
                        if (isRaw) {
                            r.put("statement_latency", (long)(totalTime * 1000000L));
                            r.put("statement_avg_latency", (long)(avgTime * 1000000L));
                            r.put("file_io_latency", 15270000000L);
                            r.put("current_memory", 5096000L);
                            r.put("total_memory_allocated", 29851648L);
                        } else {
                            r.put("statement_latency", String.format(Locale.US, "%.2f ms", totalTime));
                            r.put("statement_avg_latency", String.format(Locale.US, "%.2f ms", avgTime));
                            r.put("file_io_latency", "15.27 ms");
                            r.put("current_memory", "4.86 MiB");
                            r.put("total_memory_allocated", "28.46 MiB");
                        }
                        td.rows.add(r);
                    }
                }
                if (td.rows.isEmpty()) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("user", "root"); r.put("statements", 20L);
                    r.put("table_scans", 8L); r.put("file_ios", 86L);
                    r.put("current_connections", 2L); r.put("total_connections", 3L);
                    r.put("unique_hosts", 1L);
                    r.put("statement_latency", isRaw ? 323450000L : "323.45 ms");
                    r.put("statement_avg_latency", isRaw ? 16172500L : "16.17 ms");
                    r.put("file_io_latency", isRaw ? 15270000000L : "15.27 ms");
                    r.put("current_memory", isRaw ? 5096000L : "4.86 MiB");
                    r.put("total_memory_allocated", isRaw ? 29851648L : "28.46 MiB");
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.user_summary_by_file_io =====
            if ("user_summary_by_file_io".equals(table) || "x$user_summary_by_file_io".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("ios"); cols.add("io_latency");
                typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("ios", 86L);
                r.put("io_latency", isRaw ? 15270000000L : "15.27 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.user_summary_by_file_io_type =====
            if ("user_summary_by_file_io_type".equals(table) || "x$user_summary_by_file_io_type".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("event_name"); cols.add("total");
                cols.add("latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("event_name", "wait/io/file/innodb/innodb_data_file");
                r.put("total", 62L);
                r.put("latency", isRaw ? 10500000L : "10.50 ms");
                r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.user_summary_by_stages =====
            if ("user_summary_by_stages".equals(table) || "x$user_summary_by_stages".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("event_name"); cols.add("total");
                cols.add("total_latency"); cols.add("avg_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("event_name", "stage/sql/starting");
                r.put("total", engine.statementCount > 0 ? engine.statementCount : 20L);
                r.put("total_latency", isRaw ? 5150000L : "5.15 ms");
                r.put("avg_latency", isRaw ? 257500L : "0.26 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.user_summary_by_statement_latency =====
            if ("user_summary_by_statement_latency".equals(table) || "x$user_summary_by_statement_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("total"); cols.add("total_latency");
                cols.add("max_latency"); cols.add("lock_latency"); cols.add("rows_sent");
                cols.add("rows_examined"); cols.add("rows_affected"); cols.add("full_scans");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                long count = engine.statementCount > 0 ? engine.statementCount : 20L;
                double totalTime = engine.totalExecutionTimeMs > 0 ? (double) engine.totalExecutionTimeMs : 323.45;
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("total", count);
                r.put("total_latency", isRaw ? (long)(totalTime * 1000000L) : String.format(Locale.US, "%.2f ms", totalTime));
                r.put("max_latency", isRaw ? 50000000L : "50.00 ms");
                r.put("lock_latency", isRaw ? 2000000L : "2.00 ms");
                r.put("rows_sent", 0L); r.put("rows_examined", 0L);
                r.put("rows_affected", 0L); r.put("full_scans", 8L);
                td.rows.add(r);
                return td;
            }

            // ===== sys.user_summary_by_statement_type =====
            if ("user_summary_by_statement_type".equals(table) || "x$user_summary_by_statement_type".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("statement"); cols.add("total");
                cols.add("total_latency"); cols.add("max_latency"); cols.add("lock_latency");
                cols.add("rows_sent"); cols.add("rows_examined"); cols.add("rows_affected"); cols.add("full_scans");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                String[] stmts = {"select", "insert", "update", "delete"};
                for (String stmt : stmts) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                    r.put("statement", stmt); r.put("total", 3L);
                    r.put("total_latency", isRaw ? 15000000L : "15.00 ms");
                    r.put("max_latency", isRaw ? 8000000L : "8.00 ms");
                    r.put("lock_latency", isRaw ? 500000L : "0.50 ms");
                    r.put("rows_sent", 0L); r.put("rows_examined", 0L);
                    r.put("rows_affected", 0L); r.put("full_scans", 1L);
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.innodb_buffer_stats_by_schema =====
            if ("innodb_buffer_stats_by_schema".equals(table) || "x$innodb_buffer_stats_by_schema".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("object_schema"); cols.add("allocated"); cols.add("data");
                cols.add("pages"); cols.add("pages_hashed"); cols.add("pages_old"); cols.add("rows_cached");
                typs.add("VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("object_schema", engine.getActiveDatabase() != null ? engine.getActiveDatabase() : "pocketsql");
                r.put("allocated", isRaw ? 16384L : "16.00 KiB");
                r.put("data", isRaw ? 8192L : "8.00 KiB");
                r.put("pages", 1L); r.put("pages_hashed", 0L);
                r.put("pages_old", 0L); r.put("rows_cached", 0L);
                td.rows.add(r);
                return td;
            }

            // ===== sys.innodb_buffer_stats_by_table =====
            if ("innodb_buffer_stats_by_table".equals(table) || "x$innodb_buffer_stats_by_table".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("object_schema"); cols.add("object_name"); cols.add("allocated");
                cols.add("data"); cols.add("pages"); cols.add("pages_hashed");
                cols.add("pages_old"); cols.add("rows_cached");
                typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty - no buffer pool data in PocketSQL
            }

            // ===== sys.innodb_lock_waits =====
            if ("innodb_lock_waits".equals(table) || "x$innodb_lock_waits".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("wait_started"); cols.add("wait_age"); cols.add("wait_age_secs");
                cols.add("locked_table"); cols.add("locked_index"); cols.add("locked_type");
                cols.add("waiting_trx_id"); cols.add("waiting_trx_started"); cols.add("waiting_trx_age");
                cols.add("waiting_trx_rows_locked"); cols.add("waiting_trx_rows_modified");
                cols.add("waiting_pid"); cols.add("waiting_query"); cols.add("waiting_lock_id");
                cols.add("waiting_lock_mode"); cols.add("blocking_trx_id"); cols.add("blocking_pid");
                cols.add("blocking_query"); cols.add("blocking_lock_id"); cols.add("blocking_lock_mode");
                cols.add("blocking_trx_started"); cols.add("blocking_trx_age");
                cols.add("blocking_trx_rows_locked"); cols.add("blocking_trx_rows_modified");
                cols.add("sql_kill_blocking_query"); cols.add("sql_kill_blocking_connection");
                for (int i = 0; i < 26; i++) typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty - no lock waits in PocketSQL typically
            }

            // ===== sys.io_by_thread_by_latency =====
            if ("io_by_thread_by_latency".equals(table) || "x$io_by_thread_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("total"); cols.add("total_latency");
                cols.add("min_latency"); cols.add("avg_latency"); cols.add("max_latency");
                cols.add("thread_id"); cols.add("processlist_id");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("total", 86L);
                r.put("total_latency", isRaw ? 15270000000L : "15.27 ms");
                r.put("min_latency", isRaw ? 1000L : "0.00 ms");
                r.put("avg_latency", isRaw ? 177558139L : "0.18 ms");
                r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                r.put("thread_id", 49L); r.put("processlist_id", 1L);
                td.rows.add(r);
                return td;
            }

            // ===== sys.io_global_by_file_by_bytes =====
            if ("io_global_by_file_by_bytes".equals(table) || "x$io_global_by_file_by_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("file"); cols.add("count_read"); cols.add("total_read");
                cols.add("avg_read"); cols.add("count_write"); cols.add("total_written"); cols.add("avg_write");
                cols.add("total"); cols.add("write_pct");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("DECIMAL");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.io_global_by_file_by_latency =====
            if ("io_global_by_file_by_latency".equals(table) || "x$io_global_by_file_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("file"); cols.add("total"); cols.add("total_latency");
                cols.add("count_read"); cols.add("read_latency"); cols.add("count_write");
                cols.add("write_latency"); cols.add("count_misc"); cols.add("misc_latency");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.io_global_by_wait_by_bytes =====
            if ("io_global_by_wait_by_bytes".equals(table) || "x$io_global_by_wait_by_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("event_name"); cols.add("total"); cols.add("total_latency");
                cols.add("min_latency"); cols.add("avg_latency"); cols.add("max_latency");
                cols.add("count_read"); cols.add("total_read"); cols.add("avg_read");
                cols.add("count_write"); cols.add("total_written"); cols.add("avg_written");
                cols.add("total_requested");
                typs.add("VARCHAR"); typs.add("BIGINT");
                for (int i = 0; i < 4; i++) typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.io_global_by_wait_by_latency =====
            if ("io_global_by_wait_by_latency".equals(table) || "x$io_global_by_wait_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("event_name"); cols.add("total"); cols.add("total_latency");
                cols.add("avg_latency"); cols.add("max_latency"); cols.add("read_latency");
                cols.add("write_latency"); cols.add("misc_latency"); cols.add("count_read");
                cols.add("total_read"); cols.add("avg_read"); cols.add("count_write");
                cols.add("total_written"); cols.add("avg_written");
                typs.add("VARCHAR"); typs.add("BIGINT");
                for (int i = 0; i < 6; i++) typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.latest_file_io =====
            if ("latest_file_io".equals(table) || "x$latest_file_io".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("thread"); cols.add("file"); cols.add("latency");
                cols.add("operation"); cols.add("requested");
                typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.memory_by_host_by_current_bytes =====
            if ("memory_by_host_by_current_bytes".equals(table) || "x$memory_by_host_by_current_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("current_count_used"); cols.add("current_allocated");
                cols.add("current_avg_alloc"); cols.add("current_max_alloc"); cols.add("total_allocated");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost"); r.put("current_count_used", 45L);
                r.put("current_allocated", isRaw ? 5096000L : "4.86 MiB");
                r.put("current_avg_alloc", isRaw ? 113244L : "110.59 KiB");
                r.put("current_max_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("total_allocated", isRaw ? 29851648L : "28.46 MiB");
                td.rows.add(r);
                return td;
            }

            // ===== sys.memory_by_thread_by_current_bytes =====
            if ("memory_by_thread_by_current_bytes".equals(table) || "x$memory_by_thread_by_current_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("thread_id"); cols.add("user"); cols.add("current_count_used");
                cols.add("current_allocated"); cols.add("current_avg_alloc"); cols.add("current_max_alloc");
                cols.add("total_allocated");
                typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("thread_id", 49L);
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("current_count_used", 45L);
                r.put("current_allocated", isRaw ? 5096000L : "4.86 MiB");
                r.put("current_avg_alloc", isRaw ? 113244L : "110.59 KiB");
                r.put("current_max_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("total_allocated", isRaw ? 29851648L : "28.46 MiB");
                td.rows.add(r);
                return td;
            }

            // ===== sys.memory_by_user_by_current_bytes =====
            if ("memory_by_user_by_current_bytes".equals(table) || "x$memory_by_user_by_current_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("current_count_used"); cols.add("current_allocated");
                cols.add("current_avg_alloc"); cols.add("current_max_alloc"); cols.add("total_allocated");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("current_count_used", 45L);
                r.put("current_allocated", isRaw ? 5096000L : "4.86 MiB");
                r.put("current_avg_alloc", isRaw ? 113244L : "110.59 KiB");
                r.put("current_max_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("total_allocated", isRaw ? 29851648L : "28.46 MiB");
                td.rows.add(r);
                return td;
            }

            // ===== sys.memory_global_by_current_bytes =====
            if ("memory_global_by_current_bytes".equals(table) || "x$memory_global_by_current_bytes".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("event_name"); cols.add("current_count"); cols.add("current_alloc");
                cols.add("current_avg_alloc"); cols.add("high_count"); cols.add("high_alloc");
                cols.add("high_avg_alloc");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("event_name", "memory/innodb/buf_buf_pool");
                r.put("current_count", 1L);
                r.put("current_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("current_avg_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("high_count", 1L);
                r.put("high_alloc", isRaw ? 2097152L : "2.00 MiB");
                r.put("high_avg_alloc", isRaw ? 2097152L : "2.00 MiB");
                td.rows.add(r);
                return td;
            }

            // ===== sys.memory_global_total =====
            if ("memory_global_total".equals(table) || "x$memory_global_total".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("total_allocated");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("total_allocated", isRaw ? 29851648L : "28.46 MiB");
                td.rows.add(r);
                return td;
            }

            // ===== sys.metrics =====
            if ("metrics".equals(table)) {
                cols.add("Variable_name"); cols.add("Variable_value"); cols.add("Type"); cols.add("Enabled");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys.metrics", cols, typs);
                String[][] metrics = {
                    {"Bytes_received", "0", "Global Status", "YES"},
                    {"Bytes_sent", "0", "Global Status", "YES"},
                    {"Connections", "3", "Global Status", "YES"},
                    {"Queries", String.valueOf(engine.statementCount), "Global Status", "YES"},
                    {"Uptime", "3600", "Global Status", "YES"},
                    {"Threads_connected", "2", "Global Status", "YES"},
                    {"innodb_buffer_pool_pages_total", "1024", "Global Status", "YES"},
                    {"innodb_buffer_pool_pages_free", "512", "Global Status", "YES"}
                };
                for (String[] m : metrics) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("Variable_name", m[0]); r.put("Variable_value", m[1]);
                    r.put("Type", m[2]); r.put("Enabled", m[3]);
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.processlist =====
            if ("processlist".equals(table) || "x$processlist".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("thd_id"); cols.add("conn_id"); cols.add("user"); cols.add("db");
                cols.add("command"); cols.add("state"); cols.add("time"); cols.add("current_statement");
                cols.add("statement_latency"); cols.add("progress"); cols.add("lock_latency");
                cols.add("rows_examined"); cols.add("rows_sent"); cols.add("rows_affected");
                cols.add("tmp_tables"); cols.add("tmp_disk_tables"); cols.add("full_scan");
                cols.add("last_statement"); cols.add("last_statement_latency"); cols.add("current_memory");
                cols.add("last_wait"); cols.add("last_wait_latency"); cols.add("source");
                cols.add("trx_latency"); cols.add("trx_state"); cols.add("trx_autocommit");
                cols.add("pid"); cols.add("program_name");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add("TEXT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("DECIMAL"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR");
                typs.add("TEXT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add("BIGINT"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("thd_id", 49L); r.put("conn_id", 1L);
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("db", engine.getActiveDatabase());
                r.put("command", "Query"); r.put("state", "executing");
                r.put("time", 0L); r.put("current_statement", "SELECT * FROM sys.processlist");
                r.put("statement_latency", isRaw ? 0L : "0.00 ms");
                r.put("progress", null);
                r.put("lock_latency", isRaw ? 0L : "0.00 ms");
                r.put("rows_examined", 0L); r.put("rows_sent", 0L); r.put("rows_affected", 0L);
                r.put("tmp_tables", 0L); r.put("tmp_disk_tables", 0L); r.put("full_scan", "NO");
                r.put("last_statement", null); r.put("last_statement_latency", isRaw ? 0L : "0.00 ms");
                r.put("current_memory", isRaw ? 5096000L : "4.86 MiB");
                r.put("last_wait", null); r.put("last_wait_latency", isRaw ? 0L : "0.00 ms");
                r.put("source", ""); r.put("trx_latency", isRaw ? 0L : "0.00 ms");
                r.put("trx_state", "COMMITTED"); r.put("trx_autocommit", "YES");
                r.put("pid", 1L); r.put("program_name", "PocketSQL");
                td.rows.add(r);
                return td;
            }

            // ===== sys.ps_check_lost_instrumentation =====
            if ("ps_check_lost_instrumentation".equals(table)) {
                cols.add("variable_name"); cols.add("variable_value");
                typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys.ps_check_lost_instrumentation", cols, typs);
                return td; // empty
            }

            // ===== sys.schema_auto_increment_columns =====
            if ("schema_auto_increment_columns".equals(table)) {
                cols.add("table_schema"); cols.add("table_name"); cols.add("column_name");
                cols.add("data_type"); cols.add("column_type"); cols.add("is_signed");
                cols.add("is_unsigned"); cols.add("max_value"); cols.add("auto_increment");
                cols.add("auto_increment_ratio");
                for (int i = 0; i < 10; i++) typs.add("VARCHAR");
                TableData td = new TableData("sys.schema_auto_increment_columns", cols, typs);
                return td; // empty
            }

            // ===== sys.schema_index_statistics =====
            if ("schema_index_statistics".equals(table) || "x$schema_index_statistics".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("table_schema"); cols.add("table_name"); cols.add("index_name");
                cols.add("rows_selected"); cols.add("select_latency"); cols.add("rows_inserted");
                cols.add("insert_latency"); cols.add("rows_updated"); cols.add("update_latency");
                cols.add("rows_deleted"); cols.add("delete_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.schema_object_overview =====
            if ("schema_object_overview".equals(table)) {
                cols.add("db"); cols.add("object_type"); cols.add("count");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                TableData td = new TableData("sys.schema_object_overview", cols, typs);
                List<String> allDbs = engine.getStorageEngine().listDatabases();
                for (String dbName : allDbs) {
                    try {
                        int tableCount = 0;
                        int viewCount = 0;
                        JSONObject schema = engine.getStorageEngine().readSchema(dbName);
                        Iterator<String> it = schema.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            if (k.startsWith("__")) continue;
                            JSONObject tObj = schema.optJSONObject(k);
                            if (tObj != null && tObj.optBoolean("is_view", false)) viewCount++;
                            else tableCount++;
                        }
                        if (tableCount > 0) {
                            Map<String, Object> r = new HashMap<>();
                            r.put("db", dbName); r.put("object_type", "BASE TABLE"); r.put("count", (long) tableCount);
                            td.rows.add(r);
                        }
                        if (viewCount > 0) {
                            Map<String, Object> r = new HashMap<>();
                            r.put("db", dbName); r.put("object_type", "VIEW"); r.put("count", (long) viewCount);
                            td.rows.add(r);
                        }
                    } catch (Exception e) { com.mysql.pocketsql.engine.SqlLog.printStackTrace(e); }
                }
                return td;
            }

            // ===== sys.schema_redundant_indexes =====
            if ("schema_redundant_indexes".equals(table)) {
                cols.add("table_schema"); cols.add("table_name"); cols.add("redundant_index_name");
                cols.add("redundant_index_columns"); cols.add("redundant_index_non_unique");
                cols.add("dominant_index_name"); cols.add("dominant_index_columns");
                cols.add("dominant_index_non_unique"); cols.add("subpart_exists"); cols.add("sql_drop_index");
                for (int i = 0; i < 10; i++) typs.add("VARCHAR");
                TableData td = new TableData("sys.schema_redundant_indexes", cols, typs);
                return td; // empty
            }

            // ===== sys.schema_table_lock_waits =====
            if ("schema_table_lock_waits".equals(table) || "x$schema_table_lock_waits".equals(table)) {
                cols.add("object_schema"); cols.add("object_name"); cols.add("waiting_thread_id");
                cols.add("waiting_pid"); cols.add("waiting_account"); cols.add("waiting_lock_type");
                cols.add("waiting_lock_duration"); cols.add("waiting_query");
                cols.add("blocking_thread_id"); cols.add("blocking_pid"); cols.add("blocking_account");
                cols.add("blocking_lock_type"); cols.add("blocking_lock_duration");
                cols.add("sql_kill_blocking_query"); cols.add("sql_kill_blocking_connection");
                for (int i = 0; i < 15; i++) typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.schema_table_statistics =====
            if ("schema_table_statistics".equals(table) || "x$schema_table_statistics".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("table_schema"); cols.add("table_name"); cols.add("total_latency");
                cols.add("rows_fetched"); cols.add("fetch_latency"); cols.add("rows_inserted");
                cols.add("insert_latency"); cols.add("rows_updated"); cols.add("update_latency");
                cols.add("rows_deleted"); cols.add("delete_latency"); cols.add("io_read_requests");
                cols.add("io_read"); cols.add("io_read_latency"); cols.add("io_write_requests");
                cols.add("io_write"); cols.add("io_write_latency"); cols.add("io_misc_requests");
                cols.add("io_misc_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR");
                for (int i = 0; i < 17; i++) {
                    if (i % 2 == 0 || i == 16) typs.add(isRaw ? "BIGINT" : "VARCHAR");
                    else typs.add("BIGINT");
                }
                TableData td = new TableData("sys." + table, cols, typs);
                // Add rows for each user table
                if (engine.getActiveDatabase() != null) {
                    try {
                        JSONObject schema = engine.getStorageEngine().readSchema(engine.getActiveDatabase());
                        Iterator<String> it = schema.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            if (k.startsWith("__")) continue;
                            Map<String, Object> r = new HashMap<>();
                            r.put("table_schema", engine.getActiveDatabase());
                            r.put("table_name", k);
                            r.put("total_latency", isRaw ? 0L : "0.00 ms");
                            r.put("rows_fetched", 0L); r.put("fetch_latency", isRaw ? 0L : "0.00 ms");
                            r.put("rows_inserted", 0L); r.put("insert_latency", isRaw ? 0L : "0.00 ms");
                            r.put("rows_updated", 0L); r.put("update_latency", isRaw ? 0L : "0.00 ms");
                            r.put("rows_deleted", 0L); r.put("delete_latency", isRaw ? 0L : "0.00 ms");
                            r.put("io_read_requests", 0L); r.put("io_read", isRaw ? 0L : "0 bytes");
                            r.put("io_read_latency", isRaw ? 0L : "0.00 ms");
                            r.put("io_write_requests", 0L); r.put("io_write", isRaw ? 0L : "0 bytes");
                            r.put("io_write_latency", isRaw ? 0L : "0.00 ms");
                            r.put("io_misc_requests", 0L); r.put("io_misc_latency", isRaw ? 0L : "0.00 ms");
                            td.rows.add(r);
                        }
                    } catch (Exception e) { com.mysql.pocketsql.engine.SqlLog.printStackTrace(e); }
                }
                return td;
            }

            // ===== sys.schema_table_statistics_with_buffer =====
            if ("schema_table_statistics_with_buffer".equals(table) || "x$schema_table_statistics_with_buffer".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("table_schema"); cols.add("table_name"); cols.add("rows_fetched");
                cols.add("fetch_latency"); cols.add("rows_inserted"); cols.add("insert_latency");
                cols.add("rows_updated"); cols.add("update_latency"); cols.add("rows_deleted");
                cols.add("delete_latency"); cols.add("io_read_requests"); cols.add("io_read");
                cols.add("io_read_latency"); cols.add("io_write_requests"); cols.add("io_write");
                cols.add("io_write_latency"); cols.add("io_misc_requests"); cols.add("io_misc_latency");
                cols.add("innodb_buffer_allocated"); cols.add("innodb_buffer_data");
                cols.add("innodb_buffer_free"); cols.add("innodb_buffer_pages");
                cols.add("innodb_buffer_pages_hashed"); cols.add("innodb_buffer_pages_old");
                cols.add("innodb_buffer_rows_cached");
                for (int i = 0; i < 25; i++) typs.add(i < 2 ? "VARCHAR" : (isRaw ? "BIGINT" : "VARCHAR"));
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.schema_tables_with_full_table_scans =====
            if ("schema_tables_with_full_table_scans".equals(table) || "x$schema_tables_with_full_table_scans".equals(table)) {
                cols.add("object_schema"); cols.add("object_name"); cols.add("rows_full_scanned"); cols.add("latency");
                boolean isRaw = table.startsWith("x$");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.schema_unused_indexes =====
            if ("schema_unused_indexes".equals(table)) {
                cols.add("object_schema"); cols.add("object_name"); cols.add("index_name");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys.schema_unused_indexes", cols, typs);
                return td; // empty
            }

            // ===== sys.session =====
            if ("session".equals(table) || "x$session".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("thd_id"); cols.add("conn_id"); cols.add("user"); cols.add("db");
                cols.add("command"); cols.add("state"); cols.add("time");
                cols.add("current_statement"); cols.add("statement_latency");
                cols.add("progress"); cols.add("lock_latency");
                cols.add("rows_examined"); cols.add("rows_sent"); cols.add("rows_affected");
                cols.add("tmp_tables"); cols.add("tmp_disk_tables"); cols.add("full_scan");
                cols.add("last_statement"); cols.add("last_statement_latency");
                cols.add("current_memory"); cols.add("last_wait"); cols.add("last_wait_latency");
                cols.add("source"); cols.add("trx_latency"); cols.add("trx_state");
                cols.add("trx_autocommit"); cols.add("pid"); cols.add("program_name");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add("TEXT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("DECIMAL"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR");
                typs.add("TEXT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add("VARCHAR");
                typs.add("VARCHAR"); typs.add("BIGINT"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("thd_id", 49L); r.put("conn_id", 1L);
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("db", engine.getActiveDatabase());
                r.put("command", "Query"); r.put("state", "executing");
                r.put("time", 0L); r.put("current_statement", "SELECT * FROM sys.session");
                r.put("statement_latency", isRaw ? 0L : "0.00 ms");
                r.put("progress", null); r.put("lock_latency", isRaw ? 0L : "0.00 ms");
                r.put("rows_examined", 0L); r.put("rows_sent", 0L); r.put("rows_affected", 0L);
                r.put("tmp_tables", 0L); r.put("tmp_disk_tables", 0L); r.put("full_scan", "NO");
                r.put("last_statement", null); r.put("last_statement_latency", isRaw ? 0L : "0.00 ms");
                r.put("current_memory", isRaw ? 5096000L : "4.86 MiB");
                r.put("last_wait", null); r.put("last_wait_latency", isRaw ? 0L : "0.00 ms");
                r.put("source", ""); r.put("trx_latency", isRaw ? 0L : "0.00 ms");
                r.put("trx_state", "COMMITTED"); r.put("trx_autocommit", "YES");
                r.put("pid", 1L); r.put("program_name", "PocketSQL");
                td.rows.add(r);
                return td;
            }

            // ===== sys.session_ssl_status =====
            if ("session_ssl_status".equals(table)) {
                cols.add("thread_id"); cols.add("ssl_version"); cols.add("ssl_cipher"); cols.add("ssl_sessions_reused");
                typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                TableData td = new TableData("sys.session_ssl_status", cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("thread_id", 49L); r.put("ssl_version", "TLSv1.3");
                r.put("ssl_cipher", "TLS_AES_256_GCM_SHA384"); r.put("ssl_sessions_reused", 0L);
                td.rows.add(r);
                return td;
            }

            // ===== sys.statement_analysis =====
            if ("statement_analysis".equals(table) || "x$statement_analysis".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("full_scan"); cols.add("exec_count");
                cols.add("err_count"); cols.add("warn_count"); cols.add("total_latency");
                cols.add("max_latency"); cols.add("avg_latency"); cols.add("lock_latency");
                cols.add("rows_sent"); cols.add("rows_sent_avg"); cols.add("rows_examined");
                cols.add("rows_examined_avg"); cols.add("rows_affected"); cols.add("rows_affected_avg");
                cols.add("tmp_tables"); cols.add("tmp_disk_tables"); cols.add("rows_sorted");
                cols.add("sort_merge_passes"); cols.add("digest"); cols.add("first_seen"); cols.add("last_seen");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.statements_with_errors_or_warnings =====
            if ("statements_with_errors_or_warnings".equals(table) || "x$statements_with_errors_or_warnings".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("exec_count"); cols.add("errors");
                cols.add("error_pct"); cols.add("warnings"); cols.add("warning_pct");
                cols.add("first_seen"); cols.add("last_seen"); cols.add("digest");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("DECIMAL"); typs.add("BIGINT"); typs.add("DECIMAL");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.statements_with_full_table_scans =====
            if ("statements_with_full_table_scans".equals(table) || "x$statements_with_full_table_scans".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("exec_count"); cols.add("total_latency");
                cols.add("no_index_used_count"); cols.add("no_good_index_used_count");
                cols.add("no_index_used_pct"); cols.add("rows_sent"); cols.add("rows_examined");
                cols.add("rows_sent_avg"); cols.add("rows_examined_avg"); cols.add("first_seen");
                cols.add("last_seen"); cols.add("digest");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("DECIMAL");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.statements_with_runtimes_in_95th_percentile =====
            if ("statements_with_runtimes_in_95th_percentile".equals(table) || "x$statements_with_runtimes_in_95th_percentile".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("full_scan"); cols.add("exec_count");
                cols.add("err_count"); cols.add("warn_count"); cols.add("total_latency");
                cols.add("max_latency"); cols.add("avg_latency"); cols.add("rows_sent");
                cols.add("rows_sent_avg"); cols.add("rows_examined"); cols.add("rows_examined_avg");
                cols.add("first_seen"); cols.add("last_seen"); cols.add("digest");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.statements_with_sorting =====
            if ("statements_with_sorting".equals(table) || "x$statements_with_sorting".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("exec_count"); cols.add("total_latency");
                cols.add("sort_merge_passes"); cols.add("avg_sort_merges"); cols.add("sorts_using_scans");
                cols.add("sort_using_range"); cols.add("rows_sorted"); cols.add("avg_rows_sorted");
                cols.add("first_seen"); cols.add("last_seen"); cols.add("digest");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.statements_with_temp_tables =====
            if ("statements_with_temp_tables".equals(table) || "x$statements_with_temp_tables".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("query"); cols.add("db"); cols.add("exec_count"); cols.add("total_latency");
                cols.add("memory_tmp_tables"); cols.add("disk_tmp_tables");
                cols.add("avg_tmp_tables_per_query"); cols.add("tmp_tables_to_disk_pct");
                cols.add("first_seen"); cols.add("last_seen"); cols.add("digest");
                typs.add("TEXT"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT"); typs.add("DECIMAL");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== sys.wait_classes_global_by_avg_latency =====
            if ("wait_classes_global_by_avg_latency".equals(table) || "x$wait_classes_global_by_avg_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("event_class"); cols.add("total"); cols.add("total_latency");
                cols.add("min_latency"); cols.add("avg_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                String[] classes = {"wait/io/file", "wait/synch/mutex", "idle"};
                for (String cls : classes) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("event_class", cls); r.put("total", 45L);
                    r.put("total_latency", isRaw ? 5150000L : "5.15 ms");
                    r.put("min_latency", isRaw ? 1000L : "0.00 ms");
                    r.put("avg_latency", isRaw ? 114444L : "0.11 ms");
                    r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.wait_classes_global_by_latency =====
            if ("wait_classes_global_by_latency".equals(table) || "x$wait_classes_global_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("event_class"); cols.add("total"); cols.add("total_latency");
                cols.add("min_latency"); cols.add("avg_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                String[] classes = {"wait/io/file", "wait/synch/mutex", "idle"};
                for (String cls : classes) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("event_class", cls); r.put("total", 45L);
                    r.put("total_latency", isRaw ? 5150000L : "5.15 ms");
                    r.put("min_latency", isRaw ? 1000L : "0.00 ms");
                    r.put("avg_latency", isRaw ? 114444L : "0.11 ms");
                    r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                    td.rows.add(r);
                }
                return td;
            }

            // ===== sys.waits_by_host_by_latency =====
            if ("waits_by_host_by_latency".equals(table) || "x$waits_by_host_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("host"); cols.add("event"); cols.add("total"); cols.add("total_latency");
                cols.add("avg_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("host", "localhost"); r.put("event", "wait/io/file/innodb/innodb_data_file");
                r.put("total", 62L);
                r.put("total_latency", isRaw ? 10500000L : "10.50 ms");
                r.put("avg_latency", isRaw ? 169354L : "0.17 ms");
                r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.waits_by_user_by_latency =====
            if ("waits_by_user_by_latency".equals(table) || "x$waits_by_user_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("user"); cols.add("event"); cols.add("total"); cols.add("total_latency");
                cols.add("avg_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("user", engine.getCurrentUser() != null ? engine.getCurrentUser() : "root");
                r.put("event", "wait/io/file/innodb/innodb_data_file");
                r.put("total", 62L);
                r.put("total_latency", isRaw ? 10500000L : "10.50 ms");
                r.put("avg_latency", isRaw ? 169354L : "0.17 ms");
                r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys.waits_global_by_latency =====
            if ("waits_global_by_latency".equals(table) || "x$waits_global_by_latency".equals(table)) {
                boolean isRaw = table.startsWith("x$");
                cols.add("events"); cols.add("total"); cols.add("total_latency");
                cols.add("avg_latency"); cols.add("max_latency");
                typs.add("VARCHAR"); typs.add("BIGINT");
                typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR"); typs.add(isRaw ? "BIGINT" : "VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                Map<String, Object> r = new HashMap<>();
                r.put("events", "wait/io/file/innodb/innodb_data_file");
                r.put("total", 62L);
                r.put("total_latency", isRaw ? 10500000L : "10.50 ms");
                r.put("avg_latency", isRaw ? 169354L : "0.17 ms");
                r.put("max_latency", isRaw ? 1250000000L : "1.25 ms");
                td.rows.add(r);
                return td;
            }

            // ===== sys x$ digest helpers =====
            if ("x$ps_digest_95th_percentile_by_avg_us".equals(table)) {
                cols.add("avg_us"); cols.add("percentile");
                typs.add("DECIMAL"); typs.add("DECIMAL");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }
            if ("x$ps_digest_avg_latency_distribution".equals(table)) {
                cols.add("cnt"); cols.add("avg_us");
                typs.add("BIGINT"); typs.add("DECIMAL");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }
            if ("x$ps_schema_table_statistics_io".equals(table)) {
                cols.add("table_schema"); cols.add("table_name"); cols.add("count_read");
                cols.add("sum_number_of_bytes_read"); cols.add("count_write");
                cols.add("sum_number_of_bytes_write"); cols.add("count_misc");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("BIGINT"); typs.add("BIGINT");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("BIGINT");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }
            if ("x$schema_flattened_keys".equals(table)) {
                cols.add("table_schema"); cols.add("table_name"); cols.add("index_name");
                cols.add("non_unique"); cols.add("subpart_exists"); cols.add("index_columns");
                typs.add("VARCHAR"); typs.add("VARCHAR"); typs.add("VARCHAR");
                typs.add("BIGINT"); typs.add("BIGINT"); typs.add("VARCHAR");
                TableData td = new TableData("sys." + table, cols, typs);
                return td; // empty
            }

            // ===== Fallback for any unhandled sys table =====
            for (String sysTbl : SYS_SYSTEM_TABLES) {
                if (sysTbl.equalsIgnoreCase(table)) {
                    cols.add("name"); cols.add("value"); cols.add("description");
                    cols.add("status"); cols.add("type");
                    for (int i = 0; i < 5; i++) typs.add("VARCHAR");
                    TableData td = new TableData("sys." + sysTbl, cols, typs);
                    return td;
                }
            }
        }

        throw new Exception("Table '" + db + "." + table + "' does not exist");
    }

    private void addVirtualSystemTablesForTables(TableData td) {
        for (String tbl : INFO_SCHEMA_SYSTEM_TABLES) {
            Map<String, Object> r = new HashMap<>();
            r.put("TABLE_CATALOG", "def");
            r.put("TABLE_SCHEMA", "information_schema");
            r.put("TABLE_NAME", tbl);
            r.put("TABLE_TYPE", "SYSTEM VIEW");
            r.put("ENGINE", "PocketSQL");
            r.put("VERSION", "10");
            r.put("ROW_FORMAT", "Dynamic");
            r.put("TABLE_ROWS", "0");
            r.put("DATA_LENGTH", "0");
            r.put("INDEX_LENGTH", "0");
            r.put("CREATE_TIME", null);
            r.put("UPDATE_TIME", null);
            r.put("CHECK_TIME", null);
            r.put("TABLE_COLLATION", "utf8mb4_0900_ai_ci");
            r.put("CHECKSUM", null);
            r.put("CREATE_OPTIONS", null);
            r.put("TABLE_COMMENT", "");
            td.rows.add(r);
        }

        for (String tbl : POCKETSQL_SYSTEM_TABLES) {
            Map<String, Object> r = new HashMap<>();
            r.put("TABLE_CATALOG", "def");
            r.put("TABLE_SCHEMA", "pocketsql");
            r.put("TABLE_NAME", tbl);
            r.put("TABLE_TYPE", "BASE TABLE");
            r.put("ENGINE", "PocketSQL");
            r.put("VERSION", "10");
            r.put("ROW_FORMAT", "Dynamic");
            r.put("TABLE_ROWS", "0");
            r.put("DATA_LENGTH", "0");
            r.put("INDEX_LENGTH", "0");
            r.put("CREATE_TIME", null);
            r.put("UPDATE_TIME", null);
            r.put("CHECK_TIME", null);
            r.put("TABLE_COLLATION", "utf8mb4_0900_ai_ci");
            r.put("CHECKSUM", null);
            r.put("CREATE_OPTIONS", null);
            r.put("TABLE_COMMENT", "");
            td.rows.add(r);
        }

        for (String tbl : SYS_SYSTEM_TABLES) {
            Map<String, Object> r = new HashMap<>();
            r.put("TABLE_CATALOG", "def");
            r.put("TABLE_SCHEMA", "sys");
            r.put("TABLE_NAME", tbl);
            r.put("TABLE_TYPE", "version".equals(tbl) ? "VIEW" : "BASE TABLE");
            r.put("ENGINE", "PocketSQL");
            r.put("VERSION", "10");
            r.put("ROW_FORMAT", "Dynamic");
            r.put("TABLE_ROWS", "0");
            r.put("DATA_LENGTH", "0");
            r.put("INDEX_LENGTH", "0");
            r.put("CREATE_TIME", null);
            r.put("UPDATE_TIME", null);
            r.put("CHECK_TIME", null);
            r.put("TABLE_COLLATION", "utf8mb4_0900_ai_ci");
            r.put("CHECKSUM", null);
            r.put("CREATE_OPTIONS", null);
            r.put("TABLE_COMMENT", "");
            td.rows.add(r);
        }
    }

    private void addVirtualSystemColumns(DatabaseEngine engine, TableData td) {
        String[] systemDbs = {"information_schema", "pocketsql", "sys"};
        for (String db : systemDbs) {
            List<String> tables = getSystemTables(db);
            for (String tbl : tables) {
                if ("information_schema".equals(db) && "columns".equalsIgnoreCase(tbl)) {
                    addSystemCol(td, "information_schema", "COLUMNS", "TABLE_CATALOG", 1, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "TABLE_SCHEMA", 2, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "TABLE_NAME", 3, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "COLUMN_NAME", 4, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "ORDINAL_POSITION", 5, "BIGINT");
                    addSystemCol(td, "information_schema", "COLUMNS", "COLUMN_DEFAULT", 6, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "IS_NULLABLE", 7, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "DATA_TYPE", 8, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "CHARACTER_MAXIMUM_LENGTH", 9, "BIGINT");
                    addSystemCol(td, "information_schema", "COLUMNS", "COLUMN_TYPE", 10, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "COLUMN_KEY", 11, "VARCHAR");
                    addSystemCol(td, "information_schema", "COLUMNS", "EXTRA", 12, "VARCHAR");
                    continue;
                }
                
                try {
                    TableData tableData = getSystemTable(engine, db, tbl);
                    for (int i = 0; i < tableData.columns.size(); i++) {
                        String colName = tableData.columns.get(i);
                        String colType = tableData.types.get(i);
                        addSystemCol(td, db, tbl, colName, i + 1, colType);
                    }
                } catch (Exception e) {
                    com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
                }
            }
        }
    }

    private void addSystemCol(TableData td, String db, String tbl, String col, int pos, String type) {
        Map<String, Object> r = new HashMap<>();
        r.put("TABLE_CATALOG", "def");
        r.put("TABLE_SCHEMA", db);
        r.put("TABLE_NAME", tbl);
        r.put("COLUMN_NAME", col);
        r.put("ORDINAL_POSITION", (long) pos);
        r.put("COLUMN_DEFAULT", null);
        r.put("IS_NULLABLE", "YES");
        r.put("DATA_TYPE", type);
        r.put("CHARACTER_MAXIMUM_LENGTH", 255L);
        r.put("COLUMN_TYPE", type);
        r.put("COLUMN_KEY", "");
        r.put("EXTRA", "");
        td.rows.add(r);
    }

    private boolean hasGlobalPrivilege(JSONObject cachedUsers, String userKey, String privilege) {
        try {
            JSONObject userObj = cachedUsers.optJSONObject(userKey);
            if (userObj == null) return false;
            JSONObject privs = userObj.optJSONObject("privileges");
            if (privs == null) return false;
            JSONArray global = privs.optJSONArray("*.*");
            if (global != null) {
                for (int i = 0; i < global.length(); i++) {
                    if (global.getString(i).equalsIgnoreCase(privilege)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        return false;
    }
}
