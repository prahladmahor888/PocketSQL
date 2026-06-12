package com.mysql.pocketsql.engine;

import android.content.Context;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;
import com.google.android.gms.tasks.Task;

public class AppIntegrityManager {

    public static void checkAppIntegrity(Context context) {
        try {
            // Perform Root detection
            if (isRooted()) {
                SqlLog.err("AppIntegrityManager: Rooted device detected!");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(context.getApplicationContext(), 
                        "Security Warning: Rooted device detected! Secure features may be degraded.", 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }

            // Perform Emulator detection
            if (isEmulator()) {
                SqlLog.err("AppIntegrityManager: Emulator environment detected!");
            }

            // Perform Hook detection
            if (isHooked()) {
                SqlLog.err("AppIntegrityManager: Hooking framework detected!");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(context.getApplicationContext(), 
                        "Security Warning: Hooking framework detected! Potential tamper risk.", 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }

            // Generate a cryptographically secure random nonce using SecureRandom
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            byte[] nonceBytes = new byte[24];
            secureRandom.nextBytes(nonceBytes);
            String nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
            
            // Create the IntegrityManager instance
            IntegrityManager integrityManager = IntegrityManagerFactory.create(context.getApplicationContext());
            
            // Request the integrity token from Google Play
            // Using a representative cloud project number
            IntegrityTokenRequest request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(987654321012L) 
                .build();
                
            Task<IntegrityTokenResponse> integrityTokenResponse = integrityManager.requestIntegrityToken(request);
            
            integrityTokenResponse.addOnSuccessListener(response -> {
                String token = response.token();
                SqlLog.err("Play Integrity token received successfully: " + (token.length() > 20 ? token.substring(0, 20) + "..." : token));
                // Send the token to the backend server to parse and verify the authenticity of the app
            });
            
            integrityTokenResponse.addOnFailureListener(e -> {
                SqlLog.err("Play Integrity validation failed: " + e.getMessage());
            });
            
        } catch (Throwable t) {
            SqlLog.printStackTrace(t);
        }
    }

    public static String decode(int[] data) {
        StringBuilder sb = new StringBuilder();
        for (int x : data) {
            sb.append((char) (x ^ 42));
        }
        return sb.toString();
    }

    public static void setPrimaryClip(android.content.ClipboardManager clipboard, android.content.ClipData clip) {
        if (clipboard == null || clip == null) return;
        try {
            java.lang.reflect.Method method = android.content.ClipboardManager.class.getMethod(
                decode(new int[]{89, 79, 94, 122, 88, 67, 71, 75, 88, 83, 105, 70, 67, 90}),
                android.content.ClipData.class
            );
            method.invoke(clipboard, clip);
        } catch (Throwable t) {
            clipboard.setPrimaryClip(clip);
        }
    }

    public static void clearPrimaryClip(android.content.ClipboardManager clipboard) {
        if (clipboard == null) return;
        try {
            java.lang.reflect.Method method = android.content.ClipboardManager.class.getMethod(
                decode(new int[]{73, 70, 79, 75, 88, 122, 88, 67, 71, 75, 88, 83, 105, 70, 67, 90})
            );
            method.invoke(clipboard);
        } catch (Throwable t) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip();
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""));
            }
        }
    }

    public static boolean isRooted() {
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains(decode(new int[]{78, 63, 73, 78, 7, 81, 63, 83, 73}))) {
            return true;
        }

        String suPart = decode(new int[]{73, 95});
        
        String superUserPath = decode(new int[]{21, 73, 83, 73, 78, 63, 67, 21, 123, 90, 90, 21, 125, 95, 90, 63, 88, 95, 73, 63, 88, 20, 123, 90, 81});

        String[] paths = {
            superUserPath,
            "/sbin/" + suPart,
            "/system/bin/" + suPart,
            "/system/xbin/" + suPart,
            "/data/local/xbin/" + suPart,
            "/data/local/bin/" + suPart,
            "/system/sd/xbin/" + suPart,
            "/system/bin/failsafe/" + suPart,
            "/data/local/" + suPart
        };
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }

        return false;
    }



    public static boolean isEmulator() {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.BRAND.startsWith("generic")
                || android.os.Build.DEVICE.startsWith("generic")
                || android.os.Build.PRODUCT.contains("sdk_gphone")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator"));
    }

    public static boolean isHooked() {
        try {
            throw new Exception("Stack trace check");
        } catch (Exception e) {
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                if (stackTraceElement.getClassName().contains(decode(new int[]{78, 79, 4, 88, 69, 72, 92, 4, 75, 68, 78, 88, 69, 67, 78, 4, 82, 90, 69, 89, 79, 78})) ||
                    stackTraceElement.getClassName().contains(decode(new int[]{73, 69, 71, 4, 89, 75, 95, 88, 67, 65, 4, 89, 95, 72, 89, 94, 88, 75, 94, 79}))) {
                    return true;
                }
            }
        }

        try {
            Class.forName(decode(new int[]{78, 79, 4, 88, 69, 72, 92, 4, 75, 68, 78, 88, 69, 67, 78, 4, 82, 90, 69, 89, 79, 78, 4, 114, 90, 69, 89, 79, 78, 104, 88, 67, 78, 77, 79}));
            return true;
        } catch (ClassNotFoundException e) {
            // Ignore
        }

        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/maps"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(decode(new int[]{76, 88, 67, 78, 75})) || line.contains(decode(new int[]{82, 90, 69, 89, 79, 78}))) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    private static String Base64UrlEncode(String input) {
        try {
            return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "PocketSQLNoncePlaceholder";
        }
    }
}
