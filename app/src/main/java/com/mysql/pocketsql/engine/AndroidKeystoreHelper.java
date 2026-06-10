package com.mysql.pocketsql.engine;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class AndroidKeystoreHelper {
    private static final String ALIAS = "PocketSQLAESKeyAlias";

    public static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
            
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(ALIAS, null);
    }
}
