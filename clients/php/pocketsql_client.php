<?php

define('BASE_URL', 'http://<mobile_ip>:<active_port>/api/query');
define('API_KEY', '<your_api_key>');
define('DATABASE', '<your_database_name>');

function run_sql($sql) {
    $sql = trim($sql);
    if ($sql === '') return;

    echo "\nmysql> $sql\n\n";

    $payload = json_encode([
        'sql' => $sql,
        'database' => DATABASE
    ]);

    $ch = curl_init(BASE_URL);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $payload,
        CURLOPT_HTTPHEADER => [
            'Authorization: Bearer ' . API_KEY,
            'Content-Type: application/json'
        ],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 10,
    ]);

    $body = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);

    if (curl_errno($ch)) {
        echo "ERROR: " . curl_error($ch) . "\n";
        curl_close($ch);
        return;
    }
    curl_close($ch);

    if ($httpCode < 200 || $httpCode >= 300) {
        echo "ERROR: HTTP $httpCode\n$body\n";
        return;
    }

    $data = json_decode($body, true);
    if (!$data) {
        echo "ERROR: Invalid JSON from server\n$body\n";
        return;
    }

    if (!($data['success'] ?? false)) {
        echo "ERROR: " . ($data['message'] ?? $data['error'] ?? $body) . "\n";
        return;
    }

    $columns = $data['columns'] ?? [];
    $rows = $data['rows'] ?? [];
    $execTime = ($data['executionTimeMs'] ?? 0) / 1000;

    if (empty($rows)) {
        echo "Query OK\n";
        if (isset($data['affectedRows']) || isset($data['affected_rows'])) {
            $aff = $data['affectedRows'] ?? $data['affected_rows'] ?? 0;
            echo "$aff rows affected\n";
        }
        return;
    }

    // Determine column widths
    $widths = array_map('strlen', $columns);
    $isDict = is_array($rows[0]) && !isset($rows[0][0]);

    $tableRows = [];
    foreach ($rows as $row) {
        $r = [];
        foreach ($columns as $col) {
            $val = $isDict ? ($row[$col] ?? '') : (string)array_shift($row);
            if (is_array($val) || is_object($val)) {
                $val = json_encode($val);
            }
            $r[] = (string)$val;
        }
        $tableRows[] = $r;
        foreach ($r as $i => $val) {
            if (mb_strlen($val) > $widths[$i]) {
                $widths[$i] = mb_strlen($val);
            }
        }
    }

    // Separator line
    $sep = '+' . implode('', array_map(fn($w) => str_repeat('-', $w + 2) . '+', $widths));

    // Header
    echo "$sep\n|";
    foreach ($columns as $i => $col) {
        printf(" %-{$widths[$i]}s |", $col);
    }
    echo "\n$sep\n";

    // Rows
    foreach ($tableRows as $row) {
        echo "|";
        foreach ($row as $i => $val) {
            printf(" %-{$widths[$i]}s |", $val);
        }
        echo "\n";
    }
    echo "$sep\n";

    printf("\n%d rows in set (%.2f sec)\n", count($rows), $execTime);
}

echo str_repeat('=', 50) . "\n";
echo "        PocketSQL Terminal (PHP)\n";
echo str_repeat('=', 50) . "\n";
echo "Type 'exit' to quit\n\n";

while (true) {
    echo "mysql> ";
    $line = fgets(STDIN);
    if ($line === false) break;
    $sql = rtrim($line, "\n\r");
    if (strtolower($sql) === 'exit' || strtolower($sql) === 'quit') {
        echo "\nBye\n";
        break;
    }
    run_sql($sql);
}
?>
