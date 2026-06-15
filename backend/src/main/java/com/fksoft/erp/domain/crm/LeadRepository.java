package com.fksoft.erp.domain.crm;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the {@link Lead} aggregate. */
public interface LeadRepository extends JpaRepository<Lead, UUID> {}
