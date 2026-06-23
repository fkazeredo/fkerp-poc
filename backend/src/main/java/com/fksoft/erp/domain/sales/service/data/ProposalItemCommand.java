package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input for adding or updating a Proposal item. Built by the delivery layer after boundary validation; the
 * service resolves {@code typeId} to an active item-type cadastro value and the aggregate validates the
 * discount consistency. The discount is optional — {@code discountType} and {@code discountValue} are present
 * together (absolute amount or percentage) or both {@code null}.
 *
 * @param typeId the item-type cadastro id (required)
 * @param description the line description (required)
 * @param quantity the quantity ({@code >= 1})
 * @param unitValue the unit value ({@code >= 0}, required)
 * @param discountType the discount type (AMOUNT/PERCENT) or {@code null}
 * @param discountValue the discount value or {@code null}
 */
public record ProposalItemCommand(
        UUID typeId,
        String description,
        int quantity,
        BigDecimal unitValue,
        DiscountType discountType,
        BigDecimal discountValue) {}
