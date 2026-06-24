package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.ReceivableListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the operational Receivable list item: outstanding amount and the status-based overdue flag. */
class ReceivableListItemTest {

    private static final LocalDate REF_DUE = LocalDate.of(2026, 7, 15);

    private Receivable receivable(ReceivableStatus status, LocalDate dueDate) {
        Receivable r = mock(Receivable.class);
        lenient().when(r.id()).thenReturn(UUID.randomUUID());
        lenient().when(r.commercialOrderId()).thenReturn(UUID.randomUUID());
        lenient().when(r.customerId()).thenReturn(UUID.randomUUID());
        lenient().when(r.totalAmount()).thenReturn(new BigDecimal("1500.00"));
        lenient().when(r.status()).thenReturn(status);
        lenient().when(r.dueDate()).thenReturn(dueDate);
        lenient().when(r.commercialResponsiblePersonId()).thenReturn(null);
        lenient().when(r.financialResponsiblePersonId()).thenReturn(null);
        lenient().when(r.createdAt()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        lenient().when(r.amountPaid()).thenReturn(BigDecimal.ZERO);
        lenient().when(r.lastPaymentDate()).thenReturn(null);
        return r;
    }

    @Test
    void reportsZeroPaidFullOutstandingAndNoLastPaymentUntilThePaymentSlice() {
        ReceivableListItem item = ReceivableListItem.from(
                receivable(ReceivableStatus.OPEN, REF_DUE.plusDays(10)), 7, "Maria", null, null);

        assertThat(item.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(item.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(item.outstandingAmount()).isEqualByComparingTo("1500.00");
        assertThat(item.lastPaymentDate()).isNull();
    }

    @Test
    void flagsAnOverdueStatusReceivableAsOverdue() {
        ReceivableListItem item = ReceivableListItem.from(
                receivable(ReceivableStatus.OVERDUE, REF_DUE.minusDays(1)), 7, "Maria", null, null);
        assertThat(item.overdue()).isTrue();
    }

    @Test
    void doesNotFlagOperationalNonOverdueReceivablesEvenWhenPastTheReferenceDate() {
        // Overdue is the stored OVERDUE status (the daily job's authoritative, per-installment result), not the
        // single reference due date — so an OPEN/PARTIALLY_PAID receivable is not overdue until the job flags it.
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.OPEN, REF_DUE.minusDays(5)), 7, "Maria", null, null)
                        .overdue())
                .isFalse();
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.PARTIALLY_PAID, REF_DUE.minusDays(5)),
                                7,
                                "Maria",
                                null,
                                null)
                        .overdue())
                .isFalse();
    }

    @Test
    void neverFlagsSettledReceivablesAsOverdue() {
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.PAID, REF_DUE.minusDays(5)), 7, "Maria", null, null)
                        .overdue())
                .isFalse();
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.CANCELLED, REF_DUE.minusDays(5)), 7, "Maria", null, null)
                        .overdue())
                .isFalse();
    }

    @Test
    void operationalStatusesExcludeTheSettledOutcomes() {
        assertThat(ReceivableStatus.operational())
                .containsExactlyInAnyOrder(
                        ReceivableStatus.OPEN, ReceivableStatus.PARTIALLY_PAID, ReceivableStatus.OVERDUE)
                .doesNotContain(ReceivableStatus.PAID, ReceivableStatus.CANCELLED);
    }
}
