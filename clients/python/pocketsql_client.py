import requests
import json
from tabulate import tabulate

BASE_URL = "http://<mobile_ip>:<active_port>/api/query"
API_KEY = "<your_api_key>"
DATABASE = "ecommerce"

HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

def run_sql(sql):
    sql = sql.strip()
    if not sql:
        return

    print(f"\nmysql> {sql}\n")

    try:
        payload = {"sql": sql, "database": DATABASE}
        response = requests.post(BASE_URL, json=payload, headers=HEADERS, timeout=10)

        try:
            response.raise_for_status()
            data = response.json()
        except requests.exceptions.RequestException as e:
            print("ERROR:", str(e))
            return
        except json.JSONDecodeError:
            print("ERROR: Invalid JSON from server")
            if response.text:
                print(response.text)
            return

        if data.get("success"):
            columns = data.get("columns", [])
            rows = data.get("rows", [])
            exec_time = data.get("executionTimeMs", 0) / 1000.0

            if rows:
                if isinstance(rows[0], dict):
                    print(tabulate(rows, headers="keys", tablefmt="psql"))
                else:
                    print(tabulate(rows, headers=columns, tablefmt="psql"))
                print(f"\n{len(rows)} rows in set ({exec_time:.2f} sec)")
            else:
                print("Query OK")
                if "affected_rows" in data:
                    print(f"{data['affected_rows']} rows affected")
        else:
            print("ERROR:")
            print(data.get("error") or data.get("message") or json.dumps(data))

    except Exception as e:
        print("ERROR:", str(e))

print("=" * 50)
print("        PocketSQL Terminal (Python)")
print("=" * 50)
print("Type 'exit' to quit\n")

while True:
    sql = input("mysql> ")
    if sql.lower() in ["exit", "quit"]:
        print("\nBye")
        break
    run_sql(sql)
