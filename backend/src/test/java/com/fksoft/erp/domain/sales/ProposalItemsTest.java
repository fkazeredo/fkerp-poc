package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException;
import com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Domain invariants of the Proposal items: line totals, the proposal total, the Draft guard and validation. */
class ProposalItemsTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private Proposal draftProposal() {
        Opportunity opportunity = mock(Opportunity.class);
        when(opportunity.stage()).thenReturn(OpportunityStage.READY_FOR_PROPOSAL);
        when(opportunity.id()).thenReturn(UUID.randomUUID());
        when(opportunity.leadId()).thenReturn(UUID.randomUUID());
        CreateProposalCommand command = new CreateProposalCommand(null, ACTOR, "Proposta", null, null, null);
        return Proposal.createFromOpportunity(opportunity, ACTOR, command, ACTOR);
    }

    private ProposalItemCommand item(
            ProposalItemType type, int quantity, String unitValue, DiscountType discountType, String discountValue) {
        return new ProposalItemCommand(
                type.id(),
                "linha",
                quantity,
                new BigDecimal(unitValue),
                discountType,
                discountValue == null ? null : new BigDecimal(discountValue));
    }

    @Test
    void addsItemsAndSumsTheProposalTotal() {
        Proposal p = draftProposal();
        p.addItem(
                ProposalItemTypeFixtures.TRAVEL_PACKAGE,
                item(ProposalItemTypeFixtures.TRAVEL_PACKAGE, 2, "100.00", null, null),
                ACTOR);
        p.addItem(
                ProposalItemTypeFixtures.SERVICE_FEE,
                item(ProposalItemTypeFixtures.SERVICE_FEE, 1, "50.00", null, null),
                ACTOR);

        assertThat(p.items()).hasSize(2);
        assertThat(p.items().get(0).lineTotal()).isEqualByComparingTo("200.00");
        assertThat(p.items().get(1).lineTotal()).isEqualByComparingTo("50.00");
        assertThat(p.total()).isEqualByComparingTo("250.00");
    }

    @Test
    void appliesAnAbsoluteDiscountToTheLineTotal() {
        Proposal p = draftProposal();
        p.addItem(
                ProposalItemTypeFixtures.CAR_RENTAL,
                item(ProposalItemTypeFixtures.CAR_RENTAL, 2, "100.00", DiscountType.AMOUNT, "30.00"),
                ACTOR);
        assertThat(p.items().get(0).lineTotal()).isEqualByComparingTo("170.00"); // 200 - 30
        assertThat(p.total()).isEqualByComparingTo("170.00");
    }

    @Test
    void appliesAPercentDiscountToTheLineTotal() {
        Proposal p = draftProposal();
        p.addItem(
                ProposalItemTypeFixtures.OTHER,
                item(ProposalItemTypeFixtures.OTHER, 2, "100.00", DiscountType.PERCENT, "10"),
                ACTOR);
        assertThat(p.items().get(0).lineTotal()).isEqualByComparingTo("180.00"); // 200 - 10%
        assertThat(p.total()).isEqualByComparingTo("180.00");
    }

    @Test
    void updatingAnItemRecomputesTheTotal() {
        Proposal p = draftProposal();
        p.addItem(
                ProposalItemTypeFixtures.TRAVEL_PACKAGE,
                item(ProposalItemTypeFixtures.TRAVEL_PACKAGE, 1, "100.00", null, null),
                ACTOR);
        UUID itemId = p.items().get(0).id();

        p.updateItem(
                itemId,
                ProposalItemTypeFixtures.TRAVEL_PACKAGE,
                item(ProposalItemTypeFixtures.TRAVEL_PACKAGE, 3, "100.00", null, null),
                ACTOR);

        assertThat(p.items().get(0).lineTotal()).isEqualByComparingTo("300.00");
        assertThat(p.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void removingAnItemRecomputesTheTotal() {
        Proposal p = draftProposal();
        p.addItem(
                ProposalItemTypeFixtures.TRAVEL_PACKAGE,
                item(ProposalItemTypeFixtures.TRAVEL_PACKAGE, 2, "100.00", null, null),
                ACTOR);
        p.addItem(
                ProposalItemTypeFixtures.SERVICE_FEE,
                item(ProposalItemTypeFixtures.SERVICE_FEE, 1, "50.00", null, null),
                ACTOR);
        UUID first = p.items().get(0).id();

        p.removeItem(first, ACTOR);

        assertThat(p.items()).hasSize(1);
        assertThat(p.total()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsAPercentDiscountAboveOneHundred() {
        Proposal p = draftProposal();
        assertThatThrownBy(() -> p.addItem(
                        ProposalItemTypeFixtures.OTHER,
                        item(ProposalItemTypeFixtures.OTHER, 1, "100.00", DiscountType.PERCENT, "150"),
                        ACTOR))
                .isInstanceOf(ProposalItemInvalidException.class);
    }

    @Test
    void rejectsAnAbsoluteDiscountAboveTheSubtotal() {
        Proposal p = draftProposal();
        assertThatThrownBy(() -> p.addItem(
                        ProposalItemTypeFixtures.OTHER,
                        item(ProposalItemTypeFixtures.OTHER, 1, "100.00", DiscountType.AMOUNT, "150.00"),
                        ACTOR))
                .isInstanceOf(ProposalItemInvalidException.class);
    }

    @Test
    void rejectsADiscountTypeWithoutAValue() {
        Proposal p = draftProposal();
        assertThatThrownBy(() -> p.addItem(
                        ProposalItemTypeFixtures.OTHER,
                        item(ProposalItemTypeFixtures.OTHER, 1, "100.00", DiscountType.AMOUNT, null),
                        ACTOR))
                .isInstanceOf(ProposalItemInvalidException.class);
    }

    @Test
    void rejectsUpdatingOrRemovingAnUnknownItem() {
        Proposal p = draftProposal();
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> p.updateItem(
                        unknown,
                        ProposalItemTypeFixtures.OTHER,
                        item(ProposalItemTypeFixtures.OTHER, 1, "10.00", null, null),
                        ACTOR))
                .isInstanceOf(ProposalItemNotFoundException.class);
        assertThatThrownBy(() -> p.removeItem(unknown, ACTOR)).isInstanceOf(ProposalItemNotFoundException.class);
    }

    @Test
    void rejectsEditingItemsWhenTheProposalIsNotADraft() {
        Proposal p = draftProposal();
        ReflectionTestUtils.setField(p, "status", ProposalStatus.SENT);
        assertThatThrownBy(() -> p.addItem(
                        ProposalItemTypeFixtures.OTHER,
                        item(ProposalItemTypeFixtures.OTHER, 1, "10.00", null, null),
                        ACTOR))
                .isInstanceOf(ProposalNotEditableException.class);
    }
}
