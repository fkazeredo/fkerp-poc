package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.model.ReceivablePayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a single payment registered against a Receivable installment. Carries payment data only — never
 * Commission, Invoice or bank-reconciliation data.
 *
 * @param id the payment id
 * @param installmentId the settled installment id
 * @param installmentNumber the settled installment's 1-based number
 * @param amount the amount received
 * @param paymentDate the date the payment was received
 * @param paymentMethodId the payment method id
 * @param paymentMethodCode the payment method stable code
 * @param paymentMethodLabel the payment method display label
 * @param note optional free-text reference/note
 * @param registeredById the user who registered the payment
 * @param registeredByName the resolved name of the user who registered the payment
 * @param registeredAt when the payment was registered
 * @param reversed whether the payment has been reversed (kept in history, no longer counts towards the paid total)
 * @param reversalReason the reason recorded for the reversal, or {@code null} if not reversed
 * @param reversedById the user who reversed the payment, or {@code null} if not reversed
 * @param reversedByName the resolved name of the user who reversed the payment, or {@code null}
 * @param reversedAt when the payment was reversed, or {@code null} if not reversed
 */
public record PaymentView(
        UUID id,
        UUID installmentId,
        int installmentNumber,
        BigDecimal amount,
        LocalDate paymentDate,
        UUID paymentMethodId,
        String paymentMethodCode,
        String paymentMethodLabel,
        String note,
        UUID registeredById,
        String registeredByName,
        Instant registeredAt,
        boolean reversed,
        String reversalReason,
        UUID reversedById,
        String reversedByName,
        Instant reversedAt) {

    /**
     * Builds the view from the payment entity and the resolved cross-aggregate data.
     *
     * @param payment the payment entity
     * @param installmentNumber the settled installment's number
     * @param registeredByName the resolved registering-user name, or {@code null}
     * @param reversedByName the resolved reversing-user name, or {@code null} if not reversed
     * @return the read view
     */
    public static PaymentView from(
            ReceivablePayment payment, int installmentNumber, String registeredByName, String reversedByName) {
        PaymentMethod method = payment.method();
        return new PaymentView(
                payment.id(),
                payment.installmentId(),
                installmentNumber,
                payment.amount(),
                payment.paymentDate(),
                method.id(),
                method.code(),
                method.label(),
                payment.note(),
                payment.registeredBy(),
                registeredByName,
                payment.registeredAt(),
                payment.reversed(),
                payment.reversalReason(),
                payment.reversedBy(),
                reversedByName,
                payment.reversedAt());
    }
}
