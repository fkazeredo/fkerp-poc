package com.fksoft.erp.infra.web;

import com.fksoft.erp.domain.crm.DuplicateReferenceCodeException;
import com.fksoft.erp.domain.crm.LeadContactRequiredException;
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

    private final Map<Class<? extends DomainException>, HttpStatus> mappings = Map.of(
            LeadContactRequiredException.class, HttpStatus.UNPROCESSABLE_ENTITY,
            OriginNotAvailableException.class, HttpStatus.UNPROCESSABLE_ENTITY,
            InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED,
            DuplicateReferenceCodeException.class, HttpStatus.CONFLICT,
            ReferenceNotFoundException.class, HttpStatus.NOT_FOUND,
            ResponsiblePersonNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY);

    public HttpStatus statusFor(DomainException ex) {
        return mappings.getOrDefault(ex.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public Set<Class<? extends DomainException>> mappedTypes() {
        return mappings.keySet();
    }
}
