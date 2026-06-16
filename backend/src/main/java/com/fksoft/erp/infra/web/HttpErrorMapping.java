package com.fksoft.erp.infra.web;

import static java.util.Map.entry;

import com.fksoft.erp.domain.crm.DuplicateReferenceCodeException;
import com.fksoft.erp.domain.crm.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.LeadCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.LeadCannotBeQualifiedException;
import com.fksoft.erp.domain.crm.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.LeadNotFoundException;
import com.fksoft.erp.domain.crm.LeadQualificationRequiresResponsibleException;
import com.fksoft.erp.domain.crm.LossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.ReferenceNotFoundException;
import com.fksoft.erp.domain.crm.ResponsiblePersonNotFoundException;
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
            entry(ReferenceNotFoundException.class, HttpStatus.NOT_FOUND));

    public HttpStatus statusFor(DomainException ex) {
        return mappings.getOrDefault(ex.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public Set<Class<? extends DomainException>> mappedTypes() {
        return mappings.keySet();
    }
}
