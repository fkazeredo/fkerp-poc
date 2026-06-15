package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.ResponsibleResponse;
import com.fksoft.erp.domain.crm.LeadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoint exposing users assignable as a Lead responsible (requires {@code crm:lead:read}). */
@RestController
@RequestMapping("/api/crm/responsibles")
@RequiredArgsConstructor
public class ResponsibleController {

    private final LeadService leadService;

    /**
     * Lists active users that can be set as a Lead responsible.
     *
     * @return the responsibles (id + name)
     */
    @GetMapping
    public List<ResponsibleResponse> list() {
        return leadService.listResponsibles().stream()
                .map(ResponsibleResponse::from)
                .toList();
    }
}
