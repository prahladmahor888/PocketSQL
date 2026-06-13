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
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class JvmCertGenerator implements CertGenerator {
    @Override
    public CertificateAndKey generate() throws Exception {
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
            .build(keyPair.getPrivate());
            
        X509Certificate certificate = new JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signer));

        return new CertificateAndKey(certificate, keyPair.getPrivate());
    }
}
