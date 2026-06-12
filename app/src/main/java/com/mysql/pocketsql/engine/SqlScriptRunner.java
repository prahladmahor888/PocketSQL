package com.mysql.pocketsql.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SqlScriptRunner {

    /**
     * Executes a SQL script from an input stream statement-by-statement.
     * Handles dynamic delimiters (e.g. DELIMITER $$).
     *
     * @param engine      The DatabaseEngine instance.
     * @param inputStream The input stream containing the SQL script.
     * @throws Exception if an error occurs during execution.
     */
    /**
     * Executes a SQL script and switches to the specified database on completion.
     */
    public static void runScript(DatabaseEngine engine, InputStream inputStream, String useDbAfter) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder statementBuilder = new StringBuilder();
            String currentDelim = ";";
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

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

                // Append line
                statementBuilder.append(line).append("\n");

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
                        QueryResult res = engine.execute(cleanSql);
                        if (!res.success) {
                            SqlLog.err("SQL Script Error on statement");
                            SqlLog.err("Message: " + res.message);
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
                    QueryResult res = engine.execute(remaining);
                    if (!res.success) {
                        SqlLog.err("SQL Script Error on remaining statement");
                        SqlLog.err("Message: " + res.message);
                    }
                }
            }

            // Switch to the specified database on completion
            if (useDbAfter != null && !useDbAfter.isEmpty()) {
                engine.useDatabase(useDbAfter);
            }
        }
    }

    /**
     * Executes a SQL script without switching database on completion.
     */
    public static void runScript(DatabaseEngine engine, InputStream inputStream) throws Exception {
        runScript(engine, inputStream, null);
    }
}
