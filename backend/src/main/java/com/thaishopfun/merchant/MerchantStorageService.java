package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class MerchantStorageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String,String> EXTENSIONS = Map.of(
        "image/jpeg", ".jpg", "image/png", ".png", "application/pdf", ".pdf");
    private final Path root;

    public MerchantStorageService(MerchantProperties properties) {
        this.root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
    }

    public StoredFile store(UUID applicationId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("กรุณาเลือกไฟล์");
        if (file.getSize() > MAX_BYTES) throw bad("ไฟล์ต้องมีขนาดไม่เกิน 5 MB");
        var extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) throw bad("รองรับเฉพาะไฟล์ JPG, PNG หรือ PDF");
        if (!hasValidSignature(file, file.getContentType())) throw bad("เนื้อหาไฟล์ไม่ตรงกับชนิดไฟล์ที่ระบุ");
        var original = Path.of(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()).getFileName().toString();
        var stored = UUID.randomUUID() + extension;
        var directory = root.resolve(applicationId.toString()).normalize();
        var target = directory.resolve(stored).normalize();
        if (!target.startsWith(root)) throw bad("ตำแหน่งจัดเก็บไฟล์ไม่ถูกต้อง");
        try {
            Files.createDirectories(directory);
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(original, stored, target.toString(), file.getContentType(), file.getSize(), HexFormat.of().formatHex(digest.digest()));
        } catch (Exception exception) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "ไม่สามารถจัดเก็บไฟล์ได้");
        }
    }

    public void deleteQuietly(String path) {
        if (path == null) return;
        try {
            var target = Path.of(path).toAbsolutePath().normalize();
            if (target.startsWith(root)) Files.deleteIfExists(target);
        } catch (Exception ignored) { }
    }

    private AuthException bad(String message) { return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message); }
    private boolean hasValidSignature(MultipartFile file,String contentType) {
        try (var input=file.getInputStream()) {
            var head=input.readNBytes(8);
            return switch(contentType) {
                case "image/jpeg" -> head.length>=3 && (head[0]&255)==0xff && (head[1]&255)==0xd8 && (head[2]&255)==0xff;
                case "image/png" -> head.length>=8 && (head[0]&255)==0x89 && head[1]==0x50 && head[2]==0x4e && head[3]==0x47 && head[4]==0x0d && head[5]==0x0a && head[6]==0x1a && head[7]==0x0a;
                case "application/pdf" -> head.length>=5 && head[0]=='%' && head[1]=='P' && head[2]=='D' && head[3]=='F' && head[4]=='-';
                default -> false;
            };
        } catch(Exception exception) { return false; }
    }
    public record StoredFile(String originalName,String storedName,String path,String contentType,long size,String sha256) {}
}
