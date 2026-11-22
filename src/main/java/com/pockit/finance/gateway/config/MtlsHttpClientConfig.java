package com.pockit.finance.gateway.config;

import io.netty.handler.ssl.SslContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.mtls.enabled", havingValue = "true")
public class MtlsHttpClientConfig {

    @Bean
    public HttpClientCustomizer mtlsHttpClientCustomizer(
            MtlsSslContextFactory sslContextFactory,
            @Value("${app.mtls.keystore-path}") String keystorePath,
            @Value("${app.mtls.keystore-password}") String keystorePassword,
            @Value("${app.mtls.truststore-path}") String truststorePath,
            @Value("${app.mtls.truststore-password}") String truststorePassword
    ) {
        SslContext sslContext = sslContextFactory.createClientSslContext(
                keystorePath,
                keystorePassword,
                truststorePath,
                truststorePassword
        );
        return httpClient -> httpClient.secure(sslSpec -> sslSpec.sslContext(sslContext));
    }
}
