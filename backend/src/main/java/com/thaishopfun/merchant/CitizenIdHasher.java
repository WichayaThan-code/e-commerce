package com.thaishopfun.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CitizenIdHasher {
    private CitizenIdHasher() {}

    public static String normalize(String citizenId) {
        if (citizenId == null) return "";
        return citizenId.replaceAll("[^0-9]", "");
    }

    public static String hash(String citizenId) {
        var normalized = normalize(citizenId);
        if (normalized.isBlank()) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) builder.append(String.format("%02x", value));
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
