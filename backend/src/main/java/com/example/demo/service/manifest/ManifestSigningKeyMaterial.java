package com.example.demo.service.manifest;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

record ManifestSigningKeyMaterial(PrivateKey privateKey, X509Certificate certificate, PublicKey publicKey) {

    ManifestSigningKeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
        this(privateKey, certificate, certificate.getPublicKey());
    }
}
