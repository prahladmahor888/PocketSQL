package com.mysql.pocketsql.engine;

public interface CertGenerator {
    CertificateAndKey generate() throws Exception;
}
