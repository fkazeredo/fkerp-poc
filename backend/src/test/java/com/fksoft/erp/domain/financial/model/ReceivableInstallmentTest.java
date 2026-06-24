package com.fksoft.erp.domain.financial.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link ReceivableInstallment} factory (in-package to exercise the package-private {@code of}). */
class ReceivableInstallmentTest {

    private static final LocalDate DUE = LocalDate.of(2026, 7, 15);

    @Test
    void ofBuildsAnOpenInstallmentNormalizingScale() {
        ReceivableInstallment installment = ReceivableInstallment.of(2, new BigDecimal("100.5"), DUE, "parcela 2");

        assertThat(installment.id()).isNotNull();
        assertThat(installment.number()).isEqualTo(2);
        assertThat(installment.amount()).isEqualByComparingTo("100.50");
        assertThat(installment.amount().scale()).isEqualTo(2);
        assertThat(installment.dueDate()).isEqualTo(DUE);
        assertThat(installment.status()).isEqualTo(InstallmentStatus.OPEN);
        assertThat(installment.paymentNotes()).isEqualTo("parcela 2");
    }

    @Test
    void ofKeepsBlankNotesNull() {
        assertThat(ReceivableInstallment.of(1, new BigDecimal("10.00"), DUE, "   ")
                        .paymentNotes())
                .isNull();
    }

    @Test
    void ofRejectsANegativeAmount() {
        assertThatThrownBy(() -> ReceivableInstallment.of(1, new BigDecimal("-0.01"), DUE, null))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
    }

    @Test
    void ofRejectsANullAmount() {
        assertThatThrownBy(() -> ReceivableInstallment.of(1, null, DUE, null))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
    }

    @Test
    void ofRejectsANullDueDate() {
        assertThatThrownBy(() -> ReceivableInstallment.of(1, new BigDecimal("10.00"), null, null))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
    }

    @Test
    void applyPaymentPartiallyCoversTheInstallment() {
        ReceivableInstallment installment = ReceivableInstallment.of(1, new BigDecimal("100.00"), DUE, null);

        installment.applyPayment(new BigDecimal("40.00"));

        assertThat(installment.status()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(installment.amountPaid()).isEqualByComparingTo("40.00");
        assertThat(installment.outstanding()).isEqualByComparingTo("60.00");
    }

    @Test
    void applyPaymentSettlesTheInstallmentWhenItCoversTheOutstanding() {
        ReceivableInstallment installment = ReceivableInstallment.of(1, new BigDecimal("100.00"), DUE, null);
        installment.applyPayment(new BigDecimal("40.00"));

        installment.applyPayment(new BigDecimal("60.00"));

        assertThat(installment.status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(installment.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(installment.outstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void applyPaymentFullyCoversTheInstallmentInOneGo() {
        ReceivableInstallment installment = ReceivableInstallment.of(1, new BigDecimal("100.00"), DUE, null);

        installment.applyPayment(new BigDecimal("100.00"));

        assertThat(installment.status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(installment.outstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void applyPaymentRejectsAnAmountExceedingTheOutstanding() {
        ReceivableInstallment installment = ReceivableInstallment.of(1, new BigDecimal("100.00"), DUE, null);
        installment.applyPayment(new BigDecimal("40.00"));

        assertThatThrownBy(() -> installment.applyPayment(new BigDecimal("70.00")))
                .isInstanceOf(PaymentExceedsOutstandingException.class);
        // Rejected: nothing changed.
        assertThat(installment.amountPaid()).isEqualByComparingTo("40.00");
        assertThat(installment.status()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
    }

    @Test
    void applyPaymentRejectsAnAlreadyPaidInstallment() {
        ReceivableInstallment installment = ReceivableInstallment.of(1, new BigDecimal("100.00"), DUE, null);
        installment.applyPayment(new BigDecimal("100.00"));

        assertThatThrownBy(() -> installment.applyPayment(new BigDecimal("1.00")))
                .isInstanceOf(InstallmentNotPayableException.class);
    }
}
