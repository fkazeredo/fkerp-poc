package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.LeadCreateRequest;
import com.fksoft.erp.application.api.dto.LeadResponse;
import com.fksoft.erp.domain.crm.LeadService;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.RegisterLeadCommand;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Commercial / CRM lead-intake endpoints. Creating a Lead requires the {@code crm:lead:create} scope. */
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
}
