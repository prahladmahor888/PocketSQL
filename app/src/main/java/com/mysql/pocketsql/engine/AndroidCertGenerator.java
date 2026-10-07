package com.mysql.pocketsql.engine;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.math.BigInteger;
import java.util.Calendar;
import javax.security.auth.x500.X500Principal;

public class AndroidCertGenerator implements CertGenerator {
    @Override
    public CertificateAndKey generate() throws Exception {
        String provider = SqlEnvConfig.getKeystoreProvider();
        String alias = SqlEnvConfig.getKeystoreTlsCertAlias();
        int rsaKeySize = SqlEnvConfig.getKeystoreRsaKeySize();
        String subject = SqlEnvConfig.getKeystoreCertSubject();
        int validityYears = SqlEnvConfig.getKeystoreCertValidityYears();

        KeyStore keyStore = KeyStore.getInstance(provider);
        keyStore.load(null);
        
        if (!keyStore.containsAlias(alias)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, provider);
            
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, validityYears > 0 ? validityYears : 1);
            
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(rsaKeySize > 0 ? rsaKeySize : 2048)
                .setCertificateSubject(new X500Principal(subject != null && !subject.isEmpty() ? subject : "CN=localhost, O=PocketSQL, C=US"))
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
