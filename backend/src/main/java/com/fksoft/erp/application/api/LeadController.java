package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.LeadCreateRequest;
import com.fksoft.erp.application.api.dto.LeadListItemResponse;
import com.fksoft.erp.application.api.dto.LeadResponse;
import com.fksoft.erp.domain.crm.LeadListView;
import com.fksoft.erp.domain.crm.LeadSearchCriteria;
import com.fksoft.erp.domain.crm.LeadService;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.RegisterLeadCommand;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial / CRM lead endpoints. Creating a Lead requires {@code crm:lead:create}; listing
 * requires {@code crm:lead:read} (a regular user sees own + unassigned; {@code crm:lead:read:all}
 * sees every Lead).
 */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final UserContextProvider userContext;

    /**
     * Registers a new Lead (status NEW).
     *
     * @param request the lead data
     * @return 201 Created with the new lead
     */
    @PostMapping
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadCreateRequest request) {
        UUID createdBy = userContext.currentUserId();
        RegisterLeadCommand command = new RegisterLeadCommand(
                request.name(),
                request.phone(),
                request.whatsapp(),
                request.email(),
                request.originId(),
                request.responsiblePersonId(),
                request.initialNote());
        UUID id = leadService.register(command, createdBy);
        return ResponseEntity.created(URI.create("/api/leads/" + id))
                .body(new LeadResponse(id, request.name(), LeadStatus.NEW));
    }

    /**
     * Operational, paginated list of Leads visible to the caller, with optional filters and search.
     * Lost Leads are excluded unless the {@code status} filter explicitly includes LOST.
     *
     * @param status optional status filter (repeatable); empty means all non-lost
     * @param originId optional origin filter
     * @param responsible optional responsible filter: a user id, or the literal {@code unassigned}
     * @param createdFrom optional inclusive lower bound on the creation date (ISO date)
     * @param createdTo optional inclusive upper bound on the creation date (ISO date)
     * @param q optional case-insensitive search over name and contacts
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of operational Lead items
     */
    @GetMapping
    public PageResponse<LeadListItemResponse> list(
            @RequestParam(required = false) Set<LeadStatus> status,
            @RequestParam(required = false) UUID originId,
            @RequestParam(required = false) String responsible,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean unassignedOnly = "unassigned".equalsIgnoreCase(responsible);
        UUID responsibleId = (!unassignedOnly && responsible != null && !responsible.isBlank())
                ? UUID.fromString(responsible)
                : null;
        LeadSearchCriteria criteria = new LeadSearchCriteria(
                status,
                originId,
                responsibleId,
                unassignedOnly,
                createdFrom != null ? createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                createdTo != null
                        ? createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                        : null,
                q);
        Page<LeadListView> page = leadService.list(
                criteria, pageable, userContext.currentUserId(), userContext.hasScope("crm:lead:read:all"));
        return PageResponse.from(page, LeadListItemResponse::from);
    }
}
