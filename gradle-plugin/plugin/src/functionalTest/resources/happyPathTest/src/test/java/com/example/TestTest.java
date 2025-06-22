package com.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTest {
    @Test
    public void test() throws IOException, SQLException {
        var resource = getClass().getResourceAsStream("/application-test.properties");
        assertNotNull(resource);
        var p = new Properties();
        p.load(resource);
        var jdbcUrl = p.getProperty("datasources.default.url");
        assertNotNull(jdbcUrl);
        Set<String> codes = new HashSet<>();
        try (var connection = DriverManager.getConnection(jdbcUrl);
             var statement = connection.prepareStatement("SELECT code FROM country");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                codes.add(resultSet.getString(1));
            }
        }
        assertEquals(2, codes.size());
        assertTrue(codes.contains("USA"));
    }
}
