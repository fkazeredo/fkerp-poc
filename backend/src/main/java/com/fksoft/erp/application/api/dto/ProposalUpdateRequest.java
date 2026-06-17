package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.DiscountType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to edit a Draft Proposal's commercial details. The Proposal-level discount is optional:
 * {@code discountType} and {@code discountValue} are present together (an absolute amount or a percentage)
 * or both absent; the aggregate validates their consistency (percentage 0–100, amount within the items
 * subtotal). Payment notes are descriptive free text only — never a Financial/Payment/Receivable record.
 *
 * @param validUntil the validity date (optional)
 * @param commercialTerms the commercial terms (optional)
 * @param paymentNotes descriptive payment notes (optional)
 * @param discountType the Proposal-level discount type (AMOUNT/PERCENT) or {@code null}
 * @param discountValue the Proposal-level discount value ({@code >= 0}) or {@code null}
 */
public record ProposalUpdateRequest(
        LocalDate validUntil,
        @Size(max = 4000) String commercialTerms,
        @Size(max = 4000) String paymentNotes,
        DiscountType discountType,
        @PositiveOrZero BigDecimal discountValue) {}
