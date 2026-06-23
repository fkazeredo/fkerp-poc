package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.sales.model.CommercialOrderCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Materializes the Customer when a Commercial Order is created (the deal is closed): the Lead graduates to a
 * Customer. Synchronous and atomic with the Order creation (same transaction, like the Order's booking-status
 * reflection listener); it is purely additive — the Order creation is unchanged, it only also guarantees a
 * Customer exists for the source Lead. The materialization is idempotent.
 */
@Component
@RequiredArgsConstructor
public class CustomerMaterializationListener {

    private final CustomerService customers;

    /**
     * Reacts to a Commercial Order creation by materializing (or reusing) the Customer for its source Lead.
     *
     * @param event the order-created fact (carries the source lead id and the creator)
     */
    @EventListener
    public void on(CommercialOrderCreated event) {
        customers.findOrCreateFromLead(event.leadId(), event.createdBy());
    }
}
