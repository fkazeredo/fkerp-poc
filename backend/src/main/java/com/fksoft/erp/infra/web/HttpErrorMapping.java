package com.fksoft.erp.infra.web;

import static java.util.Map.entry;

import com.fksoft.erp.domain.crm.exception.DuplicateLeadException;
import com.fksoft.erp.domain.crm.exception.DuplicateReferenceCodeException;
import com.fksoft.erp.domain.crm.exception.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.exception.LeadCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.LeadCannotBeQualifiedException;
import com.fksoft.erp.domain.crm.exception.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.LeadQualificationRequiresResponsibleException;
import com.fksoft.erp.domain.crm.exception.LossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.exception.ReferenceNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.identity.InvalidCredentialsException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Registry mapping each {@link DomainException} type to its HTTP status. Keeping the mapping here
 * (presentation) keeps the domain free of transport concerns. A build-time test asserts every
 * domain exception subtype is mapped.
 */
@Component
public class HttpErrorMapping {

    private final Map<Class<? extends DomainException>, HttpStatus> mappings = Map.ofEntries(
            entry(LeadContactRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OriginNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(ResponsiblePersonNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadNotFoundException.class, HttpStatus.NOT_FOUND),
            entry(LeadAccessDeniedException.class, HttpStatus.FORBIDDEN),
            entry(LeadAssignmentNotAllowedException.class, HttpStatus.FORBIDDEN),
            entry(LeadCannotBeQualifiedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadQualificationRequiresResponsibleException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LeadCannotBeMarkedLostException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(LossReasonNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InteractionTypeNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InteractionResultNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED),
            entry(DuplicateReferenceCodeException.class, HttpStatus.CONFLICT),
            entry(DuplicateLeadException.class, HttpStatus.CONFLICT),
            entry(LeadNotQualifiedForOpportunityException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            entry(OpportunityAlreadyExistsForLeadException.class, HttpStatus.CONFLICT),
            entry(ReferenceNotFoundException.class, HttpStatus.NOT_FOUND));

    public HttpStatus statusFor(DomainException ex) {
        return mappings.getOrDefault(ex.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public Set<Class<? extends DomainException>> mappedTypes() {
        return mappings.keySet();
    }
}
