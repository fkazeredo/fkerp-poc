package com.fksoft.erp.domain.financial.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fksoft.erp.domain.financial.exception.PaymentAlreadyReversedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link ReceivablePayment} reversal state (in-package to exercise the package-private API). */
class ReceivablePaymentTest {

    private ReceivablePayment payment() {
        return ReceivablePayment.of(
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                LocalDate.of(2026, 6, 20),
                mock(PaymentMethod.class),
                "pix",
                UUID.randomUUID());
    }

    @Test
    void aFreshPaymentIsNotReversed() {
        ReceivablePayment payment = payment();
        assertThat(payment.reversed()).isFalse();
        assertThat(payment.reversalReason()).isNull();
        assertThat(payment.reversedBy()).isNull();
        assertThat(payment.reversedAt()).isNull();
    }

    @Test
    void reverseStampsTheReasonAuthorAndInstant() {
        ReceivablePayment payment = payment();
        UUID by = UUID.randomUUID();
        Instant at = Instant.parse("2026-06-24T12:00:00Z");

        payment.reverse("lançamento duplicado", by, at);

        assertThat(payment.reversed()).isTrue();
        assertThat(payment.reversalReason()).isEqualTo("lançamento duplicado");
        assertThat(payment.reversedBy()).isEqualTo(by);
        assertThat(payment.reversedAt()).isEqualTo(at);
    }

    @Test
    void reverseKeepsBlankReasonNull() {
        ReceivablePayment payment = payment();
        payment.reverse("   ", UUID.randomUUID(), Instant.now());
        assertThat(payment.reversalReason()).isNull();
        assertThat(payment.reversed()).isTrue();
    }

    @Test
    void reversingAnAlreadyReversedPaymentIsRejected() {
        ReceivablePayment payment = payment();
        payment.reverse("motivo", UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> payment.reverse("outro motivo", UUID.randomUUID(), Instant.now()))
                .isInstanceOf(PaymentAlreadyReversedException.class);
    }
}
