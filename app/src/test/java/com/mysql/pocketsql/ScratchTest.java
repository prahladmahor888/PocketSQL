package com.mysql.pocketsql;

import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;
import org.junit.Test;
import java.io.File;
import java.util.Map;

public class ScratchTest {
    @Test
    public void testMain() {
        DatabaseEngine engine = new DatabaseEngine(new File(System.getProperty("java.io.tmpdir"), "pocketsql_scratch_db"));
        engine.execute("DROP DATABASE IF EXISTS test_window_arithmetic_db;");
        engine.execute("CREATE DATABASE test_window_arithmetic_db;");
        engine.execute("USE test_window_arithmetic_db;");
        engine.execute("CREATE TABLE Orders (order_date DATE, total_amount DOUBLE);");
        
        engine.execute("INSERT INTO Orders VALUES " +
                "('2022-05-01', 50.0), " +
                "('2022-08-15', 100.0), " + 
                "('2023-01-10', 2000.0), " +
                "('2023-06-20', 4200.0);");

        QueryResult r = engine.execute("SELECT " +
                "    YEAR(order_date) AS year, " +
                "    SUM(total_amount) AS revenue, " +
                "    SUM(total_amount) - LAG(SUM(total_amount)) OVER (ORDER BY YEAR(order_date)) AS yoy_growth " +
                "FROM Orders " +
                "GROUP BY YEAR(order_date);");

        if (!r.success) {
            System.out.println("ERROR: " + r.message);
        } else {
            System.out.println("SUCCESS! ROWS: " + r.rows.size());
            for (Map<String, Object> row : r.rows) {
                System.out.println(row);
            }
        }
    }
}
