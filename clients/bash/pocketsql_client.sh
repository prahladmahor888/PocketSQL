#!/bin/bash

BASE_URL="http://<mobile_ip>:<active_port>/api/query"
API_KEY="<your_api_key>"
DATABASE="ecommerce"

echo "=================================================="
echo "        PocketSQL Terminal (Bash/cURL)"
echo "=================================================="
echo "Type 'exit' or 'quit' to quit"
echo ""

run_sql() {
    local sql="$1"
    if [ -z "$sql" ]; then
        return
    fi

    echo -e "\nmysql> $sql\n"

    # Escape quotes for JSON payload
    local escaped_sql
    escaped_sql=$(echo "$sql" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')

    # Execute curl request
    local response
    response=$(curl -s -X POST "$BASE_URL" \
        -H "Authorization: Bearer $API_KEY" \
        -H "Content-Type: application/json" \
        -d "{\"sql\": \"$escaped_sql\", \"database\": \"$DATABASE\"}")

    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to connect to server"
        return
    fi

    # Check if jq is installed to print output beautifully
    if command -v jq &> /dev/null; then
        local success
        success=$(echo "$response" | jq -r '.success')
        if [ "$success" = "true" ]; then
            local cols
            cols=$(echo "$response" | jq -r '.columns | join(", ")')
            local rows_count
            rows_count=$(echo "$response" | jq '.rows | length')
            local exec_time
            exec_time=$(echo "$response" | jq -r '.executionTimeMs')
            local exec_sec
            exec_sec=$(echo "scale=3; $exec_time / 1000" | bc 2>/dev/null || echo "0")

            if [ "$rows_count" -gt 0 ]; then
                echo "Columns: $cols"
                echo "--------------------------------------------------"
                # Format rows nicely using jq
                echo "$response" | jq -r '.rows[] | to_entries | map("\(.key): \(.value)") | join(" | ")'
                echo "--------------------------------------------------"
                echo -e "\n$rows_count rows in set ($exec_sec sec)"
            else
                echo "Query OK"
                local affected
                affected=$(echo "$response" | jq -r '.affectedRows // .affected_rows // 0')
                if [ "$affected" -ne 0 ]; then
                    echo "$affected rows affected"
                fi
            fi
        else
            local err
            err=$(echo "$response" | jq -r '.error // .message')
            echo "ERROR: $err"
        fi
    else
        # fallback to raw response if jq is not installed
        echo "Response (raw):"
        echo "$response"
        echo -e "\n[Tip: Install 'jq' for formatted table output]"
    fi
}

while true; do
    read -p "mysql> " sql
    # Trim whitespace
    sql=$(echo "$sql" | xargs)
    if [[ "${sql,,}" == "exit" || "${sql,,}" == "quit" ]]; then
        echo -e "\nBye"
        break
    fi
    if [ -n "$sql" ]; then
        run_sql "$sql"
    fi
done
