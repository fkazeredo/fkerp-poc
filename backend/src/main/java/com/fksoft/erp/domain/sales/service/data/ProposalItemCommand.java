package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import java.math.BigDecimal;

/**
 * Input for adding or updating a Proposal item. Built by the delivery layer after boundary validation;
 * the aggregate validates the discount consistency. The discount is optional — {@code discountType} and
 * {@code discountValue} are present together (absolute amount or percentage) or both {@code null}.
 *
 * @param type the item type (required)
 * @param description the line description (required)
 * @param quantity the quantity ({@code >= 1})
 * @param unitValue the unit value ({@code >= 0}, required)
 * @param discountType the discount type (AMOUNT/PERCENT) or {@code null}
 * @param discountValue the discount value or {@code null}
 */
public record ProposalItemCommand(
        ProposalItemType type,
        String description,
        int quantity,
        BigDecimal unitValue,
        DiscountType discountType,
        BigDecimal discountValue) {}
