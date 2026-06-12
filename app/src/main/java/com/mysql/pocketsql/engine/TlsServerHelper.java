package com.mysql.pocketsql.engine;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class TlsServerHelper {

    public static SSLServerSocketFactory getSslServerSocketFactory() throws Exception {
        // Ensure Bouncy Castle provider is registered
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        // Generate keypair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Build self-signed certificate
        X500Name DN = new X500Name("CN=localhost, O=PocketSQL, C=US");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);
        
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            DN, serial, notBefore, notAfter, DN, keyPair.getPublic());
            
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());
            
        X509Certificate certificate = new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer));

        // Generate a random keystore password dynamically
        SecureRandom secureRandom = new SecureRandom();
        byte[] passwordBytes = new byte[16];
        secureRandom.nextBytes(passwordBytes);
        char[] password = java.util.Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        // Setup Keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("psql_entry", keyPair.getPrivate(), password, 
            new Certificate[]{certificate});

        // Setup KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        // Setup SSLContext with TLS 1.3
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        return sslContext.getServerSocketFactory();
    }
}
