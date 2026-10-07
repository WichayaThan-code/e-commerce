package com.thaishopfun.catalog;

import java.util.UUID;

public class ImportRunningException extends RuntimeException {
    private final UUID jobId;

    public ImportRunningException(UUID jobId) {
        super("มีการนำเข้าสินค้าที่ยังไม่เสร็จ");
        this.jobId = jobId;
    }

    public UUID jobId() {
        return jobId;
    }
}
