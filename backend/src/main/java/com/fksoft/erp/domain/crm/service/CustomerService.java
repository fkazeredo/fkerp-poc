package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.exception.CustomerNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.service.data.CreateCustomerCommand;
import com.fksoft.erp.domain.crm.service.data.CustomerDetail;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for Customers (Customer Management — the commercial graduation of a Lead). It materializes a
 * Customer from its source Lead when a deal closes, lets a Customer Management user create/consolidate the profile
 * from a Commercial Order, and serves the customer read used to resolve the payer of a Receivable. It only
 * <b>reads</b> the Commercial Order (cross-context) and never creates or modifies Order, Booking, Receivable,
 * Payment, Commission or Customer Care data.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;
    private final LeadRepository leads;
    private final CommercialOrderRepository orders;

    /**
     * Returns the existing Customer for the Lead, or materializes a new one snapshotting the Lead's name and
     * contacts. Idempotent — one Lead yields at most one Customer.
     *
     * @param leadId the originating Lead id
     * @param createdBy id of the user (or system action) materializing the Customer
     * @return the existing or newly created Customer
     * @throws LeadNotFoundException if the Lead does not exist
     */
    @Transactional
    public Customer findOrCreateFromLead(UUID leadId, UUID createdBy) {
        return customers.findByLeadId(leadId).orElseGet(() -> {
            Lead lead = leads.findById(leadId).orElseThrow(LeadNotFoundException::new);
            return customers.save(Customer.fromLead(lead, createdBy));
        });
    }

    /**
     * Creates or consolidates a Customer Profile from a Commercial Order: finds (or materializes) the Customer for
     * the Order's source Lead and consolidates it with the supplied profile data, preserving the commercial origin
     * (source Order / Proposal / Opportunity). Idempotent — a customer already materialized for the Lead is enriched
     * in place, never duplicated. It only reads the Order and never creates a Customer Care Ticket nor alters the
     * Order, Booking, Receivable, Payment or Commission.
     *
     * @param command the create/consolidate data (source order id + the profile fields)
     * @param userId the acting user (Customer Management / commercial)
     * @return the consolidated customer read model (status {@code ACTIVE})
     * @throws CommercialOrderNotFoundException if the source Commercial Order does not exist
     * @throws LeadNotFoundException if the Order's source Lead does not exist
     */
    @Transactional
    public CustomerDetail createFromOrder(CreateCustomerCommand command, UUID userId) {
        CommercialOrder order =
                orders.findById(command.commercialOrderId()).orElseThrow(CommercialOrderNotFoundException::new);
        Customer customer = findOrCreateFromLead(order.leadId(), userId);
        customer.consolidateFromOrder(order, command, userId);
        return CustomerDetail.from(customers.save(customer));
    }

    /**
     * The Customer materialized from the given Lead.
     *
     * @param leadId the originating Lead id
     * @return the customer read model
     * @throws CustomerNotFoundException if no Customer was materialized for the Lead
     */
    @Transactional(readOnly = true)
    public CustomerDetail detailByLeadId(UUID leadId) {
        return customers.findByLeadId(leadId).map(CustomerDetail::from).orElseThrow(CustomerNotFoundException::new);
    }
}
