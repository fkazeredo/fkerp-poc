package com.fksoft.erp.domain.crm.repository;

import com.fksoft.erp.domain.crm.model.Customer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the {@link Customer} aggregate (the commercial graduation of a Lead). */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * The Customer materialized from the given Lead, if any (a Lead originates at most one Customer).
     *
     * @param leadId the originating Lead id
     * @return the customer, or empty when the Lead has not been graduated yet
     */
    Optional<Customer> findByLeadId(UUID leadId);

    /**
     * Whether a Customer already exists for the given Lead.
     *
     * @param leadId the originating Lead id
     * @return {@code true} if a Customer was already materialized for the Lead
     */
    boolean existsByLeadId(UUID leadId);

    /**
     * The Customers materialized from the given Leads (batch resolution of payer names, no N+1).
     *
     * @param leadIds the originating Lead ids
     * @return the matching customers
     */
    List<Customer> findByLeadIdIn(Collection<UUID> leadIds);
}
