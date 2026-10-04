package com.thaishopfun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.DriverManager;
import java.util.regex.Pattern;

public class DatabaseBootstrap implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern URL = Pattern.compile("^jdbc:postgresql://([^/:]+)(?::(\\d+))?/([^?]+)");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        var url = environment.getProperty("spring.datasource.url", "");
        if (url.isBlank() || url.contains("${")) url = System.getenv().getOrDefault("DB_URL", "");
        if (!url.startsWith("jdbc:postgresql:")) return;
        var matcher = URL.matcher(url);
        if (!matcher.find()) {
            log.error("อ่านที่อยู่ฐานข้อมูลไม่ได้: {}", url);
            return;
        }
        var database = matcher.group(3);
        if (!NAME.matcher(database).matches()) {
            log.error("ชื่อฐานข้อมูลไม่ปลอดภัย จึงไม่สร้างให้อัตโนมัติ: {}", database);
            return;
        }
        var port = matcher.group(2) == null ? "5432" : matcher.group(2);
        var adminUrl = "jdbc:postgresql://" + matcher.group(1) + ":" + port + "/postgres";
        var username = environment.getProperty("spring.datasource.username", "");
        var password = environment.getProperty("spring.datasource.password", "");
        if (username.isBlank() || username.contains("${")) username = System.getenv().getOrDefault("DB_USERNAME", "");
        if (password.isBlank()) password = System.getenv().getOrDefault("DB_PASSWORD", "");
        try (var connection = DriverManager.getConnection(adminUrl, username, password);
             var statement = connection.createStatement()) {
            boolean exists;
            try (var existing = statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + database + "'")) {
                exists = existing.next();
            }
            if (exists) return;
            statement.executeUpdate("CREATE DATABASE " + database);
            log.info("สร้างฐานข้อมูล {} แล้ว", database);
        } catch (Exception exception) {
            log.error("สร้างฐานข้อมูล {} ไม่ได้ ถ้ายังไม่มีฐานข้อมูลให้รัน backend/setup-postgres.ps1 แล้วสตาร์ตใหม่ ({})", database, exception.getMessage());
        }
    }
}
