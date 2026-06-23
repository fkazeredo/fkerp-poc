package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.exception.CustomerNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.service.data.CustomerDetail;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for Customers (the commercial graduation of a Lead). It materializes a Customer from
 * its source Lead (when a deal closes) and serves the customer read used to resolve the payer of a
 * Receivable. It never creates Receivable, Payment, Commission or Customer Care data.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;
    private final LeadRepository leads;

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
