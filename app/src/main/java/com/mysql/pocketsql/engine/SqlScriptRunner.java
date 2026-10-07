package com.mysql.pocketsql.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SqlScriptRunner {

    public interface ScriptProgressListener {
        void onStatementExecuted(String dbName, int currentStmt, int percent, String statementSample);
    }

    /**
     * Executes a SQL script from an input stream statement-by-statement,
     * supporting dynamic delimiters, statement progress reporting, and CPU time-slice yielding.
     *
     * @param engine       The DatabaseEngine instance.
     * @param inputStream  The input stream containing the SQL script.
     * @param useDbAfter   Optional database name to switch to after execution.
     * @param listener     Optional listener for progress updates.
     * @throws Exception if an error occurs during execution.
     */
    public static void runScript(DatabaseEngine engine, InputStream inputStream, String useDbAfter, ScriptProgressListener listener) throws Exception {
        if (useDbAfter != null && !useDbAfter.isEmpty()) {
            engine.execute("CREATE DATABASE IF NOT EXISTS `" + useDbAfter + "`;");
            engine.useDatabase(useDbAfter);
        }

        int yieldInterval = SqlEnvConfig.getDbSeedBatchYield();
        if (yieldInterval <= 0) yieldInterval = 20;

        int statementCounter = 0;
        int totalBytes = 0;
        try {
            totalBytes = inputStream.available();
        } catch (Exception ignored) {}
        long bytesProcessed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder statementBuilder = new StringBuilder();
            String currentDelim = ";";
            String line;
            boolean inBlockComment = false;

            while ((line = reader.readLine()) != null) {
                bytesProcessed += line.getBytes(StandardCharsets.UTF_8).length + 1;
                String trimmedLine = line.trim();

                if (inBlockComment) {
                    int endIdx = trimmedLine.indexOf("*/");
                    if (endIdx >= 0) {
                        inBlockComment = false;
                        trimmedLine = trimmedLine.substring(endIdx + 2).trim();
                        if (trimmedLine.isEmpty()) continue;
                    } else {
                        continue;
                    }
                }

                if (trimmedLine.startsWith("/*") && !trimmedLine.contains("*/")) {
                    inBlockComment = true;
                    continue;
                }

                // Skip purely empty lines or comments
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("--") || trimmedLine.startsWith("#")) {
                    continue;
                }

                // Handle delimiter change
                if (trimmedLine.toLowerCase().startsWith("delimiter ")) {
                    String newDelim = trimmedLine.substring("delimiter ".length()).trim();
                    if (!newDelim.isEmpty()) {
                        currentDelim = newDelim;
                    }
                    continue;
                }

                // Strip inline comments for statement termination check
                String lineForStatement = line;
                int commentIdx = line.indexOf("--");
                if (commentIdx >= 0) {
                    lineForStatement = line.substring(0, commentIdx);
                }
                int hashIdx = lineForStatement.indexOf("#");
                if (hashIdx >= 0) {
                    lineForStatement = lineForStatement.substring(0, hashIdx);
                }

                statementBuilder.append(lineForStatement).append("\n");

                // Check if statement is complete
                String accumulated = statementBuilder.toString().trim();
                boolean isComplete = false;
                String cleanSql = accumulated;

                if (currentDelim.equals(";")) {
                    if (accumulated.endsWith(";")) {
                        isComplete = true;
                        cleanSql = accumulated.substring(0, accumulated.length() - 1).trim();
                    } else if (accumulated.toLowerCase().endsWith("\\g")) {
                        isComplete = true;
                        cleanSql = accumulated.substring(0, accumulated.length() - 2).trim();
                    }
                } else {
                    if (accumulated.endsWith(currentDelim)) {
                        isComplete = true;
                        cleanSql = accumulated.substring(0, accumulated.length() - currentDelim.length()).trim();
                    }
                }

                if (isComplete) {
                    if (!cleanSql.isEmpty()) {
                        String upperSql = cleanSql.toUpperCase();
                        if (useDbAfter != null && !useDbAfter.isEmpty()) {
                            if (upperSql.startsWith("CREATE DATABASE ")) {
                                cleanSql = "CREATE DATABASE IF NOT EXISTS `" + useDbAfter + "`";
                            } else if (upperSql.startsWith("USE ")) {
                                cleanSql = "USE `" + useDbAfter + "`";
                            }
                        }
                        QueryResult res = engine.execute(cleanSql);
                        if (!res.success) {
                            SqlLog.err("SQL Script Error on statement: " + cleanSql);
                            SqlLog.err("Message: " + res.message);
                        }
                        statementCounter++;

                        int percent = totalBytes > 0 ? (int) Math.min(100, ((float) bytesProcessed / totalBytes) * 100) : 50;

                        if (listener != null) {
                            listener.onStatementExecuted(useDbAfter, statementCounter, percent, cleanSql);
                        }

                        // Micro-yield CPU slice every batch interval to allow UI and user queries to run instantly
                        if (statementCounter % yieldInterval == 0) {
                            Thread.yield();
                        }
                    }
                    statementBuilder.setLength(0);
                }
            }

            // Execute any remaining statement
            String remaining = statementBuilder.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.endsWith(currentDelim)) {
                    remaining = remaining.substring(0, remaining.length() - currentDelim.length()).trim();
                }
                if (!remaining.isEmpty()) {
                    String upperSql = remaining.toUpperCase();
                    if (useDbAfter != null && !useDbAfter.isEmpty()) {
                        if (upperSql.startsWith("CREATE DATABASE ")) {
                            remaining = "CREATE DATABASE IF NOT EXISTS `" + useDbAfter + "`";
                        } else if (upperSql.startsWith("USE ")) {
                            remaining = "USE `" + useDbAfter + "`";
                        }
                    }
                    QueryResult res = engine.execute(remaining);
                    if (!res.success) {
                        SqlLog.err("SQL Script Error on remaining statement: " + remaining);
                        SqlLog.err("Message: " + res.message);
                    }
                    statementCounter++;
                    if (listener != null) {
                        listener.onStatementExecuted(useDbAfter, statementCounter, 100, remaining);
                    }
                }
            }

            // Switch to the specified database on completion
            if (useDbAfter != null && !useDbAfter.isEmpty()) {
                engine.clearTableCache(useDbAfter);
                engine.useDatabase(useDbAfter);
            }
        }
    }

    /**
     * Executes a SQL script and switches to the specified database on completion.
     */
    public static void runScript(DatabaseEngine engine, InputStream inputStream, String useDbAfter) throws Exception {
        runScript(engine, inputStream, useDbAfter, null);
    }

    /**
     * Executes a SQL script without switching database on completion.
     */
    public static void runScript(DatabaseEngine engine, InputStream inputStream) throws Exception {
        runScript(engine, inputStream, null, null);
    }
}
