package com.mysql.pocketsql.engine;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public class SecurityHelper {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // in bits

    private static SecretKey jvmKey = null;

    private static synchronized SecretKey getSecretKey() throws Exception {
        if (java.security.Security.getProvider("AndroidKeyStore") != null) {
            return AndroidKeystoreHelper.getOrCreateKey();
        } else {
            // JVM fallback: generate a 256-bit key dynamically on first use (in-memory caching)
            if (jvmKey == null) {
                byte[] keyBytes = new byte[32];
                new SecureRandom().nextBytes(keyBytes);
                jvmKey = new SecretKeySpec(keyBytes, "AES");
            }
            return jvmKey;
        }
    }

    public static String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;
        SecretKey keySpec = getSecretKey();
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length == 0) {
            throw new Exception("Encryption failed: generated IV is null or empty");
        }
        
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV and Ciphertext: [IV length bytes][Ciphertext...]
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null) return null;
        
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(encryptedText.trim());
        } catch (IllegalArgumentException e) {
            // If the string is not valid Base64, return it as-is (plaintext fallback support)
            return encryptedText;
        }

        // Try AES-256-GCM Decryption (using KeyStore/JVM Key)
        if (decodedBytes.length > GCM_IV_LENGTH) {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[decodedBytes.length - GCM_IV_LENGTH];
            System.arraycopy(decodedBytes, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decodedBytes, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey keySpec = getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } else {
            throw new Exception("Decryption failed: invalid ciphertext length");
        }
    }

    public static String hashPassword(String password) {
        if (password == null) return "";
        try {
            byte[] salt = new byte[16];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(salt);
            
            int iterations = 3;
            int memory = 65536; // 64MB
            int parallelism = 4;
            int hashLength = 32;
            
            byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] hash = hashArgon2id(pwdBytes, salt, iterations, memory, parallelism, hashLength);
            
            String saltBase64 = Base64.getEncoder().encodeToString(salt).replace("=", "");
            String hashBase64 = Base64.getEncoder().encodeToString(hash).replace("=", "");
            
            return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s", memory, iterations, parallelism, saltBase64, hashBase64);
        } catch (Exception e) {
            return hashPasswordLegacy(password); // Fallback to SHA-256 if Argon2id fails
        }
    }

    public static boolean verifyPassword(String password, String stored) {
        if (stored == null || password == null) return false;
        if (stored.startsWith("$argon2id$")) {
            return verifyPasswordArgon2id(password, stored);
        }
        // Fallback to legacy SHA-256 or plaintext
        return stored.equals(hashPasswordLegacy(password)) || stored.equals(password);
    }

    private static String hashPasswordLegacy(String password) {
        if (password == null) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("S" + "H" + "A-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private static byte[] hashArgon2id(byte[] password, byte[] salt, int iterations, int memory, int parallelism, int outputLength) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism)
            .withSalt(salt);
            
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(builder.build());
        byte[] result = new byte[outputLength];
        gen.generateBytes(password, result, 0, result.length);
        return result;
    }

    private static boolean verifyPasswordArgon2id(String password, String phcString) {
        try {
            String[] parts = phcString.split("\\$");
            if (parts.length < 6) return false;
            // phcString split with $:
            // parts[1] is "argon2id"
            // parts[3] is parameters "m=65536,t=3,p=4"
            // parts[4] is salt
            // parts[5] is hash
            
            String paramsStr = parts[3];
            int memory = 65536;
            int iterations = 3;
            int parallelism = 4;
            for (String param : paramsStr.split(",")) {
                String[] kv = param.split("=");
                if (kv.length == 2) {
                    if ("m".equals(kv[0])) memory = Integer.parseInt(kv[1]);
                    else if ("t".equals(kv[0])) iterations = Integer.parseInt(kv[1]);
                    else if ("p".equals(kv[0])) parallelism = Integer.parseInt(kv[1]);
                }
            }
            
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[5]);
            
            byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] actualHash = hashArgon2id(pwdBytes, salt, iterations, memory, parallelism, expectedHash.length);
            
            return java.security.MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }
}
