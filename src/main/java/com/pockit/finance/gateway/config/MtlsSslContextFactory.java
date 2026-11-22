package com.pockit.finance.gateway.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;

@Component
public class MtlsSslContextFactory {

    private static final String PKCS12_TYPE = "PKCS12";
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    private final ResourceLoader resourceLoader;

    public MtlsSslContextFactory(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public SslContext createClientSslContext(
            String keystorePath,
            String keystorePassword,
            String truststorePath,
            String truststorePassword
    ) {
        validateStoreParameter(keystorePath, "keystore path");
        validateStoreParameter(truststorePath, "truststore path");

        char[] keystoreChars = keystorePassword != null ? keystorePassword.toCharArray() : new char[0];
        char[] truststoreChars = truststorePassword != null ? truststorePassword.toCharArray() : new char[0];

        try {
            KeyStore keyStore = loadKeyStore(keystorePath, keystoreChars);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keystoreChars);

            KeyStore trustStore = loadKeyStore(truststorePath, truststoreChars);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            return SslContextBuilder.forClient()
                    .keyManager(keyManagerFactory)
                    .trustManager(trustManagerFactory)
                    .protocols(ENABLED_PROTOCOLS)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize mTLS SslContext", ex);
        } finally {
            Arrays.fill(keystoreChars, '\0');
            Arrays.fill(truststoreChars, '\0');
        }
    }

    public KeyStore loadKeyStore(String location, char[] password) {
        validateStoreParameter(location, "store location");
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("PKCS12 store not found at location: " + location);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            KeyStore keyStore = KeyStore.getInstance(PKCS12_TYPE);
            keyStore.load(inputStream, password);
            return keyStore;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load PKCS12 store from location: " + location, ex);
        }
    }

    private void validateStoreParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mTLS " + name + " must not be null or blank");
        }
    }
}
