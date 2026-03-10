package com.ecommerce.paymentservice.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payment Gateway Implementations")
class PaymentGatewayTest {

    // ========== Stripe Payment Gateway ==========

    @Nested
    @DisplayName("StripePaymentGateway")
    class StripeTests {

        private final StripePaymentGateway stripeGateway = new StripePaymentGateway();

        @Test
        @DisplayName("charge returns successful GatewayResponse with pi_ prefix transaction ID")
        void charge_returnsSuccessWithStripePrefix() {
            GatewayResponse response = stripeGateway.charge(1L, new BigDecimal("149.99"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("pi_");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("charge returns response with non-null transaction ID")
        void charge_returnsResponseWithTransactionId() {
            GatewayResponse response = stripeGateway.charge(42L, new BigDecimal("250.00"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).isNotNull();
            assertThat(response.transactionId()).isNotBlank();
            assertThat(response.transactionId()).startsWith("pi_");
        }

        @Test
        @DisplayName("charge generates unique transaction IDs for different calls")
        void charge_generatesUniqueTransactionIds() {
            GatewayResponse response1 = stripeGateway.charge(1L, new BigDecimal("100.00"));
            GatewayResponse response2 = stripeGateway.charge(2L, new BigDecimal("200.00"));

            assertThat(response1.transactionId()).isNotEqualTo(response2.transactionId());
        }

        @Test
        @DisplayName("refund returns successful GatewayResponse with re_ prefix")
        void refund_returnsSuccessWithRefundPrefix() {
            GatewayResponse response = stripeGateway.refund("pi_abc123", new BigDecimal("149.99"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("re_");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("gatewayName returns STRIPE")
        void gatewayName_returnsStripe() {
            assertThat(stripeGateway.gatewayName()).isEqualTo("STRIPE");
        }
    }

    // ========== PayPal Payment Gateway ==========

    @Nested
    @DisplayName("PayPalPaymentGateway")
    class PayPalTests {

        private final PayPalPaymentGateway paypalGateway = new PayPalPaymentGateway();

        @Test
        @DisplayName("charge returns successful GatewayResponse with PAY- prefix")
        void charge_returnsSuccessWithPayPalPrefix() {
            GatewayResponse response = paypalGateway.charge(1L, new BigDecimal("50.00"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("PAY-");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("charge generates unique transaction IDs")
        void charge_generatesUniqueTransactionIds() {
            GatewayResponse response1 = paypalGateway.charge(1L, new BigDecimal("50.00"));
            GatewayResponse response2 = paypalGateway.charge(2L, new BigDecimal("75.00"));

            assertThat(response1.transactionId()).isNotEqualTo(response2.transactionId());
        }

        @Test
        @DisplayName("refund returns successful GatewayResponse with PAYREF- prefix")
        void refund_returnsSuccessWithRefundPrefix() {
            GatewayResponse response = paypalGateway.refund("PAY-ABC123", new BigDecimal("50.00"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("PAYREF-");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("gatewayName returns PAYPAL")
        void gatewayName_returnsPayPal() {
            assertThat(paypalGateway.gatewayName()).isEqualTo("PAYPAL");
        }
    }

    // ========== Bank Transfer Gateway ==========

    @Nested
    @DisplayName("BankTransferGateway")
    class BankTransferTests {

        private final BankTransferGateway bankGateway = new BankTransferGateway();

        @Test
        @DisplayName("charge returns successful GatewayResponse with ACH- prefix")
        void charge_returnsSuccessWithBankPrefix() {
            GatewayResponse response = bankGateway.charge(1L, new BigDecimal("1000.00"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("ACH-");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("charge generates unique transaction IDs")
        void charge_generatesUniqueTransactionIds() {
            GatewayResponse response1 = bankGateway.charge(1L, new BigDecimal("500.00"));
            GatewayResponse response2 = bankGateway.charge(2L, new BigDecimal("750.00"));

            assertThat(response1.transactionId()).isNotEqualTo(response2.transactionId());
        }

        @Test
        @DisplayName("refund returns successful GatewayResponse with ACHREF- prefix")
        void refund_returnsSuccessWithRefundPrefix() {
            GatewayResponse response = bankGateway.refund("ACH-ABC123", new BigDecimal("1000.00"));

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).startsWith("ACHREF-");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("gatewayName returns BANK_TRANSFER")
        void gatewayName_returnsBankTransfer() {
            assertThat(bankGateway.gatewayName()).isEqualTo("BANK_TRANSFER");
        }
    }

    // ========== GatewayResponse ==========

    @Nested
    @DisplayName("GatewayResponse")
    class GatewayResponseTests {

        @Test
        @DisplayName("success factory method creates response with success=true and transaction ID")
        void success_createsSuccessResponse() {
            GatewayResponse response = GatewayResponse.success("txn_123");

            assertThat(response.success()).isTrue();
            assertThat(response.transactionId()).isEqualTo("txn_123");
            assertThat(response.message()).isEqualTo("Payment processed successfully");
        }

        @Test
        @DisplayName("failure factory method creates response with success=false and null transaction ID")
        void failure_createsFailureResponse() {
            GatewayResponse response = GatewayResponse.failure("Card declined");

            assertThat(response.success()).isFalse();
            assertThat(response.transactionId()).isNull();
            assertThat(response.message()).isEqualTo("Card declined");
        }
    }
}
