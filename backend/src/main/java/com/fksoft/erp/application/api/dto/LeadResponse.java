package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.LeadStatus;
import java.util.UUID;

/**
 * Created-lead response.
 *
 * @param id the new lead id
 * @param name the lead name
 * @param status the lead status (always NEW on creation)
 */
public record LeadResponse(UUID id, String name, LeadStatus status) {}
