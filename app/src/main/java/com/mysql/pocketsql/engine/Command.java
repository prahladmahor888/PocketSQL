package com.mysql.pocketsql.engine;

import java.util.List;
import java.util.Map;

public interface Command {
    QueryResult execute(DatabaseEngine engine) throws Exception;

    class CreateDatabase implements Command {
        public final String dbName;
        public final boolean ifNotExists;
        public final String charset;
        public final String collation;

        public CreateDatabase(String dbName, boolean ifNotExists) {
            this(dbName, ifNotExists, null, null);
        }

        public CreateDatabase(String dbName, boolean ifNotExists, String charset, String collation) {
            this.dbName = dbName;
            this.ifNotExists = ifNotExists;
            this.charset = charset;
            this.collation = collation;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createDatabase(dbName, ifNotExists, charset, collation);
        }
    }

    class DropDatabase implements Command {
        public final String dbName;
        public final boolean ifExists;

        public DropDatabase(String dbName, boolean ifExists) {
            this.dbName = dbName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropDatabase(dbName, ifExists);
        }
    }

    class UseDatabase implements Command {
        public final String dbName;

        public UseDatabase(String dbName) {
            this.dbName = dbName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.useDatabase(dbName);
        }
    }

    class CreateTable implements Command {
        public final String tableName;
        public final List<String> columnNames;
        public final List<String> columnTypes;
        public final Map<String, String> columnDefaults;
        public final Map<String, String> columnOnUpdates;
        public final Map<String, Boolean> columnNullables;
        public final Map<String, String> columnKeys;
        public final Map<String, String> columnExtras;
        public final List<Map<String, Object>> checks;
        public final Map<String, String> foreignKeys;
        public final List<String> primaryKey;
        public final List<List<String>> uniques;
        public final Map<String, SqlAttributes> columnAttributes = new java.util.HashMap<>();
        public final Map<List<String>, String> uniqueNames = new java.util.HashMap<>();
        public final boolean ifNotExists;
        public String charset = null;
        public String collation = null;
        public String definition = null;

        public CreateTable(String tableName, List<String> columnNames, List<String> columnTypes,
                           Map<String, String> columnDefaults, Map<String, String> columnOnUpdates,
                           Map<String, Boolean> columnNullables, Map<String, String> columnKeys,
                           Map<String, String> columnExtras, List<Map<String, Object>> checks,
                           Map<String, String> foreignKeys, List<String> primaryKey,
                           List<List<String>> uniques, boolean ifNotExists) {
            this.tableName = tableName;
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.columnDefaults = columnDefaults;
            this.columnOnUpdates = columnOnUpdates;
            this.columnNullables = columnNullables;
            this.columnKeys = columnKeys;
            this.columnExtras = columnExtras;
            this.checks = checks;
            this.foreignKeys = foreignKeys;
            this.primaryKey = primaryKey;
            this.uniques = uniques;
            this.ifNotExists = ifNotExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createTable(tableName, columnNames, columnTypes,
                                      columnDefaults, columnOnUpdates, columnNullables,
                                      columnKeys, columnExtras, checks, foreignKeys,
                                      primaryKey, uniques, ifNotExists, charset, collation,
                                      columnAttributes, uniqueNames, definition);
        }
    }

    class CreateIndex implements Command {
        public final String tableName;
        public final String indexName;
        public final List<String> columns;
        public final boolean unique;

        public CreateIndex(String tableName, String indexName, List<String> columns, boolean unique) {
            this.tableName = tableName;
            this.indexName = indexName;
            this.columns = columns;
            this.unique = unique;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createIndex(tableName, indexName, columns, unique);
        }
    }

    class DropTable implements Command {
        public final String tableName;
        public final boolean ifExists;

        public DropTable(String tableName, boolean ifExists) {
            this.tableName = tableName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropTable(tableName, ifExists);
        }
    }

    class Insert implements Command {
        public final String tableName;
        public final List<String> columnNames;
        public final List<List<Object>> valuesList;
        public java.util.Map<String, String> updateAssignments = null;

        public Insert(String tableName, List<String> columnNames, List<List<Object>> valuesList) {
            this.tableName = tableName;
            this.columnNames = columnNames;
            this.valuesList = valuesList;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.insertInto(tableName, columnNames, valuesList, updateAssignments);
        }
    }

