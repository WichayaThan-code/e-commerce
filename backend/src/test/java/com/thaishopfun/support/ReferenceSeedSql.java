package com.thaishopfun.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the authz catalog out of the real reference seed file
 * ({@code src/main/resources/db/seed/seed_reference_data.sql}) so tests use exactly the roles, permissions and
 * role-permission mappings that are seeded into PostgreSQL, without a hand-written copy.
 * <p>The seed is PostgreSQL-only (ON CONFLICT ... DO UPDATE, DO blocks, data-modifying CTE), so H2 cannot run it
 * as is; instead the VALUES lists of the three catalog statements are parsed and inserted with plain JDBC.
 */
public final class ReferenceSeedSql {
    public static final String RESOURCE = "/db/seed/seed_reference_data.sql";

    private static final Pattern ROLES = Pattern.compile("INSERT INTO auth_roles \\(([^)]*)\\) VALUES(.*?)ON CONFLICT", Pattern.DOTALL);
    private static final Pattern PERMISSIONS = Pattern.compile("INSERT INTO auth_permissions \\(([^)]*)\\) VALUES(.*?)ON CONFLICT", Pattern.DOTALL);
    private static final Pattern MAPPINGS = Pattern.compile("WITH v\\(role_code, perm_code\\) AS \\(VALUES(.*?)\\), del AS", Pattern.DOTALL);

    public record Table(List<String> columns, List<List<Object>> rows) {}

    public record Catalog(Table roles, Table permissions, List<List<Object>> mappings) {}

    private ReferenceSeedSql() {}

    public static Catalog load() {
        String sql;
        try (InputStream in = ReferenceSeedSql.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Seed file not found on classpath: " + RESOURCE);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return parse(sql);
    }

    static Catalog parse(String sql) {
        var roles = table(sql, ROLES, "auth_roles");
        var permissions = table(sql, PERMISSIONS, "auth_permissions");
        var mapping = MAPPINGS.matcher(sql);
        if (!mapping.find()) throw new IllegalStateException("auth_role_permissions VALUES list not found in " + RESOURCE);
        return new Catalog(roles, permissions, tuples(mapping.group(1)));
    }

    private static Table table(String sql, Pattern pattern, String name) {
        var matcher = pattern.matcher(sql);
        if (!matcher.find()) throw new IllegalStateException(name + " INSERT not found in " + RESOURCE);
        var columns = Arrays.stream(matcher.group(1).split(",")).map(String::trim).toList();
        var rows = tuples(matcher.group(2));
        for (var row : rows) {
            if (row.size() != columns.size()) throw new IllegalStateException(name + ": row " + row + " does not match columns " + columns);
        }
        return new Table(columns, rows);
    }

    /** Parses "(...), (...)" with SQL literals: 'text' (with '' escapes), true/false, integers, NULL. */
    static List<List<Object>> tuples(String text) {
        var rows = new ArrayList<List<Object>>();
        var i = 0;
        var n = text.length();
        while (i < n) {
            var c = text.charAt(i);
            if (c != '(') { i++; continue; }
            i++;
            var row = new ArrayList<Object>();
            while (true) {
                while (i < n && Character.isWhitespace(text.charAt(i))) i++;
                if (i >= n) throw new IllegalStateException("Unterminated tuple in seed VALUES");
                if (text.charAt(i) == '\'') {
                    var value = new StringBuilder();
                    i++;
                    while (true) {
                        if (i >= n) throw new IllegalStateException("Unterminated string in seed VALUES");
                        var ch = text.charAt(i);
                        if (ch == '\'') {
                            if (i + 1 < n && text.charAt(i + 1) == '\'') { value.append('\''); i += 2; continue; }
                            i++;
                            break;
                        }
                        value.append(ch);
                        i++;
                    }
                    row.add(value.toString());
                } else {
                    var start = i;
                    while (i < n && text.charAt(i) != ',' && text.charAt(i) != ')') i++;
                    var token = text.substring(start, i).trim();
                    row.add(literal(token));
                }
                while (i < n && Character.isWhitespace(text.charAt(i))) i++;
                if (i >= n) throw new IllegalStateException("Unterminated tuple in seed VALUES");
                if (text.charAt(i) == ',') { i++; continue; }
                if (text.charAt(i) == ')') { i++; break; }
                throw new IllegalStateException("Unexpected character '" + text.charAt(i) + "' in seed VALUES");
            }
            rows.add(row);
        }
        return rows;
    }

    private static Object literal(String token) {
        if (token.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (token.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (token.equalsIgnoreCase("null")) return null;
        if (token.matches("-?\\d+")) return Integer.valueOf(token);
        throw new IllegalStateException("Unsupported literal in seed VALUES: " + token);
    }
}
