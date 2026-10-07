package com.thaishopfun.catalog;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.ProductCategoryRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ProductImportJobService {
    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, UUID> activeByShop = new ConcurrentHashMap<>();
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    private final ProductService products;
    private final ProductCategoryRepository categories;
    private volatile CountDownLatch testStarted;
    private volatile CountDownLatch testRelease;

    public ProductImportJobService(ProductService products, ProductCategoryRepository categories) {
        this.products = products;
        this.categories = categories;
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("product-import-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(2);
        executor.initialize();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public void armForTest(CountDownLatch started, CountDownLatch release) {
        this.testStarted = started;
        this.testRelease = release;
    }

    public CatalogDtos.ProductImportAccepted submit(UUID userId, Long shopId, InputStream input) {
        products.requireImportAccess(userId, shopId);
        List<ProductSpreadsheet.SheetRow> rows;
        try {
            rows = ProductSpreadsheet.read(input);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID", exception.getMessage());
        }
        if (rows.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "IMPORT_EMPTY", "ไม่พบรายการสินค้าในไฟล์");
        }
        var job = new Job(UUID.randomUUID(), shopId, userId, rows.size());
        synchronized (activeByShop) {
            var current = activeByShop.get(shopId);
            if (current != null) {
                var existing = jobs.get(current);
                if (existing != null && existing.isOpen()) throw new ImportRunningException(current);
            }
            jobs.put(job.jobId, job);
            activeByShop.put(shopId, job.jobId);
            try {
                executor.execute(() -> run(job, List.copyOf(rows)));
            } catch (RejectedExecutionException exception) {
                job.fail();
                activeByShop.remove(shopId, job.jobId);
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_BUSY", "ระบบนำเข้าไม่ว่าง ลองใหม่ภายหลัง");
            }
        }
        return new CatalogDtos.ProductImportAccepted(job.jobId, rows.size());
    }

    public CatalogDtos.ProductImportJobView status(UUID userId, Long shopId, UUID jobId) {
        products.requireImportAccess(userId, shopId);
        var job = jobs.get(jobId);
        if (job == null || !job.shopId.equals(shopId) || !job.userId.equals(userId)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "ไม่พบงานนำเข้า");
        }
        return job.view();
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeFinished() {
        var cutoff = Instant.now().minus(Duration.ofMinutes(30));
        jobs.entrySet().removeIf(entry -> {
            var job = entry.getValue();
            var finishedAt = job.finishedAt();
            if (finishedAt == null || finishedAt.isAfter(cutoff) || job.isOpen()) return false;
            activeByShop.remove(job.shopId, job.jobId);
            return true;
        });
    }

    private void run(Job job, List<ProductSpreadsheet.SheetRow> rows) {
        job.markRunning();
        var started = testStarted;
        if (started != null) started.countDown();
        var release = testRelease;
        if (release != null) {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                job.fail();
                activeByShop.remove(job.shopId, job.jobId);
                return;
            }
        }
        try {
            var catalog = categories.findByActiveTrueOrderBySortOrderAsc();
            for (var row : rows) {
                job.current(row.row());
                var outcome = ProductImportService.importRow(products, job.userId, job.shopId, row, catalog);
                job.record(outcome);
            }
            job.complete();
        } catch (RuntimeException exception) {
            job.fail();
        } finally {
            if (job.isOpen()) job.fail();
            activeByShop.remove(job.shopId, job.jobId);
        }
    }

    private static final class Job {
        private final UUID jobId;
        private final Long shopId;
        private final UUID userId;
        private final int total;
        private String status = "QUEUED";
        private int processed;
        private int created;
        private int failed;
        private final List<CatalogDtos.ProductImportError> errors = new ArrayList<>();
        private Integer currentRow;
        private Instant startedAt;
        private Instant finishedAt;

        private Job(UUID jobId, Long shopId, UUID userId, int total) {
            this.jobId = jobId;
            this.shopId = shopId;
            this.userId = userId;
            this.total = total;
        }

        private boolean isOpen() {
            synchronized (this) {
                return "QUEUED".equals(status) || "RUNNING".equals(status);
            }
        }

        private void markRunning() {
            synchronized (this) {
                status = "RUNNING";
                startedAt = Instant.now();
            }
        }

        private void current(int row) {
            synchronized (this) {
                currentRow = row;
            }
        }

        private void record(ProductImportService.RowOutcome outcome) {
            synchronized (this) {
                if (outcome.created()) created++;
                if (outcome.error() != null) errors.add(outcome.error());
                failed = errors.size();
                processed++;
            }
        }

        private void complete() {
            synchronized (this) {
                status = "COMPLETED";
                finishedAt = Instant.now();
            }
        }

        private void fail() {
            synchronized (this) {
                status = "FAILED";
                if (finishedAt == null) finishedAt = Instant.now();
            }
        }

        private Instant finishedAt() {
            synchronized (this) {
                return finishedAt;
            }
        }

        private CatalogDtos.ProductImportJobView view() {
            synchronized (this) {
                return new CatalogDtos.ProductImportJobView(
                    jobId, shopId, userId, status, total, processed, created, failed, List.copyOf(errors), currentRow, startedAt, finishedAt
                );
            }
        }
    }
}
