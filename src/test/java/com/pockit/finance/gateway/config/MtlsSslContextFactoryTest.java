package com.pockit.finance.gateway.config;

import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtlsSslContextFactoryTest {

    private MtlsSslContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MtlsSslContextFactory(new DefaultResourceLoader());
    }

    @Test
    void shouldCreateClientSslContextFromValidPkcs12Stores(@TempDir Path tempDir) throws Exception {
        Path keystorePath = tempDir.resolve("gateway-keystore.p12");
        Path truststorePath = tempDir.resolve("pockit-truststore.p12");
        String password = "changeit";

        createEmptyPkcs12Store(keystorePath, password);
        createEmptyPkcs12Store(truststorePath, password);

        SslContext sslContext = factory.createClientSslContext(
                keystorePath.toUri().toString(),
                password,
                truststorePath.toUri().toString(),
                password
        );

        assertThat(sslContext).isNotNull();
        assertThat(sslContext.isClient()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenKeystorePathIsMissing() {
        assertThatThrownBy(() -> factory.createClientSslContext(
                "",
                "secret",
                "file:/nonexistent/truststore.p12",
                "secret"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keystore");
    }

    @Test
    void shouldThrowExceptionWhenStoreFileDoesNotExist(@TempDir Path tempDir) {
        Path missingStore = tempDir.resolve("missing.p12");

        assertThatThrownBy(() -> factory.createClientSslContext(
                missingStore.toUri().toString(),
                "secret",
                missingStore.toUri().toString(),
                "secret"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to initialize mTLS SslContext");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsInvalid(@TempDir Path tempDir) throws Exception {
        Path keystorePath = tempDir.resolve("gateway-keystore.p12");
        createEmptyPkcs12Store(keystorePath, "correct-password");

        assertThatThrownBy(() -> factory.loadKeyStore(
                keystorePath.toUri().toString(),
                "wrong-password".toCharArray()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load PKCS12 store");
    }

    private void createEmptyPkcs12Store(Path path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password.toCharArray());
        try (OutputStream out = Files.newOutputStream(path)) {
            keyStore.store(out, password.toCharArray());
        }
    }
}
