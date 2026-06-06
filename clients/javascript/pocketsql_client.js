const BASE_URL = 'http://<mobile_ip>:<active_port>/api/query';
const API_KEY = '<your_api_key>';
const DATABASE = 'ecommerce';

const readline = require('readline');

function runSql(sql) {
  sql = sql.trim();
  if (!sql) return;

  console.log(`\nmysql> ${sql}\n`);

  const payload = JSON.stringify({ sql, database: DATABASE });

  fetch(BASE_URL, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json'
    },
    body: payload
  })
  .then(async res => {
    const body = await res.text();
    if (!res.ok) {
      console.log(`ERROR: HTTP ${res.status}\n${body}`);
      return;
    }

    let data;
    try { data = JSON.parse(body); } catch {
      console.log('ERROR: Invalid JSON from server\n' + body);
      return;
    }

    if (!data.success) {
      console.log('ERROR: ' + (data.message || data.error || body));
      return;
    }

    const columns = data.columns || [];
    const rows = data.rows || [];
    const execTime = (data.executionTimeMs || 0) / 1000;

    if (!rows.length) {
      console.log('Query OK');
      if (data.affected_rows) console.log(data.affected_rows + ' rows affected');
      return;
    }

    const isDict = typeof rows[0] === 'object' && !Array.isArray(rows[0]);
    let widths = columns.map(c => c.length);
    const tableRows = [];

    for (const row of rows) {
      const r = [];
      for (const col of columns) {
        r.push(isDict ? (row[col] ?? '') : String(row.shift ? row.shift() : ''));
      }
      tableRows.push(r);
      for (let i = 0; i < r.length; i++) {
        if (r[i].length > widths[i]) widths[i] = r[i].length;
      }
    }

    const sep = '+' + widths.map(w => '-'.repeat(w + 2)).join('+') + '+';

    console.log(sep);
    console.log('|' + columns.map((c, i) => ` ${c.padEnd(widths[i])} |`).join(''));
    console.log(sep);

    for (const row of tableRows) {
      console.log('|' + row.map((v, i) => ` ${v.padEnd(widths[i])} |`).join(''));
    }
    console.log(sep);
    console.log(`\n${rows.length} rows in set (${execTime.toFixed(2)} sec)`);
  })
  .catch(err => console.log('ERROR: ' + err.message));
}

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

console.log('='.repeat(50));
console.log('        PocketSQL Terminal (JS)');
console.log('='.repeat(50));
console.log("Type 'exit' to quit\n");

rl.setPrompt('mysql> ');
rl.prompt();

rl.on('line', line => {
  const sql = line.trim();
  if (sql.toLowerCase() === 'exit' || sql.toLowerCase() === 'quit') {
    console.log('\nBye');
    rl.close();
    return;
  }
  runSql(sql);
  setTimeout(() => rl.prompt(), 100);
});
