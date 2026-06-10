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
            // Generate a cryptographically secure nonce or a custom app verification challenge string
            String nonce = Base64UrlEncode("PocketSQL_Nonce_AppIntegrity_2026");
            
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

    private static String Base64UrlEncode(String input) {
        try {
            return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "PocketSQLNoncePlaceholder";
        }
    }
}
