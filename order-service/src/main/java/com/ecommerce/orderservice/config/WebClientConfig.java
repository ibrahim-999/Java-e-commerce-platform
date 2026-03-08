package com.ecommerce.orderservice.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

// WebClient configuration — creates HTTP clients for inter-service communication.
//
// WebClient vs RestTemplate:
//   - RestTemplate is DEPRECATED (synchronous, blocking)
//   - WebClient is the modern replacement (supports both sync and async)
//   - Even when used synchronously (with .block()), WebClient is more efficient
//
// We create two named WebClient beans — one per service we need to call.
// Each is pre-configured with the correct base URL.

@Configuration
public class WebClientConfig {

    @Value("${service.user-service.url}")
    private String userServiceUrl;

    @Value("${service.product-service.url}")
    private String productServiceUrl;

    @Value("${service.payment-service.url}")
    private String paymentServiceUrl;

    // WebClient for calling user-service (HTTPS with self-signed cert).
    // In development, we trust all certificates (InsecureTrustManagerFactory).
    // In production, you'd use proper CA-signed certificates.
    @Bean
    public WebClient userServiceClient() throws SSLException {
        var sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        var httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext));

        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // WebClient for calling product-service (HTTP, no SSL needed)
    @Bean
    public WebClient productServiceClient() {
        return WebClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    // WebClient for calling payment-service (HTTP, no SSL needed)
    @Bean
    public WebClient paymentServiceClient() {
        return WebClient.builder()
                .baseUrl(paymentServiceUrl)
                .build();
    }
}