    class Select implements Command {
        public final List<String> projection;
        public final String tableName;
        public final Clause.Where where;
        public final String orderByColumn;
        public final boolean orderAsc;
        public final Integer limit;

        // New clause fields
        public final boolean distinct;
        public final Map<String, String> aliases;
        public final List<Clause.Join> joins;
        public final Clause.GroupBy groupBy;
        public final Clause.Having having;
        public final Clause.Union union;

        public final Map<String, String> tableAliases;
        public final List<Clause.OrderBy> orderBySpecs;

        public Select(List<String> projection, String tableName, Clause.Where where, 
                      String orderByColumn, boolean orderAsc, Integer limit,
                      boolean distinct, Map<String, String> aliases, List<Clause.Join> joins,
                      Clause.GroupBy groupBy, Clause.Having having, Clause.Union union) {
            this(projection, tableName, where,
                 orderByColumn == null ? null : java.util.Arrays.asList(new Clause.OrderBy(orderByColumn, orderAsc)),
                 limit, distinct, aliases, joins, groupBy, having, union, null);
        }

        public Select(List<String> projection, String tableName, Clause.Where where, 
                      String orderByColumn, boolean orderAsc, Integer limit,
                      boolean distinct, Map<String, String> aliases, List<Clause.Join> joins,
                      Clause.GroupBy groupBy, Clause.Having having, Clause.Union union,
                      Map<String, String> tableAliases) {
            this(projection, tableName, where,
                 orderByColumn == null ? null : java.util.Arrays.asList(new Clause.OrderBy(orderByColumn, orderAsc)),
                 limit, distinct, aliases, joins, groupBy, having, union, tableAliases);
        }

        public Select(List<String> projection, String tableName, Clause.Where where, 
                      List<Clause.OrderBy> orderBySpecs, Integer limit,
                      boolean distinct, Map<String, String> aliases, List<Clause.Join> joins,
                      Clause.GroupBy groupBy, Clause.Having having, Clause.Union union,
                      Map<String, String> tableAliases) {
            this.projection = projection;
            this.tableName = tableName;
            this.where = where;
            this.orderBySpecs = orderBySpecs;
            if (orderBySpecs != null && !orderBySpecs.isEmpty()) {
                this.orderByColumn = orderBySpecs.get(0).column;
                this.orderAsc = orderBySpecs.get(0).asc;
            } else {
                this.orderByColumn = null;
                this.orderAsc = true;
            }
            this.limit = limit;
            this.distinct = distinct;
            this.aliases = aliases;
            this.joins = joins;
            this.groupBy = groupBy;
            this.having = having;
            this.union = union;
            this.tableAliases = tableAliases;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.selectFrom(this);
        }
    }

    class Update implements Command {
        public final String tableName;
        public final Map<String, Object> updates;
        public final Clause.Where where;

        public Update(String tableName, Map<String, Object> updates, Clause.Where where) {
            this.tableName = tableName;
            this.updates = updates;
            this.where = where;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.updateTable(tableName, updates, where);
        }
    }

    class Delete implements Command {
        public final String tableName;
        public final Clause.Where where;

        public Delete(String tableName, Clause.Where where) {
            this.tableName = tableName;
            this.where = where;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.deleteFrom(tableName, where);
        }
    }

