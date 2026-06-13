package com.mysql.pocketsql.engine;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class TlsServerHelper {

    public static SSLServerSocketFactory getSslServerSocketFactory() throws Exception {
        // Platform-aware dynamic certificate generation
        CertGenerator generator;
        if (System.getProperty("java.vendor").contains("Android") || java.security.Security.getProvider("AndroidKeyStore") != null) {
            String androidClass = AppIntegrityManager.decode(new int[]{73, 69, 71, 4, 71, 83, 89, 91, 70, 4, 90, 69, 73, 65, 79, 94, 89, 91, 70, 4, 79, 68, 77, 67, 68, 79, 4, 107, 68, 78, 88, 69, 67, 78, 105, 79, 88, 94, 109, 79, 68, 79, 88, 75, 94, 69, 88});
            generator = (CertGenerator) Class.forName(androidClass)
                .getDeclaredConstructor().newInstance();
        } else {
            String jvmClass = AppIntegrityManager.decode(new int[]{73, 69, 71, 4, 71, 83, 89, 91, 70, 4, 90, 69, 73, 65, 79, 94, 89, 91, 70, 4, 79, 68, 77, 67, 68, 79, 4, 96, 92, 71, 105, 79, 88, 94, 109, 79, 68, 79, 88, 75, 94, 69, 88});
            generator = (CertGenerator) Class.forName(jvmClass)
                .getDeclaredConstructor().newInstance();
        }
        
        CertificateAndKey certAndKey = generator.generate();

        // Generate a random keystore password dynamically
        SecureRandom secureRandom = new SecureRandom();
        byte[] passwordBytes = new byte[16];
        secureRandom.nextBytes(passwordBytes);
        char[] password = java.util.Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        // Setup Keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("psql_entry", certAndKey.privateKey, password, 
            new Certificate[]{certAndKey.certificate});

        // Setup KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        // Setup SSLContext with TLS 1.3
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        return sslContext.getServerSocketFactory();
    }
}
