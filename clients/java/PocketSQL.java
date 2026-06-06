import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class PocketSQL {

    static final String BASE_URL = "http://<mobile_ip>:<active_port>/api/query";
    static final String API_KEY = "<your_api_key>";
    static final String DATABASE = "<your_database_name>";

    static final HttpClient client = HttpClient.newHttpClient();
    static final Scanner scanner = new Scanner(System.in);

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Extract a JSON array content between [ ]
    static String extractRawArray(String json, String key) {
        String q = "\"" + key + "\":";
        int i = json.indexOf(q);
        if (i < 0) return null;
        i = json.indexOf('[', i + q.length());
        if (i < 0) return null;
        int depth = 1, end = i + 1;
        boolean inStr = false;
        while (end < json.length() && depth > 0) {
            char c = json.charAt(end);
            if (inStr) { if (c == '\\') end++; else if (c == '"') inStr = false; }
            else if (c == '"') inStr = true;
            else if (c == '[') depth++;
            else if (c == ']') depth--;
            end++;
        }
        return json.substring(i, end);
    }

    // Split a JSON array into top-level elements (objects or primitives)
    static List<String> splitArray(String raw) {
        List<String> items = new ArrayList<>();
        if (raw == null || raw.length() < 2) return items;
        raw = raw.substring(1, raw.length() - 1).trim();
        if (raw.isEmpty()) return items;
        int depth = 0, start = 0;
        boolean inStr = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inStr) { if (c == '\\') i++; else if (c == '"') inStr = false; }
            else if (c == '"') inStr = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                items.add(raw.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < raw.length()) items.add(raw.substring(start).trim());
        // Strip surrounding quotes from string items
        List<String> cleaned = new ArrayList<>();
        for (String item : items) {
            if (item.startsWith("\"") && item.endsWith("\"")) {
                item = item.substring(1, item.length() - 1);
            }
            cleaned.add(item);
        }
        return cleaned;
    }

    // Extract a string or number value for a key from a JSON object
    static String extractValue(String objJson, String key) {
        String q = "\"" + key + "\":";
        int i = objJson.indexOf(q);
        if (i < 0) return "";
        i += q.length();
        while (i < objJson.length() && objJson.charAt(i) == ' ') i++;
        if (i >= objJson.length()) return "";
        char first = objJson.charAt(i);
        if (first == '"') {
            // string value
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < objJson.length()) {
                char c = objJson.charAt(i);
                if (c == '\\') { sb.append(objJson.charAt(i + 1)); i += 2; }
                else if (c == '"') break;
                else { sb.append(c); i++; }
            }
            return sb.toString();
        } else if (first == 'n') {
            // null
            return "";
        } else {
            // number or boolean
            int end = i;
            while (end < objJson.length() && !",]}".contains(String.valueOf(objJson.charAt(end)))) end++;
            return objJson.substring(i, end).trim();
        }
    }

    // Extract a numeric value from JSON
    static String extractNum(String json, String key) {
        String q = "\"" + key + "\":";
        int i = json.indexOf(q);
        if (i < 0) return "0";
        i += q.length();
        while (i < json.length() && json.charAt(i) == ' ') i++;
        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return json.substring(i, end);
    }

    static void runSql(String sql) {
        sql = sql.trim();
        if (sql.isEmpty()) return;

        System.out.println("\nmysql> " + sql + "\n");

        try {
            String payload = "{\"sql\":\"" + escapeJson(sql) + "\",\"database\":\"" + DATABASE + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.out.println("ERROR: HTTP " + response.statusCode());
                System.out.println(body);
                return;
            }

            if (!body.contains("\"success\":true") && !body.contains("\"success\": true")) {
                String msg = extractValue(body, "message");
                if (msg.isEmpty()) msg = extractValue(body, "error");
                System.out.println("ERROR: " + (msg.isEmpty() ? body : msg));
                return;
            }

            String execTime = extractNum(body, "executionTimeMs");
            String colsRaw = extractRawArray(body, "columns");
            String rowsRaw = extractRawArray(body, "rows");

            List<String> columns = splitArray(colsRaw);
            List<String> rows = splitArray(rowsRaw);

            if (rows.isEmpty()) {
                System.out.println("Query OK");
                String affected = extractNum(body, "affectedRows");
                if (affected.equals("0")) {
                    affected = extractNum(body, "affected_rows");
                }
                if (!affected.equals("0")) System.out.println(affected + " rows affected");
                return;
            }

            // Detect row format: object {} or array []
            boolean rowsAreObjects = rows.get(0).startsWith("{");

            // Determine column widths
            int[] widths = new int[columns.size()];
            for (int c = 0; c < columns.size(); c++) {
                widths[c] = columns.get(c).length();
            }

            List<List<String>> cellData = new ArrayList<>();
            for (String rj : rows) {
                List<String> row = new ArrayList<>();
                if (rowsAreObjects) {
                    for (String col : columns) {
                        String val = extractValue(rj, col);
                        row.add(val);
                    }
                } else {
                    // array format — values are in order of columns
                    String inner = rj.trim();
                    if (inner.startsWith("[") && inner.endsWith("]")) {
                        List<String> vals = splitArray(inner);
                        for (int c = 0; c < columns.size(); c++) {
                            String v = c < vals.size() ? vals.get(c) : "";
                            // Remove surrounding quotes if present
                            if (v.startsWith("\"") && v.endsWith("\"")) {
                                v = v.substring(1, v.length() - 1);
                            }
                            row.add(v);
                        }
                    }
                }
                cellData.add(row);
                for (int c = 0; c < row.size() && c < widths.length; c++) {
                    if (row.get(c).length() > widths[c]) widths[c] = row.get(c).length();
                }
            }

            // Build separator line
            StringBuilder sep = new StringBuilder("+");
            for (int w : widths) {
                sep.append("-".repeat(w + 2)).append("+");
            }

            // Print header
            System.out.println(sep);
            System.out.print("|");
            for (int c = 0; c < columns.size(); c++) {
                System.out.printf(" %-" + widths[c] + "s |", columns.get(c));
            }
            System.out.println();
            System.out.println(sep);

            // Print rows
            for (List<String> row : cellData) {
                System.out.print("|");
                for (int c = 0; c < columns.size(); c++) {
                    String val = c < row.size() ? row.get(c) : "";
                    System.out.printf(" %-" + widths[c] + "s |", val);
                }
                System.out.println();
            }
            System.out.println(sep);

            double sec = Double.parseDouble(execTime) / 1000.0;
            System.out.printf("\n%d rows in set (%.2f sec)\n", rows.size(), sec);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(50));
        System.out.println("        PocketSQL Terminal (Java)");
        System.out.println("=".repeat(50));
        System.out.println("Type 'exit' to quit\n");

        while (true) {
            System.out.print("mysql> ");
            String sql = scanner.nextLine().trim();
            if (sql.equalsIgnoreCase("exit") || sql.equalsIgnoreCase("quit")) {
                System.out.println("\nBye");
                break;
            }
            runSql(sql);
        }
        scanner.close();
    }
}
