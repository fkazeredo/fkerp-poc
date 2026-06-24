package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reflects the Financial Operations context's Receivable status onto the source Commercial Order, keeping the
 * Order <b>owned by Sales &amp; Proposals</b>: Financial publishes the business fact
 * ({@link ReceivableStatusChanged}) and this Sales-owned listener writes the Order's own {@code financialStatus}.
 * The reaction is synchronous, so it participates in the publisher's transaction and commits atomically with the
 * Receivable change (creation, payment or the overdue check). It never changes the Order's lifecycle, never
 * cancels the Order, and creates no Commission data.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommercialOrderFinancialStatusListener {

    private final CommercialOrderRepository orders;

    /**
     * Applies the Receivable's status to the source Commercial Order. A missing Order is ignored defensively (a
     * Receivable always references a real Order).
     *
     * @param event the receivable-status fact (carries the order id and the status code)
     */
    @EventListener
    public void on(ReceivableStatusChanged event) {
        orders.findById(event.commercialOrderId())
                .ifPresentOrElse(
                        order -> {
                            order.reflectFinancialStatus(event.status());
                            orders.save(order);
                        },
                        () -> log.warn(
                                "Financial status {} changed for unknown Commercial Order {}",
                                event.status(),
                                event.commercialOrderId()));
    }
}