    class ShowDatabases implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showDatabases();
        }
    }

    class ShowTables implements Command {
        public final boolean full;
        public final String databaseName;
        public final Clause.Where where;

        public ShowTables() {
            this(false, null, null);
        }

        public ShowTables(boolean full, Clause.Where where) {
            this(full, null, where);
        }

        public ShowTables(boolean full, String databaseName, Clause.Where where) {
            this.full = full;
            this.databaseName = databaseName;
            this.where = where;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showTables(databaseName, full, where);
        }
    }

    class ShowCharacterSets implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCharacterSets();
        }
    }

    class ShowCollations implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCollations();
        }
    }

    class ShowCreateDatabase implements Command {
        public final String dbName;
        public ShowCreateDatabase(String dbName) {
            this.dbName = dbName;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCreateDatabase(dbName);
        }
    }

    class ShowCreateTable implements Command {
        public final String tableName;
        public ShowCreateTable(String tableName) {
            this.tableName = tableName;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCreateTable(tableName);
        }
    }

    class AlterDatabase implements Command {
        public final String dbName;
        public final String charset;
        public final String collation;
        public AlterDatabase(String dbName, String charset, String collation) {
            this.dbName = dbName;
            this.charset = charset;
            this.collation = collation;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.alterDatabase(dbName, charset, collation);
        }
    }

    class AlterTableConvert implements Command {
        public final String tableName;
        public final String charset;
        public final String collation;
        public AlterTableConvert(String tableName, String charset, String collation) {
            this.tableName = tableName;
            this.charset = charset;
            this.collation = collation;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.alterTableConvert(tableName, charset, collation);
        }
    }

    class DescribeTable implements Command {
        public final String tableName;

        public DescribeTable(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.describeTable(tableName);
        }
    }

    class ShowIndexes implements Command {
        public final String tableName;

        public ShowIndexes(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showIndexes(tableName);
        }
    }

    class CreateFunction implements Command {
        public final String name;
        public final List<String> paramNames;
        public final List<String> paramTypes;
        public final String returnType;
        public final List<SqlToken> bodyTokens;
        public String definition = null;

        public CreateFunction(String name, List<String> paramNames, List<String> paramTypes,
                              String returnType, List<SqlToken> bodyTokens) {
            this.name = name;
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
            this.returnType = returnType;
            this.bodyTokens = bodyTokens;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createFunction(name, paramNames, paramTypes, returnType, bodyTokens, definition);
        }
    }

    class DropFunction implements Command {
        public final String name;
        public final boolean ifExists;

        public DropFunction(String name, boolean ifExists) {
            this.name = name;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropFunction(name, ifExists);
        }
    }

    class ShowFunctionStatus implements Command {
        public final Clause.Where where;

        public ShowFunctionStatus() {
            this(null);
        }

        public ShowFunctionStatus(Clause.Where where) {
            this.where = where;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showFunctionStatus(where);
        }
    }

    class CreateUser implements Command {
        public final String username;
        public final String host;
        public final String password;

        public CreateUser(String username, String host, String password) {
            this.username = username;
            this.host = host;
            this.password = password;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createUser(username, host, password);
        }
    }

    class Grant implements Command {
        public final List<String> privileges;
        public final String dbPattern;
        public final String username;
        public final String host;

        public Grant(List<String> privileges, String dbPattern, String username, String host) {
            this.privileges = privileges;
            this.dbPattern = dbPattern;
            this.username = username;
            this.host = host;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.grantPrivileges(privileges, dbPattern, username, host);
        }
    }

    class FlushPrivileges implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.flushPrivileges();
        }
    }

    class ColumnDef {
        public final String name;
        public final String type;
        public final boolean nullable;
        public final String defaultValue;
        public final String onUpdateValue;
        public final boolean isAutoIncrement;
        public final boolean isPrimaryKey;
        public final boolean isUnique;
        public SqlAttributes attributes = null;
        public final List<Map<String, Object>> checks = new java.util.ArrayList<>();

        public ColumnDef(String name, String type, boolean nullable, String defaultValue, String onUpdateValue,
                         boolean isAutoIncrement, boolean isPrimaryKey, boolean isUnique) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.onUpdateValue = onUpdateValue;
            this.isAutoIncrement = isAutoIncrement;
            this.isPrimaryKey = isPrimaryKey;
            this.isUnique = isUnique;
        }
    }

    class PositionSpec {
        public final String position; // FIRST or AFTER
        public final String targetColumn;

        public PositionSpec(String position, String targetColumn) {
            this.position = position;
            this.targetColumn = targetColumn;
        }
    }

    class AlterTable implements Command {
        public final String tableName;
        public final String operation; // ADD_COLUMN, MODIFY_COLUMN, CHANGE_COLUMN, RENAME_COLUMN, DROP_COLUMN, ADD_PRIMARY_KEY, DROP_PRIMARY_KEY, ADD_FOREIGN_KEY, DROP_FOREIGN_KEY, ADD_UNIQUE, DROP_INDEX, ADD_INDEX, RENAME_TABLE, DEFAULT_VALUE_CHANGE, ADD_CHECK, DROP_CHECK, ENGINE_CHANGE, CHARACTER_SET_CHANGE
        
        public String columnName;
        public String newColumnName;
        public String targetColumn;
        public String position;
        public String constraintName;
        public List<String> columnsList;
        public ColumnDef columnDef;
        public String referenceTable;
        public List<String> referenceColumns;
        public Map<String, Object> checkConstraint;
        public String renameToTable;
        public String engineName;
        public String characterSet;
        public String defaultValue;
        public boolean dropDefault;
        public String onUpdateValue;
        public boolean dropOnUpdate;
        public String indexType;

        // 1. ADD_COLUMN / MODIFY_COLUMN
        public AlterTable(String tableName, String operation, ColumnDef columnDef, PositionSpec posSpec) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnDef = columnDef;
            if (posSpec != null) {
                this.position = posSpec.position;
                this.targetColumn = posSpec.targetColumn;
            }
        }

        // 2. CHANGE_COLUMN
        public AlterTable(String tableName, String operation, String columnName, ColumnDef columnDef, PositionSpec posSpec) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnName = columnName;
            this.columnDef = columnDef;
            if (posSpec != null) {
                this.position = posSpec.position;
                this.targetColumn = posSpec.targetColumn;
            }
        }

        // 3. RENAME_COLUMN / RENAME_TABLE
        public AlterTable(String tableName, String operation, String oldName, String newName) {
            this.tableName = tableName;
            this.operation = operation;
            if ("RENAME_COLUMN".equals(operation)) {
                this.columnName = oldName;
                this.newColumnName = newName;
            } else if ("RENAME_TABLE".equals(operation)) {
                this.renameToTable = newName;
            }
        }

        // 4. DROP_COLUMN / DROP_FOREIGN_KEY / DROP_INDEX / DROP_CHECK
        public AlterTable(String tableName, String operation, String target) {
            this.tableName = tableName;
            this.operation = operation;
            if ("DROP_COLUMN".equals(operation)) {
                this.columnName = target;
            } else {
                this.constraintName = target;
            }
        }

        // 5. DROP_PRIMARY_KEY / generic
        public AlterTable(String tableName, String operation) {
            this.tableName = tableName;
            this.operation = operation;
        }

        // 6. ADD_PRIMARY_KEY
        public AlterTable(String tableName, String operation, List<String> columnsList) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnsList = columnsList;
        }

        // 7. ADD_FOREIGN_KEY
        public AlterTable(String tableName, String operation, List<String> columnsList, String referenceTable, List<String> referenceColumns) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnsList = columnsList;
            this.referenceTable = referenceTable;
            this.referenceColumns = referenceColumns;
        }

        // 8. ADD_UNIQUE / ADD_INDEX
        public AlterTable(String tableName, String operation, List<String> columnsList, String constraintName) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnsList = columnsList;
            this.constraintName = constraintName;
        }

        // 9. ADD_CHECK
        public AlterTable(String tableName, String operation, Map<String, Object> checkConstraint) {
            this.tableName = tableName;
            this.operation = operation;
            this.checkConstraint = checkConstraint;
        }

        // 10. DEFAULT_VALUE_CHANGE
        public AlterTable(String tableName, String operation, String columnName, String defaultValue, boolean dropDefault) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnName = columnName;
            this.defaultValue = defaultValue;
            this.dropDefault = dropDefault;
        }

        // 12. ON_UPDATE_CHANGE
        public AlterTable(String tableName, String operation, String columnName, String onUpdateValue, boolean dropOnUpdate, boolean isOnUpdate) {
            this.tableName = tableName;
            this.operation = operation;
            this.columnName = columnName;
            this.onUpdateValue = onUpdateValue;
            this.dropOnUpdate = dropOnUpdate;
        }

        // 11. Factory creator for engine/charset
        public static AlterTable createEngineOrCharSet(String tableName, String operation, String value) {
            AlterTable cmd = new AlterTable(tableName, operation);
            if ("ENGINE_CHANGE".equals(operation)) {
                cmd.engineName = value;
            } else if ("CHARACTER_SET_CHANGE".equals(operation)) {
                cmd.characterSet = value;
            }
            return cmd;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.alterTable(this);
        }
    }

    class TruncateTable implements Command {
        public final String tableName;

        public TruncateTable(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.truncateTable(tableName);
        }
    }

    class RenameTable implements Command {
        public final String oldName;
        public final String newName;

        public RenameTable(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.renameTable(oldName, newName);
        }
    }

    class Revoke implements Command {
        public final List<String> privileges;
        public final String dbPattern;
        public final String username;
        public final String host;

        public Revoke(List<String> privileges, String dbPattern, String username, String host) {
            this.privileges = privileges;
            this.dbPattern = dbPattern;
            this.username = username;
            this.host = host;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.revokePrivileges(privileges, dbPattern, username, host);
        }
    }

    class StartTransaction implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.startTransaction();
        }
    }

    class Commit implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.commitTransaction();
        }
    }

    class Rollback implements Command {
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.rollbackTransaction();
        }
    }

    class Savepoint implements Command {
        public final String name;

        public Savepoint(String name) {
            this.name = name;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createSavepoint(name);
        }
    }

    class RollbackTo implements Command {
        public final String name;

        public RollbackTo(String name) {
            this.name = name;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.rollbackToSavepoint(name);
        }
    }

    class SetVariable implements Command {
        public final String name;
        public final Object value;

        public SetVariable(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.setVariable(name, value);
        }
    }

    class CreateView implements Command {
        public final String viewName;
        public final String selectQuery;
        public String definition = null;

        public CreateView(String viewName, String selectQuery) {
            this.viewName = viewName;
            this.selectQuery = selectQuery;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createView(viewName, selectQuery, definition);
        }
    }

    class DropView implements Command {
        public final String viewName;
        public final boolean ifExists;

        public DropView(String viewName, boolean ifExists) {
            this.viewName = viewName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropView(viewName, ifExists);
        }
    }

    class CreateProcedure implements Command {
        public final String procName;
        public final String procDef;

        public CreateProcedure(String procName, String procDef) {
            this.procName = procName;
            this.procDef = procDef;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createProcedure(procName, procDef);
        }
    }

    class DropProcedure implements Command {
        public final String procName;
        public final boolean ifExists;

        public DropProcedure(String procName, boolean ifExists) {
            this.procName = procName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropProcedure(procName, ifExists);
        }
    }

    class CreateTrigger implements Command {
        public final String triggerName;
        public final String triggerDef;

        public CreateTrigger(String triggerName, String triggerDef) {
            this.triggerName = triggerName;
            this.triggerDef = triggerDef;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createTrigger(triggerName, triggerDef);
        }
    }

    class DropTrigger implements Command {
        public final String triggerName;
        public final boolean ifExists;

        public DropTrigger(String triggerName, boolean ifExists) {
            this.triggerName = triggerName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropTrigger(triggerName, ifExists);
        }
    }

    class CreateEvent implements Command {
        public final String eventName;
        public final String eventDef;

        public CreateEvent(String eventName, String eventDef) {
            this.eventName = eventName;
            this.eventDef = eventDef;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.createEvent(eventName, eventDef);
        }
    }

    class DropEvent implements Command {
        public final String eventName;
        public final boolean ifExists;

        public DropEvent(String eventName, boolean ifExists) {
            this.eventName = eventName;
            this.ifExists = ifExists;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.dropEvent(eventName, ifExists);
        }
    }

    class ShowProcedureStatus implements Command {
        public final Clause.Where where;

        public ShowProcedureStatus(Clause.Where where) {
            this.where = where;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showProcedureStatus(where);
        }
    }

    class ShowCreateProcedure implements Command {
        public final String procName;

        public ShowCreateProcedure(String procName) {
            this.procName = procName;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCreateProcedure(procName);
        }
    }

    class ShowCreateView implements Command {
        public final String viewName;
        public ShowCreateView(String viewName) {
            this.viewName = viewName;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCreateView(viewName);
        }
    }

    class ShowCreateFunction implements Command {
        public final String functionName;
        public ShowCreateFunction(String functionName) {
            this.functionName = functionName;
        }
        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.showCreateFunction(functionName);
        }
    }

    class CallProcedure implements Command {
        public final String procName;
        public final List<Object> args;

        public CallProcedure(String procName, List<Object> args) {
            this.procName = procName;
            this.args = args;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.callProcedure(procName, args);
        }
    }

    class Help implements Command {
        public final String topic;

        public Help(String topic) {
            this.topic = topic;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.help(topic);
        }
    }

    class ExportDatabase implements Command {
        public final String dbName;
        public final String filePath;

        public ExportDatabase(String dbName, String filePath) {
            this.dbName = dbName;
            this.filePath = filePath;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.exportDatabase(dbName, filePath);
        }
    }

    class ImportDatabase implements Command {
        public final String dbName;
        public final String filePath;

        public ImportDatabase(String dbName, String filePath) {
            this.dbName = dbName;
            this.filePath = filePath;
        }

        @Override
        public QueryResult execute(DatabaseEngine engine) throws Exception {
            return engine.importDatabase(dbName, filePath);
        }
    }
}
