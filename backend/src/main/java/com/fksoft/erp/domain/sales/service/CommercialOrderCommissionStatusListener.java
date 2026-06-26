package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionStatusChanged;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reflects the Commission Management context's commission status summary onto the source Commercial Order, keeping the
 * Order <b>owned by Sales &amp; Proposals</b>: Commission publishes the business fact
 * ({@link CommissionStatusChanged}) and this Sales-owned listener writes the Order's own {@code commissionStatus}. The
 * reaction is synchronous, so it participates in the publisher's transaction and commits atomically with the commission
 * change (generation, eligibility, approval, rejection, cancellation or payment). It never changes the Order's
 * lifecycle, never cancels the Order, and creates no Receivable, Payment, payroll, tax or accounting data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommercialOrderCommissionStatusListener {

    private final CommercialOrderRepository orders;

    /**
     * Applies the commission status summary to the source Commercial Order. A missing Order is ignored defensively (a
     * Commission always references a real Order).
     *
     * @param event the commission-status fact (carries the order id and the raw commission status code)
     */
    @EventListener
    public void on(CommissionStatusChanged event) {
        orders.findById(event.commercialOrderId())
                .ifPresentOrElse(
                        order -> {
                            order.reflectCommissionStatus(summaryOf(event.status()));
                            orders.save(order);
                        },
                        () -> log.warn(
                                "Commission status {} changed for unknown Commercial Order {}",
                                event.status(),
                                event.commercialOrderId()));
    }

    /**
     * Maps the raw commission status to the Order's summary: a voided commission (Rejected/Cancelled) is shown as an
     * {@code ISSUE} (the order had a commission that was voided and needs attention); the active lifecycle statuses
     * (Expected/Eligible/Approved/Paid) are reflected as-is.
     */
    private static String summaryOf(String status) {
        return CommissionStatus.REJECTED.name().equals(status)
                        || CommissionStatus.CANCELLED.name().equals(status)
                ? "ISSUE"
                : status;
    }
}
