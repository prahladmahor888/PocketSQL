package com.mysql.pocketsql.engine;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.math.BigInteger;
import java.util.Calendar;

public class AndroidCertGenerator implements CertGenerator {
    @Override
    public CertificateAndKey generate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        
        String alias = "psql_entry";
        if (!keyStore.containsAlias(alias)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 1);
            
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                .setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=localhost, O=PocketSQL, C=US"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(start.getTime())
                .setCertificateNotAfter(end.getTime())
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build();
            
            kpg.initialize(spec);
            kpg.generateKeyPair();
        }
        
        Certificate certificate = keyStore.getCertificate(alias);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
        return new CertificateAndKey(certificate, privateKey);
    }
}
