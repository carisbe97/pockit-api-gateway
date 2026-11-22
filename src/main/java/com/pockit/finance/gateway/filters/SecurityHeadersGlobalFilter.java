package com.pockit.finance.gateway.filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersGlobalFilter implements WebFilter, Ordered {

    private static final String DEFAULT_HSTS = "max-age=31536000; includeSubDomains";

    private final String contentSecurityPolicy;

    public SecurityHeadersGlobalFilter(
            @Value("${app.security.headers.content-security-policy:default-src 'none'; frame-ancestors 'none'}")
            String contentSecurityPolicy
    ) {
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", contentSecurityPolicy);
        headers.set("Strict-Transport-Security", DEFAULT_HSTS);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
