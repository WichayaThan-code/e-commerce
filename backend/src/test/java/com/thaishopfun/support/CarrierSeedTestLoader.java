package com.thaishopfun.support;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Test profile only: H2 tables come from the entities (no Flyway), so the V30 carrier seed is read from the real
 * migration file and inserted once (skipped when carriers already has rows; several contexts share one H2 database).
 */
@Component
@Profile("test")
@Order(1)
public class CarrierSeedTestLoader implements ApplicationRunner {
    static final String RESOURCE = "/db/migration/V33__B2_shipping_carriers_and_tracking.sql";
    private static final Pattern INSERT = Pattern.compile("INSERT INTO carriers \\(([^)]*)\\) VALUES(.*?)ON CONFLICT", Pattern.DOTALL);

    private final JdbcTemplate jdbc;

    public CarrierSeedTestLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(jdbc);
    }

    public static synchronized void seed(JdbcTemplate jdbc) {
        var existing = jdbc.queryForObject("SELECT COUNT(*) FROM carriers", Integer.class);
        if (existing != null && existing > 0) return;
        String sql;
        try (InputStream in = CarrierSeedTestLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Migration not found on classpath: " + RESOURCE);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        var matcher = INSERT.matcher(sql);
        if (!matcher.find()) throw new IllegalStateException("No carriers seed in " + RESOURCE);
        var columns = matcher.group(1).trim();
        var rows = tuples(matcher.group(2));
        var now = Timestamp.from(Instant.now());
        var placeholders = String.join(", ", java.util.Collections.nCopies(columns.split(",").length + 2, "?"));
        var insert = "INSERT INTO carriers (" + columns + ", created_date_time, modified_date_time) VALUES (" + placeholders + ")";
        for (var row : rows) {
            var values = new ArrayList<Object>(row);
            values.add(now);
            values.add(now);
            jdbc.update(insert, values.toArray());
        }
    }

    /** Parses "(v, v, …), (…)" with 'quoted' strings ('' escapes), NULL, true / false and integers. */
    static List<List<Object>> tuples(String body) {
        var rows = new ArrayList<List<Object>>();
        List<Object> row = null;
        var token = new StringBuilder();
        boolean quoted = false, inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\'' && i + 1 < body.length() && body.charAt(i + 1) == '\'') { token.append('\''); i++; }
                else if (c == '\'') inString = false;
                else token.append(c);
                continue;
            }
            if (c == '\'') { inString = true; quoted = true; continue; }
            if (c == '(' && row == null) { row = new ArrayList<>(); token.setLength(0); quoted = false; continue; }
            if (row == null) continue;
            if (c == ',' || c == ')') {
                row.add(value(token.toString().trim(), quoted));
                token.setLength(0);
                quoted = false;
                if (c == ')') { rows.add(row); row = null; }
                continue;
            }
            token.append(c);
        }
        return rows;
    }

    private static Object value(String raw, boolean quoted) {
        if (quoted) return raw;
        if (raw.equalsIgnoreCase("NULL")) return null;
        if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
        return Integer.parseInt(raw);
    }
}
