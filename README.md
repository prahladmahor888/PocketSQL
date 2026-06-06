# 📱 PocketSQL — Embedded SQL Database Engine & API Server for Android

<p align="center">
  <img src="https://img.shields.io/badge/PocketSQL-Database%20Engine-007ACC?style=for-the-badge&logo=mysql&logoColor=white" alt="PocketSQL Engine" />
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/API_Server-Active-brightgreen?style=for-the-badge&logo=serverless&logoColor=white" alt="API Server" />
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
</p>

PocketSQL is a high-performance Android application running a **custom-built SQL Database Engine** locally on your mobile device. It features an interactive mobile terminal emulator with autocomplete, templates, and built-in help. Additionally, it launches an embedded **HTTP API Server** as a persistent background service, allowing external applications (web app, desktop app, or script) to connect securely and execute database queries remotely.

---

## 🗺️ Table of Contents
- [⚙️ Architecture & How It Works](#%EF%B8%8F-architecture--how-it-works)
- [✨ Core Features](#-core-features)
- [📡 Embedded HTTP API Server & Background Service](#-embedded-http-api-server--background-service)
  - [Port Fallback Scan (`8080` - `8099`)](#port-fallback-scan-8080---8099)
  - [Remote Network Connections](#remote-network-connections)
  - [Session Termination & Shutdown](#session-termination--shutdown)
- [🚀 Quick-Start Copy-Paste Clients](#-quick-start-copy-paste-clients)
  - [Interactive Shell Terminals (Recommended)](#interactive-shell-terminals-recommended)
  - [Quick Integration Snippets](#quick-integration-snippets)
- [📋 HTTP API Reference](#-http-api-reference)
- [🛠️ Build & Installation Guide](#%EF%B8%8F-build--installation-guide)

---

## ⚙️ Architecture & How It Works

PocketSQL operates by combining a custom SQL parsing pipeline, a JSON-based schema/row storage engine, and an embedded HTTP service:

1. **SQL Parsing & Lexing**: Queries entered via the console or HTTP server are tokenized by [SqlScanner.java](file:///app/src/main/java/com/mysql/pocketsql/engine/SqlScanner.java) and parsed by [SqlParser.java](file:///app/src/main/java/com/mysql/pocketsql/engine/SqlParser.java) into execution commands.
2. **Storage Engine**: Database schemas (`schema.json`) and rows (`table_name.pqsql`) are serialized as readable, organized JSON structures under the `databases/` subdirectory.
3. **Privilege & Authentication Manager**: Manages administrator (`root`) and custom user accounts, passwords, and fine-grained query privileges (`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP`, `ALTER`) in `users.json`.
4. **Embedded API Server**: Hosts a local HTTP service on port `8080`. External applications can connect to it securely using bearer tokens (API Keys) to read and write database records.

---

## ✨ Core Features

### 1. Advanced SQL Database Engine
* **Complete CRUD**: Supports standard queries (`SELECT`, `INSERT`, `UPDATE`, `DELETE`).
* **DDL Commands**: Create and drop databases and tables (`CREATE DATABASE`, `DROP DATABASE`, `CREATE TABLE`, `DROP TABLE`).
* **Table Alteration**: Modify column schemas (`ADD COLUMN`, `MODIFY COLUMN`, `CHANGE COLUMN`, `RENAME COLUMN`, `DROP COLUMN`).
* **Transaction Control**: Complete transaction commands with rollback boundaries:
  - `START TRANSACTION;`
  - `COMMIT;`
  - `ROLLBACK;`
  - `SAVEPOINT <name>;`
  - `ROLLBACK TO <name>;`
* **Column Constraints**: Supports `PRIMARY KEY`, `UNIQUE`, `NOT NULL`, `CHECK`, and `FOREIGN KEY` verification.
* **Auto-Updating Columns**: Supports `ON UPDATE CURRENT_TIMESTAMP` to track record updates automatically.
* **Data Validations**: Numeric fields support `UNSIGNED` attributes to reject negative integers.

### 2. High-Performance Terminal Console
* **Interactive CLI Interface**: Includes dynamic prompt prefixes (e.g. `mysql [ecommerce]> `) that change according to your active database.
* **Premium Typography**: Rendered in custom **JetBrains Mono** font with professional spacing.
* **Smart Autocompletion**: Suggests keywords, functions, datatypes, tables, column attributes, and even table aliases dynamically as you type.
* **SQL Templates**: A dropdown toolbar offering quick-access boilerplate code for database administration, alter tables, transaction boundaries, and built-in SQL functions.
* **Output Select & Copy**: A dialog to highlight and select terminal history lines natively, supporting Copy All and custom range copying.
* **Interactive Help System**: Executing `HELP;` returns a table listing support indexes, and `HELP <keyword>;` describes keyword syntax with usage examples.

---

## 📡 Embedded HTTP API Server & Background Service

PocketSQL exposes a secure HTTP API endpoint to act as a database backend for external applications. It runs as an Android **Foreground Service**, meaning the server remains active in the background even if the main application UI is closed, swiped away, or the device is locked.

### Port Fallback Scan (`8080` - `8099`)
* By default, the server attempts to bind to port **`8080`**.
* If port `8080` is already in use by another app on the Android device, the server will scan ports sequentially up to **`8099`** and bind to the first available port.
* The active port is dynamically displayed in the **🔑 API Keys** dialog and in the background notification.

### Remote Network Connections
* **Local Access**: For client apps running on the same device (e.g., local webviews or apps):
  `http://localhost:<active_port>/api/query`
* **Remote Access (Other Systems)**: For client apps running on another computer, server, or mobile device on the same local network:
  `http://<mobile_ip>:<active_port>/api/query` (e.g., `http://192.168.1.15:8080/api/query`).
* The server includes **CORS headers** enabling direct AJAX connections from web browsers (React, Vue, HTML/JS) from any system.

### Session Termination & Shutdown
To terminate the background session and stop the API server:
* **Option 1**: Pull down your Android notification drawer and click the **Stop Server** button.
* **Option 2**: Open the app, tap the **🔑 API Keys** button, and click **Stop Background Server**.
* **Option 3**: In the app's terminal, type `exit`, `quit`, or `\q` to close the session and stop the service automatically.

---

## 🚀 Quick-Start Copy-Paste Clients

For instant testing and query execution, we provide client scripts in multiple languages. They are pre-designed to provide a interactive shell console, identical to querying directly from the local device terminal.

### Interactive Shell Terminals (Recommended)

Run the interactive shell in your preferred language directly from the directory:

| Language | Client File Link | Command to Run | Dependencies |
| :--- | :--- | :--- | :--- |
| **Python** | [pocketsql_client.py](file:///clients/python/pocketsql_client.py) | `python pocketsql_client.py` | `requests`, `tabulate` |
| **Node.js** | [pocketsql_client.js](file:///clients/javascript/pocketsql_client.js) | `node pocketsql_client.js` | None (Uses native `fetch`) |
| **Java** | [PocketSQL.java](file:///clients/java/PocketSQL.java) | `javac PocketSQL.java && java PocketSQL` | None (Uses Java 11 `HttpClient`) |
| **PHP** | [pocketsql_client.php](file:///clients/php/pocketsql_client.php) | `php pocketsql_client.php` | `php-curl` extension |
| **Bash** | [pocketsql_client.sh](file:///clients/bash/pocketsql_client.sh) | `bash pocketsql_client.sh` | `curl`, `jq` (optional) |

> [!TIP]
> Open the client file in your preferred language, configure the `BASE_URL` with your device's IP and port, paste your `API_KEY`, and you are ready to query!

---

### Quick Integration Snippets

If you just need a quick copy-paste snippet to run queries programmatically, use the templates below:

#### 1. curl (Command Line / Bash)
```bash
curl -X POST http://<mobile_ip>:<active_port>/api/query \
  -H "Authorization: Bearer <your_api_key>" \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM products WHERE price > 100;",
    "database": "ecommerce"
  }'
```

#### 2. Python
```python
import requests

url = "http://<mobile_ip>:<active_port>/api/query"
headers = {
    "Authorization": "Bearer <your_api_key>",
    "Content-Type": "application/json"
}
payload = {
    "sql": "SELECT * FROM products ORDER BY id DESC LIMIT 5;",
    "database": "ecommerce"
}

response = requests.post(url, json=payload, headers=headers)
print(response.json())
```

#### 3. JavaScript / Node.js
```javascript
const url = "http://<mobile_ip>:<active_port>/api/query";
const payload = {
  sql: "SELECT * FROM products ORDER BY id DESC LIMIT 5;",
  database: "ecommerce"
};

fetch(url, {
  method: "POST",
  headers: {
    "Authorization": "Bearer <your_api_key>",
    "Content-Type": "application/json"
  },
  body: JSON.stringify(payload)
})
.then(res => res.json())
.then(data => console.log(data))
.catch(err => console.error("Error:", err));
```

#### 4. Java (Java 11+)
```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PocketSQLQuickStart {
    public static void main(String[] args) throws Exception {
        String url = "http://<mobile_ip>:<active_port>/api/query";
        String apiKey = "<your_api_key>";
        String payload = "{\"sql\":\"SELECT * FROM products;\",\"database\":\"ecommerce\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Response: " + response.body());
    }
}
```

#### 5. PHP
```php
<?php
$url = "http://<mobile_ip>:<active_port>/api/query";
$apiKey = "<your_api_key>";
$payload = json_encode([
    "sql" => "SELECT * FROM products LIMIT 5;",
    "database" => "ecommerce"
]);

$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $payload,
    CURLOPT_HTTPHEADER => [
        "Authorization: Bearer " . $apiKey,
        "Content-Type: application/json"
    ],
    CURLOPT_RETURNTRANSFER => true
]);

$response = curl_exec($ch);
curl_close($ch);
echo "Response: " . $response;
?>
```

#### 6. Go (Golang)
```go
package main

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
)

func main() {
	url := "http://<mobile_ip>:<active_port>/api/query"
	apiKey := "<your_api_key>"
	jsonPayload := []byte(`{"sql": "SELECT * FROM products;", "database": "ecommerce"}`)

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonPayload))
	if err != nil {
		panic(err)
	}

	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	fmt.Println("Response:", string(body))
}
```

---

## 📋 HTTP API Reference

All requests to the SQL engine's REST API must be authenticated using the Authorization header.

### Request Configuration
* **Method**: `POST`
* **Path**: `/api/query`
* **Headers**:
  * `Authorization: Bearer <your_api_key>`
  * `Content-Type: application/json`

### JSON Request Payload
```json
{
  "sql": "SELECT * FROM users WHERE active = 1;",
  "database": "ecommerce"
}
```
* `sql` (String, Required): The SQL query or command statement.
* `database` (String, Optional): The target database context. Falls back to default context if not specified.

### JSON Response Payload (Success)
```json
{
  "success": true,
  "message": "3 rows in set",
  "affectedRows": 0,
  "executionTimeMs": 14,
  "columns": ["id", "username", "email"],
  "columnTypes": ["INT", "VARCHAR", "VARCHAR"],
  "rows": [
    { "id": 1, "username": "root", "email": "root@localhost" },
    { "id": 2, "username": "db_user", "email": "user@example.com" }
  ]
}
```

---

## 🛠️ Build & Installation Guide

To compile and package the Android application from source code:

1. **Prerequisites**: Android SDK & JDK 17.
2. **Build debug APK**:
   ```powershell
   ./gradlew assembleDebug
   ```
3. **Run unit tests**:
   ```powershell
   ./gradlew test
   ```
4. **Deploy to connected Android device**:
   ```powershell
   ./gradlew installDebug
   ```
