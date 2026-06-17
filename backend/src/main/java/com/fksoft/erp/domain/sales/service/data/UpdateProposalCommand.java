package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input for editing a Draft Proposal's commercial details. Built by the delivery layer after boundary
 * validation. Payment notes are descriptive free text only — never a Financial/Payment/Receivable record.
 *
 * @param validUntil the validity date (optional)
 * @param commercialTerms the commercial terms (optional)
 * @param paymentNotes descriptive payment notes (optional)
 * @param discountType the Proposal-level discount type, or {@code null} for no discount
 * @param discountValue the Proposal-level discount value, or {@code null} for no discount
 */
public record UpdateProposalCommand(
        LocalDate validUntil,
        String commercialTerms,
        String paymentNotes,
        DiscountType discountType,
        BigDecimal discountValue) {}
