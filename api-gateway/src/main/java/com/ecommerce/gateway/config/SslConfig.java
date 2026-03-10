package com.ecommerce.gateway.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

// SSL Configuration for the API Gateway.
//
// Problem: user-service uses HTTPS with a self-signed certificate.
// By default, the gateway's HTTP client rejects self-signed certs
// (just like your browser shows a warning).
//
// Solution: configure the Netty HTTP client to trust ALL certificates.
//
// WARNING: InsecureTrustManagerFactory trusts ANY certificate — this is
// ONLY acceptable in development. In production, you would:
//   1. Use a real certificate (Let's Encrypt, AWS ACM, etc.)
//   2. Or add the self-signed cert to a trust store

@Configuration
public class SslConfig {

    @Bean
    public HttpClient httpClient() throws SSLException {
        return HttpClient.create()
                .secure(ssl -> {
                    try {
                        ssl.sslContext(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                    } catch (SSLException e) {
                        throw new RuntimeException("Failed to configure SSL", e);
                    }
                });
    }
}
