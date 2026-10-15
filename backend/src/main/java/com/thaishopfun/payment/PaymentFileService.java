package com.thaishopfun.payment;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.order.MediaFile;
import com.thaishopfun.order.MediaFileRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Slip / refund-proof files: same storage and validation as product images and merchant documents
 * (JPG / PNG / WEBP / PDF, ≤ 5 MB, content signature must match the type), recorded in media_files as PRIVATE.
 * A file written in a transaction that rolls back is deleted again.
 */
@Service
public class PaymentFileService {
    public static final String PAYMENT_SLIP = "PAYMENT_SLIP";
    public static final String REFUND_PROOF = "REFUND_PROOF";

    private final MerchantStorageService storage;
    private final MediaFileRepository media;

    public PaymentFileService(MerchantStorageService storage, MediaFileRepository media) {
        this.storage = storage;
        this.media = media;
    }

    public MediaFile store(UUID orderId, UUID shopId, UUID uploader, MultipartFile file, String purpose) {
        if (file == null || file.isEmpty()) throw new AuthException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "กรุณาแนบไฟล์สลิป (JPG, PNG, WEBP หรือ PDF)", "file");
        var stored = storage.store(orderId, file, purpose);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.path());
                }
            });
        }
        var name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? stored.storedName() : file.getOriginalFilename();
        if (name.length() > 255) name = name.substring(name.length() - 255);
        var row = new MediaFile(shopId, uploader, purpose, stored.path(), name, stored.contentType(), stored.size(), stored.sha256(), null);
        row.stamp(uploader);
        return media.save(row);
    }

    public ResponseEntity<Resource> serve(UUID mediaFileId) {
        var file = mediaFileId == null ? null : media.findById(mediaFileId).orElse(null);
        var path = file == null ? null : storage.openStoredFile(file.getStorageKey()).orElse(null);
        if (path == null) throw new AuthException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "ไม่พบไฟล์");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(path.getFileName().toString()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(new FileSystemResource(path));
    }
}
