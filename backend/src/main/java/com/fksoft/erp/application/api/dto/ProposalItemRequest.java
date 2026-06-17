package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request to add or update a Proposal item. The discount is optional: {@code discountType} and
 * {@code discountValue} are present together (an absolute amount or a percentage) or both absent; the
 * aggregate validates their consistency (percentage 0–100, amount within the line subtotal).
 *
 * @param type the item type (required)
 * @param description the line description (required)
 * @param quantity the quantity ({@code >= 1})
 * @param unitValue the unit value ({@code >= 0}, required)
 * @param discountType the discount type (AMOUNT/PERCENT) or {@code null}
 * @param discountValue the discount value ({@code >= 0}) or {@code null}
 */
public record ProposalItemRequest(
        @NotNull ProposalItemType type,
        @NotBlank @Size(max = 500) String description,
        @Min(1) int quantity,
        @NotNull @PositiveOrZero BigDecimal unitValue,
        DiscountType discountType,
        @PositiveOrZero BigDecimal discountValue) {}
