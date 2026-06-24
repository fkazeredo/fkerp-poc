package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableInstallment;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.ReceivableDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Receivable detail read model: outstanding amount, overdue and the resolved references. */
class ReceivableDetailTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    private Receivable receivable(ReceivableStatus status, LocalDate dueDate) {
        Receivable r = mock(Receivable.class);
        UUID id = UUID.randomUUID();
        lenient().when(r.id()).thenReturn(id);
        lenient().when(r.commercialOrderId()).thenReturn(UUID.randomUUID());
        lenient().when(r.proposalId()).thenReturn(UUID.randomUUID());
        lenient().when(r.opportunityId()).thenReturn(UUID.randomUUID());
        lenient().when(r.leadId()).thenReturn(UUID.randomUUID());
        lenient().when(r.customerId()).thenReturn(UUID.randomUUID());
        lenient().when(r.totalAmount()).thenReturn(new BigDecimal("1500.00"));
        lenient().when(r.status()).thenReturn(status);
        lenient().when(r.dueDate()).thenReturn(dueDate);
        lenient().when(r.paymentNotes()).thenReturn("boleto");
        lenient().when(r.commercialResponsiblePersonId()).thenReturn(null);
        lenient().when(r.financialResponsiblePersonId()).thenReturn(null);
        lenient().when(r.createdBy()).thenReturn(null);
        lenient().when(r.createdAt()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        lenient().when(r.installments()).thenReturn(List.<ReceivableInstallment>of());
        return r;
    }

    @Test
    void reportsZeroPaidFullOutstandingAndCarriesTheResolvedReferences() {
        ReceivableDetail detail = ReceivableDetail.from(
                receivable(ReceivableStatus.OPEN, TODAY.plusDays(10)),
                7,
                "Maria Silva",
                "Proposta Aurora",
                "Oportunidade Aurora",
                Map.of(),
                TODAY);

        assertThat(detail.orderNumber()).isEqualTo(7);
        assertThat(detail.customerName()).isEqualTo("Maria Silva");
        assertThat(detail.proposalReference()).isEqualTo("Proposta Aurora");
        assertThat(detail.opportunityReference()).isEqualTo("Oportunidade Aurora");
        assertThat(detail.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(detail.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(detail.outstandingAmount()).isEqualByComparingTo("1500.00");
        assertThat(detail.status()).isEqualTo("OPEN");
        assertThat(detail.paymentNotes()).isEqualTo("boleto");
        assertThat(detail.overdue()).isFalse();
        assertThat(detail.installments()).isEmpty();
    }

    @Test
    void flagsAPastDueOperationalReceivableAsOverdue() {
        assertThat(ReceivableDetail.from(
                                receivable(ReceivableStatus.OPEN, TODAY.minusDays(1)),
                                7,
                                "Maria",
                                null,
                                null,
                                Map.of(),
                                TODAY)
                        .overdue())
                .isTrue();
    }

    @Test
    void neverFlagsSettledReceivablesAsOverdue() {
        Receivable paid = mock(Receivable.class);
        when(paid.totalAmount()).thenReturn(new BigDecimal("10.00"));
        when(paid.status()).thenReturn(ReceivableStatus.PAID);
        when(paid.dueDate()).thenReturn(TODAY.minusDays(5));
        when(paid.installments()).thenReturn(List.of());
        ReceivableDetail detail = ReceivableDetail.from(paid, 1, null, null, null, Map.of(), TODAY);
        assertThat(detail.overdue()).isFalse();
    }
}
