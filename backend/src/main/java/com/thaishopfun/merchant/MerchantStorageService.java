package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

<<<<<<< HEAD
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.Map;
=======
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import java.util.UUID;

@Service
public class MerchantStorageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String,String> EXTENSIONS = Map.of(
<<<<<<< HEAD
        "image/jpeg", ".jpg", "image/png", ".png", "application/pdf", ".pdf");
=======
        "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "application/pdf", ".pdf");
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    private final Path root;

    public MerchantStorageService(MerchantProperties properties) {
        this.root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
    }

<<<<<<< HEAD
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
=======
    public StoredFile store(UUID applicationId, MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) throw bad("กรุณาเลือกไฟล์");
        if (file.getSize() > MAX_BYTES) throw bad("ไฟล์ต้องมีขนาดไม่เกิน 5 MB");
        var contentType = imageType(file);
        var extension = EXTENSIONS.get(contentType);
        if (extension == null) throw bad("รองรับเฉพาะไฟล์ JPG, PNG, WEBP หรือ PDF");
        if (!hasValidSignature(file, contentType)) throw bad("เนื้อหาไฟล์ไม่ตรงกับชนิดไฟล์ที่ระบุ");
        var directory = root.resolve(applicationId.toString()).normalize();
        if (!directory.startsWith(root)) throw bad("ตำแหน่งจัดเก็บไฟล์ไม่ถูกต้อง");
        try {
            Files.createDirectories(directory);
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes;
            try (var input = new DigestInputStream(file.getInputStream(), digest)) {
                bytes = input.readAllBytes();
            }
            var stored = writeUnique(directory, kind, extension, bytes);
            var target = directory.resolve(stored).normalize();
            return new StoredFile(stored, stored, target.toString(), contentType, bytes.length, HexFormat.of().formatHex(digest.digest()));
        } catch (AuthException exception) {
            throw exception;
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        } catch (Exception exception) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "ไม่สามารถจัดเก็บไฟล์ได้");
        }
    }

<<<<<<< HEAD
=======
    public Optional<Path> openPublicImage(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        try {
            var target = Path.of(path).toAbsolutePath().normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) return Optional.empty();
            var name = target.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp")) return Optional.empty();
            return Optional.of(target);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<Path> openStoredFile(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        try {
            var target = Path.of(path).toAbsolutePath().normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) return Optional.empty();
            var name = target.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp") && !name.endsWith(".pdf")) return Optional.empty();
            return Optional.of(target);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    public void deleteQuietly(String path) {
        if (path == null) return;
        try {
            var target = Path.of(path).toAbsolutePath().normalize();
            if (target.startsWith(root)) Files.deleteIfExists(target);
        } catch (Exception ignored) { }
    }

<<<<<<< HEAD
    private AuthException bad(String message) { return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message); }
    private boolean hasValidSignature(MultipartFile file,String contentType) {
        try (var input=file.getInputStream()) {
            var head=input.readNBytes(8);
            return switch(contentType) {
                case "image/jpeg" -> head.length>=3 && (head[0]&255)==0xff && (head[1]&255)==0xd8 && (head[2]&255)==0xff;
                case "image/png" -> head.length>=8 && (head[0]&255)==0x89 && head[1]==0x50 && head[2]==0x4e && head[3]==0x47 && head[4]==0x0d && head[5]==0x0a && head[6]==0x1a && head[7]==0x0a;
=======
    private static String imageType(MultipartFile file) {
        var name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".pdf")) return "application/pdf";
        var type = file.getContentType();
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    public StoredFile copyInto(UUID applicationId, Path source, String kind, String contentType) {
        var extension = EXTENSIONS.getOrDefault(contentType, "");
        if (extension.isBlank()) throw bad("ไม่สามารถคัดลอกเอกสารเดิมได้");
        var directory = root.resolve(applicationId.toString()).normalize();
        var from = source.toAbsolutePath().normalize();
        if (!directory.startsWith(root) || !from.startsWith(root)) throw bad("ตำแหน่งจัดเก็บไฟล์ไม่ถูกต้อง");
        try {
            Files.createDirectories(directory);
            var stored = writeUnique(directory, kind, extension, Files.readAllBytes(from));
            var target = directory.resolve(stored).normalize();
            return new StoredFile(stored, stored, target.toString(), contentType, Files.size(target), "");
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "ไม่สามารถคัดลอกเอกสารเดิมได้");
        }
    }

    private String writeUnique(Path directory, String kind, String extension, byte[] bytes) throws IOException {
        var type = kind == null ? "" : kind.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (type.isBlank()) type = "FILE";
        if (type.length() > 24) type = type.substring(0, 24);
        for (var attempt = 0; attempt < 8; attempt++) {
            var token = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            var name = "Doc_(" + type + ")_" + token + extension;
            var target = directory.resolve(name).normalize();
            if (!target.startsWith(directory)) continue;
            try {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
                return name;
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // another upload took this name; try a new token
            }
        }
        throw new IOException("ไม่สามารถสร้างชื่อไฟล์ที่ไม่ซ้ำได้");
    }

    private AuthException bad(String message) { return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message); }
    private boolean hasValidSignature(MultipartFile file,String contentType) {
        try (var input=file.getInputStream()) {
            var head=input.readNBytes(12);
            return switch(contentType) {
                case "image/jpeg" -> head.length>=3 && (head[0]&255)==0xff && (head[1]&255)==0xd8 && (head[2]&255)==0xff;
                case "image/png" -> head.length>=8 && (head[0]&255)==0x89 && head[1]==0x50 && head[2]==0x4e && head[3]==0x47 && head[4]==0x0d && head[5]==0x0a && head[6]==0x1a && head[7]==0x0a;
                case "image/webp" -> head.length>=12 && head[0]=='R' && head[1]=='I' && head[2]=='F' && head[3]=='F' && head[8]=='W' && head[9]=='E' && head[10]=='B' && head[11]=='P';
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                case "application/pdf" -> head.length>=5 && head[0]=='%' && head[1]=='P' && head[2]=='D' && head[3]=='F' && head[4]=='-';
                default -> false;
            };
        } catch(Exception exception) { return false; }
    }
    public record StoredFile(String originalName,String storedName,String path,String contentType,long size,String sha256) {}
}
