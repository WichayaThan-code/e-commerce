package com.thaishopfun.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thaishopfun.common.notification.NotificationProperties;
import com.thaishopfun.merchant.MerchantProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Phase 1 platform system status. Returns only short status fields — never host, credentials, JDBC URL, full paths, or stack traces.
 */
@Service
public class AdminSystemStatusService {
    public static final String OVERALL_UP = "UP";
    public static final String OVERALL_DEGRADED = "DEGRADED";
    public static final String OVERALL_DOWN = "DOWN";

    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final NotificationProperties notification;
    private final MerchantProperties merchant;
    private final ObjectProvider<Flyway> flyway;
    private final ObjectProvider<BuildProperties> buildProperties;

    public AdminSystemStatusService(
        JdbcTemplate jdbc,
        Environment environment,
        NotificationProperties notification,
        MerchantProperties merchant,
        ObjectProvider<Flyway> flyway,
        ObjectProvider<BuildProperties> buildProperties
    ) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.notification = notification;
        this.merchant = merchant;
        this.flyway = flyway;
        this.buildProperties = buildProperties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppInfo(
        String version,
        String buildTime,
        Instant startedAt,
        long uptimeSeconds,
        String javaVersion,
        String profile
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComponentStatus(
        String key,
        String status,
        Long latencyMs,
        String detail,
        Integer pending,
        Integer failed,
        Long freeBytes,
        Long totalBytes
    ) {
        public static ComponentStatus of(String key, String status, String detail) {
            return new ComponentStatus(key, status, null, detail, null, null, null, null);
        }
    }

    public record SystemStatus(String overall, Instant checkedAt, AppInfo app, List<ComponentStatus> components) {}

    public SystemStatus status() {
        var database = checkDatabase();
        var migrations = checkMigrations();
        var mail = checkMail();
        var storage = checkStorage();
        var components = List.of(database, migrations, mail, storage);
        return new SystemStatus(overall(components), Instant.now(), appInfo(), components);
    }

    private AppInfo appInfo() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = Math.max(0L, uptimeMs / 1000L);
        Instant startedAt = Instant.now().minusMillis(uptimeMs);
        String version = resolveVersion();
        String buildTime = null;
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null && build.getTime() != null) {
            buildTime = build.getTime().toString();
        }
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join(",", profiles);
        return new AppInfo(version, buildTime, startedAt, uptimeSeconds, System.getProperty("java.version", "unknown"), profile);
    }

    private String resolveVersion() {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null) {
            String version = build.getVersion();
            if (version != null && !version.isBlank() && !"unknown".equalsIgnoreCase(version)) return version.trim();
        }
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            String impl = pkg.getImplementationVersion();
            if (impl != null && !impl.isBlank()) return impl.trim();
        }
        return "dev";
    }

    private ComponentStatus checkDatabase() {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "system-status-db");
            thread.setDaemon(true);
            return thread;
        });
        long start = System.nanoTime();
        try {
            String detail = executor.submit((Callable<String>) () -> {
                try (Connection connection = jdbc.getDataSource().getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(2);
                    try (var ignored = statement.executeQuery("SELECT 1")) {
                        // probe only
                    }
                    String product = connection.getMetaData().getDatabaseProductName();
                    String version = connection.getMetaData().getDatabaseProductVersion();
                    return shortDbDetail(product, version);
                }
            }).get(2, TimeUnit.SECONDS);
            long latencyMs = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
            return new ComponentStatus("database", OVERALL_UP, latencyMs, detail, null, null, null, null);
        } catch (TimeoutException timeout) {
            return new ComponentStatus("database", OVERALL_DOWN, 2000L, "timeout", null, null, null, null);
        } catch (Exception exception) {
            long latencyMs = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
            return new ComponentStatus("database", OVERALL_DOWN, latencyMs, "unreachable", null, null, null, null);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Product + major.x only — never JDBC URL / host. */
    static String shortDbDetail(String product, String version) {
        String name = product == null || product.isBlank() ? "Database" : product.trim();
        if (version == null || version.isBlank()) return name;
        String major = version.trim().split("[^0-9]+")[0];
        if (major.isBlank()) return name;
        return name + " " + major + ".x";
    }

    private ComponentStatus checkMigrations() {
        Flyway bean = flyway.getIfAvailable();
        if (bean == null) {
            return new ComponentStatus("migrations", OVERALL_UP, null, "n/a", 0, 0, null, null);
        }
        try {
            var info = bean.info();
            var current = info.current();
            String detail = current == null || current.getVersion() == null
                ? "none"
                : "V" + current.getVersion();
            int pending = info.pending() == null ? 0 : info.pending().length;
            int failed = 0;
            if (info.all() != null) {
                failed = (int) Arrays.stream(info.all())
                    .filter(item -> item.getState() == MigrationState.FAILED)
                    .count();
            }
            String status = failed > 0 ? OVERALL_DOWN : OVERALL_UP;
            return new ComponentStatus("migrations", status, null, detail, pending, failed, null, null);
        } catch (Exception exception) {
            return new ComponentStatus("migrations", OVERALL_DOWN, null, "unavailable", 0, 0, null, null);
        }
    }

    private ComponentStatus checkMail() {
        boolean configured = notification.mail() != null && notification.mail().hasHost();
        if (!configured) {
            return ComponentStatus.of("mail", "UNKNOWN", "not configured");
        }
        return ComponentStatus.of("mail", OVERALL_UP, "configured");
    }

    private ComponentStatus checkStorage() {
        try {
            Path root = Path.of(merchant.storageRoot()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path probe = root.resolve(".status-write-" + UUID.randomUUID());
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            File file = root.toFile();
            long free = Math.max(0L, file.getUsableSpace());
            long total = Math.max(0L, file.getTotalSpace());
            return new ComponentStatus("storage", OVERALL_UP, null, "writable", null, null, free, total);
        } catch (Exception exception) {
            return new ComponentStatus("storage", OVERALL_DOWN, null, "not writable", null, null, null, null);
        }
    }

    public static String overall(List<ComponentStatus> components) {
        boolean dbDown = components.stream().anyMatch(c -> "database".equals(c.key()) && OVERALL_DOWN.equals(c.status()));
        if (dbDown) return OVERALL_DOWN;
        boolean otherDown = components.stream()
            .anyMatch(c -> !"database".equals(c.key()) && OVERALL_DOWN.equals(c.status()));
        if (otherDown) return OVERALL_DEGRADED;
        return OVERALL_UP;
    }
}
