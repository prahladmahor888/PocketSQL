package com.mysql.pocketsql.engine;

import java.security.PrivateKey;
import java.security.cert.Certificate;

public class CertificateAndKey {
    public final Certificate certificate;
    public final PrivateKey privateKey;

    public CertificateAndKey(Certificate certificate, PrivateKey privateKey) {
        this.certificate = certificate;
        this.privateKey = privateKey;
    }
}
