package com.mysql.pocketsql;

import org.junit.Test;
import static org.junit.Assert.*;
import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;
import java.util.Map;

public class TestWindowFunctions {
    @Test
    public void testPercentRankAndNtile() throws Exception {
        java.io.File testDir = new java.io.File("build/test-pocketsql-percent-rank");
        testDir.mkdirs();
        DatabaseEngine engine = new DatabaseEngine(testDir);
        engine.initializeDefaultRootUser();
        engine.setCurrentUser("root", "localhost");
        engine.execute("CREATE DATABASE testdb;");
        engine.execute("USE testdb;");
        engine.execute("CREATE TABLE Orders (customer_id INT, total_amount DOUBLE);");
        engine.execute("INSERT INTO Orders VALUES (1, 100.0), (2, 200.0), (3, 300.0), (4, 400.0);");
        
        QueryResult r = engine.execute("SELECT customer_id, PERCENT_RANK() OVER (ORDER BY total_amount) AS pct_rank, NTILE(2) OVER (ORDER BY total_amount) AS decile FROM Orders;");
        System.out.println(r.message);
        assertTrue(r.success);
        
        assertEquals(4, r.rows.size());
        
        Map<String, Object> r1 = r.rows.get(0);
        assertEquals(1L, ((Number)r1.get("customer_id")).longValue());
        assertEquals(0.0, ((Number)r1.get("pct_rank")).doubleValue(), 0.001);
        assertEquals(1L, ((Number)r1.get("decile")).longValue());
        
        Map<String, Object> r2 = r.rows.get(1);
        assertEquals(2L, ((Number)r2.get("customer_id")).longValue());
        assertEquals(0.333, ((Number)r2.get("pct_rank")).doubleValue(), 0.01);
        assertEquals(1L, ((Number)r2.get("decile")).longValue());
        
        Map<String, Object> r3 = r.rows.get(2);
        assertEquals(3L, ((Number)r3.get("customer_id")).longValue());
        assertEquals(0.666, ((Number)r3.get("pct_rank")).doubleValue(), 0.01);
        assertEquals(2L, ((Number)r3.get("decile")).longValue());
        
        Map<String, Object> r4 = r.rows.get(3);
        assertEquals(4L, ((Number)r4.get("customer_id")).longValue());
        assertEquals(1.0, ((Number)r4.get("pct_rank")).doubleValue(), 0.001);
        assertEquals(2L, ((Number)r4.get("decile")).longValue());
    }
}
