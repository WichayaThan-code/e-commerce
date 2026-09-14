package com.thaishopfun.merchant;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SensitiveDataCipher {
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public SensitiveDataCipher(MerchantProperties properties){try{var bytes=MessageDigest.getInstance("SHA-256").digest(properties.dataEncryptionSecret().getBytes(StandardCharsets.UTF_8));key=new SecretKeySpec(bytes,"AES");}catch(Exception e){throw new IllegalStateException(e);}}
    public String encrypt(String value){if(value==null||value.isBlank())return null;try{byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] joined=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,joined,0,iv.length);System.arraycopy(encrypted,0,joined,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(joined);}catch(Exception e){throw new IllegalStateException("ไม่สามารถเข้ารหัสข้อมูลได้",e);}}
    public String decrypt(String value){if(value==null)return null;try{byte[] joined=Base64.getDecoder().decode(value);byte[] iv=java.util.Arrays.copyOfRange(joined,0,12);byte[] encrypted=java.util.Arrays.copyOfRange(joined,12,joined.length);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("ไม่สามารถถอดรหัสข้อมูลได้",e);}}
    public String mask(String encrypted){var raw=decrypt(encrypted);if(raw==null)return null;var clean=raw.replaceAll("[^0-9]","");return clean.length()<=4?"****":"*".repeat(clean.length()-4)+clean.substring(clean.length()-4);}
}
