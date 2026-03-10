package com.ecommerce.paymentservice.gateway;

import com.ecommerce.paymentservice.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentGatewayFactory")
class PaymentGatewayFactoryTest {

    private PaymentGatewayFactory factory;
    private StripePaymentGateway stripeGateway;
    private PayPalPaymentGateway paypalGateway;
    private BankTransferGateway bankTransferGateway;

    @BeforeEach
    void setUp() {
        stripeGateway = new StripePaymentGateway();
        paypalGateway = new PayPalPaymentGateway();
        bankTransferGateway = new BankTransferGateway();
        factory = new PaymentGatewayFactory(stripeGateway, paypalGateway, bankTransferGateway);
    }

    @Test
    @DisplayName("getGateway for CREDIT_CARD returns StripePaymentGateway")
    void getGateway_creditCard_returnsStripe() {
        PaymentGateway gateway = factory.getGateway(PaymentMethod.CREDIT_CARD);

        assertThat(gateway).isInstanceOf(StripePaymentGateway.class);
        assertThat(gateway).isSameAs(stripeGateway);
    }

    @Test
    @DisplayName("getGateway for DEBIT_CARD returns StripePaymentGateway")
    void getGateway_debitCard_returnsStripe() {
        PaymentGateway gateway = factory.getGateway(PaymentMethod.DEBIT_CARD);

        assertThat(gateway).isInstanceOf(StripePaymentGateway.class);
        assertThat(gateway).isSameAs(stripeGateway);
    }

    @Test
    @DisplayName("getGateway for CREDIT_CARD and DEBIT_CARD returns the same Stripe instance")
    void getGateway_creditAndDebit_returnSameStripeInstance() {
        PaymentGateway creditGateway = factory.getGateway(PaymentMethod.CREDIT_CARD);
        PaymentGateway debitGateway = factory.getGateway(PaymentMethod.DEBIT_CARD);

        assertThat(creditGateway).isSameAs(debitGateway);
    }

    @Test
    @DisplayName("getGateway for PAYPAL returns PayPalPaymentGateway")
    void getGateway_paypal_returnsPayPal() {
        PaymentGateway gateway = factory.getGateway(PaymentMethod.PAYPAL);

        assertThat(gateway).isInstanceOf(PayPalPaymentGateway.class);
        assertThat(gateway).isSameAs(paypalGateway);
    }

    @Test
    @DisplayName("getGateway for BANK_TRANSFER returns BankTransferGateway")
    void getGateway_bankTransfer_returnsBankTransfer() {
        PaymentGateway gateway = factory.getGateway(PaymentMethod.BANK_TRANSFER);

        assertThat(gateway).isInstanceOf(BankTransferGateway.class);
        assertThat(gateway).isSameAs(bankTransferGateway);
    }
}
