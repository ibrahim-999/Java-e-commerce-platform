package com.ecommerce.notificationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationServiceApplication Tests")
class NotificationServiceApplicationTest {

    @Test
    @DisplayName("application class should be instantiable")
    void applicationClassShouldBeInstantiable() {
        NotificationServiceApplication app = new NotificationServiceApplication();
        assertThat(app).isNotNull();
    }
}
