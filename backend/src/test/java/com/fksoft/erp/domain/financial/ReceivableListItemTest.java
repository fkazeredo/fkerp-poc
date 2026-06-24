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

/** Unit tests for the operational Receivable list item: outstanding amount and the overdue computation. */
class ReceivableListItemTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

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
        return r;
    }

    @Test
    void reportsZeroPaidFullOutstandingAndNoLastPaymentUntilThePaymentSlice() {
        ReceivableListItem item = ReceivableListItem.from(
                receivable(ReceivableStatus.OPEN, TODAY.plusDays(10)), 7, "Maria", null, null, TODAY);

        assertThat(item.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(item.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(item.outstandingAmount()).isEqualByComparingTo("1500.00");
        assertThat(item.lastPaymentDate()).isNull();
    }

    @Test
    void flagsAPastDueOpenReceivableAsOverdue() {
        ReceivableListItem item = ReceivableListItem.from(
                receivable(ReceivableStatus.OPEN, TODAY.minusDays(1)), 7, "Maria", null, null, TODAY);
        assertThat(item.overdue()).isTrue();
    }

    @Test
    void doesNotFlagAFutureDueReceivableAsOverdue() {
        ReceivableListItem item = ReceivableListItem.from(
                receivable(ReceivableStatus.OPEN, TODAY.plusDays(1)), 7, "Maria", null, null, TODAY);
        assertThat(item.overdue()).isFalse();
    }

    @Test
    void neverFlagsSettledReceivablesAsOverdueEvenWhenPastDue() {
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.PAID, TODAY.minusDays(5)), 7, "Maria", null, null, TODAY)
                        .overdue())
                .isFalse();
        assertThat(ReceivableListItem.from(
                                receivable(ReceivableStatus.CANCELLED, TODAY.minusDays(5)),
                                7,
                                "Maria",
                                null,
                                null,
                                TODAY)
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
