package com.ecommerce.notificationservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthController Unit Tests")
class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    @DisplayName("should return health map with correct keys")
    void shouldReturnHealthMapWithCorrectKeys() {
        Map<String, Object> result = controller.health();

        assertThat(result).containsKeys("service", "status", "timestamp");
    }

    @Test
    @DisplayName("should return status UP")
    void shouldReturnStatusUp() {
        Map<String, Object> result = controller.health();

        assertThat(result.get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("should return correct service name")
    void shouldReturnCorrectServiceName() {
        Map<String, Object> result = controller.health();

        assertThat(result.get("service")).isEqualTo("notification-service");
    }

    @Test
    @DisplayName("should return non-null timestamp")
    void shouldReturnNonNullTimestamp() {
        Map<String, Object> result = controller.health();

        assertThat(result.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("should return exactly three entries")
    void shouldReturnExactlyThreeEntries() {
        Map<String, Object> result = controller.health();

        assertThat(result).hasSize(3);
    }
}
