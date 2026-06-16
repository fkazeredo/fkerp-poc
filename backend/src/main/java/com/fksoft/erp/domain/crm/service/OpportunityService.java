package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.dto.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityCreated;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service (command side) for commercial Opportunities (Commercial / CRM). Creates an
 * Opportunity from a QUALIFIED Lead, preserving the lead's origin, responsible and main interest.
 * Never creates a Proposal, Customer, Sale, Sales Order, Booking, Financial record or Commission, and
 * never modifies the source Lead. Reads (the operational list) live on the read side
 * ({@code application.read.OpportunityReadService}).
 */
@Service
@RequiredArgsConstructor
public class OpportunityService {

    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final UserRepository users;
    private final LeadAccessPolicy leadAccessPolicy;
    private final ApplicationEventPublisher events;

    /**
     * Creates an Opportunity from a Qualified Lead the caller is allowed to see. A Lead originates at
     * most one Opportunity. The responsible defaults to the lead's; an explicit one (when active)
     * overrides it.
     *
     * @param command the opportunity data (source lead + optional commercial estimates)
     * @param createdBy id of the authenticated user
     * @param canSeeAllLeads whether the caller may see every lead
     * @param canSeeUnassignedLeads whether the caller may also see the unassigned pool
     * @return the new opportunity id
     * @throws LeadNotFoundException if the source lead does not exist
     * @throws LeadAccessDeniedException if the caller may not see the source lead
     * @throws LeadNotQualifiedForOpportunityException if the lead is not QUALIFIED
     * @throws OpportunityAlreadyExistsForLeadException if the lead already originated an opportunity
     * @throws ResponsiblePersonNotFoundException if a responsible is given but unknown/inactive
     */
    @Transactional
    public UUID create(
            CreateOpportunityCommand command, UUID createdBy, boolean canSeeAllLeads, boolean canSeeUnassignedLeads) {
        Lead lead = leads.findById(command.leadId()).orElseThrow(LeadNotFoundException::new);
        if (!leadAccessPolicy.canSee(lead, createdBy, canSeeAllLeads, canSeeUnassignedLeads)) {
            throw new LeadAccessDeniedException();
        }
        if (lead.status() != LeadStatus.QUALIFIED) {
            throw new LeadNotQualifiedForOpportunityException();
        }
        opportunities.findByLeadId(lead.id()).ifPresent(existing -> {
            throw new OpportunityAlreadyExistsForLeadException(existing.id());
        });
        UUID responsibleId;
        if (command.responsiblePersonId() != null) {
            if (users.findById(command.responsiblePersonId())
                    .filter(User::active)
                    .isEmpty()) {
                throw new ResponsiblePersonNotFoundException();
            }
            responsibleId = command.responsiblePersonId();
        } else {
            // The lead's responsible is preserved by default (already validated when assigned).
            responsibleId = lead.responsiblePersonId();
        }
        Opportunity opportunity = Opportunity.createFromLead(lead, responsibleId, command, createdBy);
        opportunities.save(opportunity);
        events.publishEvent(new OpportunityCreated(opportunity.id(), lead.id(), createdBy, responsibleId));
        return opportunity.id();
    }
}
