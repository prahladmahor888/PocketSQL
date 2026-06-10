package com.mysql.pocketsql.engine;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class SqlPrivilegeManager {
    private final DatabaseEngine engine;

    public SqlPrivilegeManager(DatabaseEngine engine) {
        this.engine = engine;
    }

    public void verifyPrivilege(String privilege, String db, String table) throws Exception {
        if (engine.currentUser == null) {
            throw new Exception("Error: Access denied; you need (at least one of) the USAGE privilege(s) for this operation");
        }
        if (hasExactPrivilege("ALL", "*", "*")) {
            return;
        }
        if (db != null && hasExactPrivilege("ALL", db, "*")) {
            return;
        }
        if (db != null && table != null && hasExactPrivilege("ALL", db, table)) {
            return;
        }
        if (hasExactPrivilege(privilege, "*", "*")) {
            return;
        }
        if (db != null && hasExactPrivilege(privilege, db, "*")) {
            return;
        }
        if (db != null && table != null && hasExactPrivilege(privilege, db, table)) {
            return;
        }
        throw new Exception("Error: Access denied; you need (at least one of) the " + privilege + " privilege(s) for this operation");
    }

    public boolean hasExactPrivilege(String privilege, String db, String table) {
        if (engine.currentUser == null) return false;
        try {
            String key = engine.currentUser + "@" + engine.currentHost;
            JSONObject userObj = engine.cachedUsers.optJSONObject(key);
            if (userObj == null) {
                userObj = engine.cachedUsers.optJSONObject(engine.currentUser + "@localhost");
            }
            if (userObj == null) return false;
            
            JSONObject privileges = userObj.optJSONObject("privileges");
            if (privileges == null) return false;
            
            String pattern = db + "." + table;
            JSONArray privArray = privileges.optJSONArray(pattern);
            if (privArray != null) {
                for (int i = 0; i < privArray.length(); i++) {
                    String priv = privArray.getString(i);
                    if (priv.equalsIgnoreCase(privilege)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
        return false;
    }

    public QueryResult createUser(String username, String host, String password) throws Exception {
        verifyPrivilege("ALL", "*", "*");
        
        String key = username + "@" + host;
        if (engine.cachedUsers.has(key)) {
            throw new Exception("Error: Operation CREATE USER failed for '" + username + "'@'" + host + "'");
        }
        
        JSONObject userObj = new JSONObject();
        userObj.put("password", SecurityHelper.hashPassword(password));
        userObj.put("privileges", new JSONObject());
        
        engine.cachedUsers.put(key, userObj);
        engine.storageEngine.writeUsers(engine.cachedUsers);
        
        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult dropUser(String username, String host) throws Exception {
        return dropUser(username, host, false);
    }

    public QueryResult dropUser(String username, String host, boolean ifExists) throws Exception {
        verifyPrivilege("ALL", "*", "*");
        
        String key = username + "@" + host;
        if (!engine.cachedUsers.has(key)) {
            if (ifExists) {
                return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
            }
            throw new Exception("Error: Operation DROP USER failed for '" + username + "'@'" + host + "'");
        }
        
        engine.cachedUsers.remove(key);
        engine.storageEngine.writeUsers(engine.cachedUsers);
        
        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult grantPrivileges(List<String> privileges, String dbPattern, String username, String host) throws Exception {
        verifyPrivilege("ALL", "*", "*");
        
        String key = username + "@" + host;
        if (!engine.cachedUsers.has(key)) {
            throw new Exception("Error: User '" + username + "'@'" + host + "' does not exist");
        }
        
        JSONObject userObj = engine.cachedUsers.getJSONObject(key);
        JSONObject userPrivs = userObj.optJSONObject("privileges");
        if (userPrivs == null) {
            userPrivs = new JSONObject();
            userObj.put("privileges", userPrivs);
        }
        
        JSONArray privArray = userPrivs.optJSONArray(dbPattern);
        if (privArray == null) {
            privArray = new JSONArray();
            userPrivs.put(dbPattern, privArray);
        }
        
        for (String privilege : privileges) {
            boolean exists = false;
            for (int i = 0; i < privArray.length(); i++) {
                if (privArray.getString(i).equalsIgnoreCase(privilege)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                privArray.put(privilege.toUpperCase());
            }
        }
        
        engine.storageEngine.writeUsers(engine.cachedUsers);
        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult revokePrivileges(List<String> privileges, String dbPattern, String username, String host) throws Exception {
        verifyPrivilege("ALL", "*", "*");

        String key = username + "@" + host;
        if (!engine.cachedUsers.has(key)) {
            throw new Exception("Error: User '" + username + "'@'" + host + "' does not exist");
        }

        JSONObject userObj = engine.cachedUsers.getJSONObject(key);
        JSONObject userPrivs = userObj.optJSONObject("privileges");
        if (userPrivs == null || !userPrivs.has(dbPattern)) {
            return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
        }

        JSONArray privArray = userPrivs.getJSONArray(dbPattern);
        JSONArray newPrivArray = new JSONArray();
        for (int i = 0; i < privArray.length(); i++) {
            String p = privArray.getString(i);
            boolean revokeThis = false;
            for (String revokePriv : privileges) {
                if (revokePriv.equalsIgnoreCase(p) || "ALL".equalsIgnoreCase(revokePriv)) {
                    revokeThis = true;
                    break;
                }
            }
            if (!revokeThis) {
                newPrivArray.put(p);
            }
        }

        if (newPrivArray.length() > 0) {
            userPrivs.put(dbPattern, newPrivArray);
        } else {
            userPrivs.remove(dbPattern);
        }

        engine.storageEngine.writeUsers(engine.cachedUsers);
        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public QueryResult flushPrivileges() throws Exception {
        verifyPrivilege("ALL", "*", "*");
        engine.loadUsers();
        return QueryResult.createSuccess("Query OK, 0 rows affected", 0, 0);
    }

    public void initializeAdminUser(String username, String host, String password) throws Exception {
        JSONObject defaultUsers = new JSONObject();
        JSONObject adminUser = new JSONObject();
        adminUser.put("password", SecurityHelper.hashPassword(password));
        JSONObject privs = new JSONObject();
        JSONArray adminPrivs = new JSONArray();
        adminPrivs.put("ALL");
        privs.put("*.*", adminPrivs);
        adminUser.put("privileges", privs);
        defaultUsers.put(username + "@" + host, adminUser);
        engine.storageEngine.writeUsers(defaultUsers);
        engine.loadUsers();
    }

    public void initializeDefaultRootUser() {
        try {
            initializeAdminUser("root", "localhost", "");
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
        }
    }
}
