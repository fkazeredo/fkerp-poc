package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CreateCustomerRequest;
import com.fksoft.erp.domain.crm.service.CustomerService;
import com.fksoft.erp.domain.crm.service.data.CustomerDetail;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer endpoints (Customer Management). Creating/consolidating a Customer Profile from a Commercial Order
 * requires {@code customer:create} (held by the post-sale back-office and the commercial manager). The action only
 * reads the source Order; it never creates a Customer Care Ticket nor alters the Order, Booking, Receivable, Payment
 * or Commission. The customer list/detail (read tiers) are later slices.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final UserContextProvider userContext;

    /**
     * Creates or consolidates a Customer Profile from a Commercial Order, returning the consolidated profile.
     *
     * @param request the source order id plus the optional profile fields
     * @return the consolidated customer detail (status {@code ACTIVE})
     */
    @PostMapping
    public CustomerDetail create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.createFromOrder(request.toCommand(), userContext.currentUserId());
    }
}
