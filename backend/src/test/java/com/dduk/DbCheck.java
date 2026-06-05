package com.dduk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheck {
    @Test
    public void checkDbCount() {
        System.out.println("==================================================");
        System.out.println("=== [DEBUG] START ACTUAL DB COUNT CHECK ===");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver not found: " + e.getMessage());
        }

        String dbUrl = requiredEnv("DB_URL");
        String dbUsername = requiredEnv("DB_USERNAME");
        String dbPassword = requiredEnv("DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            
            String[] tables = {"journal_entries", "journal_items", "accounts", "vouchers", "voucher_lines"};
            for (String table : tables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (rs.next()) {
                        System.out.println("[DB_COUNT] " + table + " : " + rs.getInt(1));
                    }
                } catch (Exception ex) {
                    System.out.println("[DB_COUNT] " + table + " failed: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("DB Connection failed: " + e.getMessage());
        }
        System.out.println("=== [DEBUG] END ACTUAL DB COUNT CHECK ===");
        System.out.println("==================================================");
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required for external DB check");
        return value;
    }
}
