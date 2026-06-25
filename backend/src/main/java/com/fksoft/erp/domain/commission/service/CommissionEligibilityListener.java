package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Makes an Expected Commission <b>eligible</b> when the related Receivable becomes fully <b>paid</b>, keeping the
 * Receivable <b>owned by Financial Operations</b>: Financial publishes the business fact
 * ({@link ReceivableStatusChanged}) and this Commission-owned listener <b>consumes</b> it. It reacts <b>only</b> to
 * the {@code PAID} status (a {@code PARTIALLY_PAID}/{@code OPEN}/{@code OVERDUE} receivable does not make the
 * commission eligible — there is no partial eligibility). The reaction is synchronous, so it participates in the
 * publisher's transaction and commits atomically with the payment; it is a no-op when the Order has no commission yet
 * (so the Financial-only flows that create no commission are unaffected). It never approves, pays, or creates any
 * Payment, Accounts Payable, payroll, tax or accounting data.
 *
 * <p>A payment reversal republishes a non-{@code PAID} status, which this listener intentionally ignores: an already
 * eligible commission is <b>not</b> regressed (no clawback / reversal-reaction automation — out of scope).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommissionEligibilityListener {

    private final CommissionRepository commissions;

    /**
     * Transitions the Order's Expected Commission to Eligible when its Receivable is paid. Ignores any other status.
     *
     * @param event the receivable-status fact (carries the order id, the receivable id and the status code)
     */
    @EventListener
    public void on(ReceivableStatusChanged event) {
        if (!ReceivableStatus.PAID.name().equals(event.status())) {
            return;
        }
        commissions
                .findFirstByCommercialOrderIdAndStatusIn(event.commercialOrderId(), Set.of(CommissionStatus.EXPECTED))
                .filter(commission -> commission.markEligible(event.receivableId(), Instant.now()))
                .ifPresent(commissions::save);
    }
}
