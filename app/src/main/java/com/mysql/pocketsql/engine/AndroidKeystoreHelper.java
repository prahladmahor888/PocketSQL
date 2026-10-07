package com.mysql.pocketsql.engine;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class AndroidKeystoreHelper {

    public static SecretKey getOrCreateKey() throws Exception {
        String provider = SqlEnvConfig.getKeystoreProvider();
        String alias = SqlEnvConfig.getKeystoreKeyAlias();
        int keySize = SqlEnvConfig.getKeystoreAesKeySize();

        KeyStore keyStore = KeyStore.getInstance(provider);
        keyStore.load(null);
        if (!keyStore.containsAlias(alias)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, provider);
            
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(keySize > 0 ? keySize : 256)
                .build();
            
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(alias, null);
    }
}
