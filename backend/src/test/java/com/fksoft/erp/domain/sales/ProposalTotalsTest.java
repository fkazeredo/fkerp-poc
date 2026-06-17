package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.ProposalDiscountInvalidException;
import com.fksoft.erp.domain.sales.exception.ProposalHasNoItemsException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.exception.ProposalTotalRequiredException;
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

/** Domain invariants of the Proposal subtotal/discount/total and the submit-for-review transition. */
class ProposalTotalsTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private Proposal draftProposal() {
        Opportunity opportunity = mock(Opportunity.class);
        when(opportunity.stage()).thenReturn(OpportunityStage.READY_FOR_PROPOSAL);
        when(opportunity.id()).thenReturn(UUID.randomUUID());
        when(opportunity.leadId()).thenReturn(UUID.randomUUID());
        CreateProposalCommand command = new CreateProposalCommand(null, ACTOR, "Proposta", null, null, null);
        return Proposal.createFromOpportunity(opportunity, ACTOR, command, ACTOR);
    }

    private ProposalItemCommand item(String unitValue, int quantity) {
        return new ProposalItemCommand(
                ProposalItemType.TRAVEL_PACKAGE, "linha", quantity, new BigDecimal(unitValue), null, null);
    }

    private Proposal proposalWithSubtotal(String unitValue, int quantity) {
        Proposal p = draftProposal();
        p.addItem(item(unitValue, quantity), ACTOR);
        return p;
    }

    @Test
    void subtotalIsTheSumOfTheItemsAndTotalEqualsItWithoutDiscount() {
        Proposal p = draftProposal();
        p.addItem(item("100.00", 2), ACTOR); // 200
        p.addItem(item("50.00", 1), ACTOR); // 50

        assertThat(p.subtotal()).isEqualByComparingTo("250.00");
        assertThat(p.total()).isEqualByComparingTo("250.00");
        assertThat(p.discountType()).isNull();
    }

    @Test
    void appliesAnAbsoluteProposalDiscountToTheTotal() {
        Proposal p = proposalWithSubtotal("100.00", 3); // subtotal 300
        p.updateCommercialDetails(null, null, null, DiscountType.AMOUNT, new BigDecimal("50.00"), ACTOR);

        assertThat(p.subtotal()).isEqualByComparingTo("300.00");
        assertThat(p.total()).isEqualByComparingTo("250.00");
    }

    @Test
    void appliesAPercentProposalDiscountToTheTotal() {
        Proposal p = proposalWithSubtotal("100.00", 3); // subtotal 300
        p.updateCommercialDetails(null, null, null, DiscountType.PERCENT, new BigDecimal("10"), ACTOR);

        assertThat(p.total()).isEqualByComparingTo("270.00"); // 300 - 10%
    }

    @Test
    void storesPaymentNotesAndValidityWithoutCreatingFinancialData() {
        Proposal p = proposalWithSubtotal("100.00", 1);
        p.updateCommercialDetails(
                java.time.LocalDate.of(2026, 12, 31), "À vista", "50% na reserva, 50% no embarque", null, null, ACTOR);

        assertThat(p.paymentNotes()).isEqualTo("50% na reserva, 50% no embarque");
        assertThat(p.commercialTerms()).isEqualTo("À vista");
        assertThat(p.validUntil()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
    }

    @Test
    void rejectsAProposalDiscountAboveTheSubtotal() {
        Proposal p = proposalWithSubtotal("100.00", 1); // subtotal 100
        assertThatThrownBy(() -> p.updateCommercialDetails(
                        null, null, null, DiscountType.AMOUNT, new BigDecimal("150.00"), ACTOR))
                .isInstanceOf(ProposalDiscountInvalidException.class);
    }

    @Test
    void rejectsAProposalDiscountTypeWithoutAValue() {
        Proposal p = proposalWithSubtotal("100.00", 1);
        assertThatThrownBy(() -> p.updateCommercialDetails(null, null, null, DiscountType.AMOUNT, null, ACTOR))
                .isInstanceOf(ProposalDiscountInvalidException.class);
    }

    @Test
    void totalNeverGoesNegativeWhenItemsAreRemovedBelowAFixedDiscount() {
        Proposal p = draftProposal();
        p.addItem(item("100.00", 2), ACTOR); // subtotal 200
        UUID firstItem = p.items().get(0).id();
        p.updateCommercialDetails(null, null, null, DiscountType.AMOUNT, new BigDecimal("150.00"), ACTOR); // total 50
        assertThat(p.total()).isEqualByComparingTo("50.00");

        p.removeItem(firstItem, ACTOR); // subtotal now 0; the 150 discount is capped at the subtotal

        assertThat(p.subtotal()).isEqualByComparingTo("0.00");
        assertThat(p.total()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(p.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void submittingForReviewMovesADraftToReadyForReview() {
        Proposal p = proposalWithSubtotal("100.00", 1);
        p.submitForReview(ACTOR);
        assertThat(p.status()).isEqualTo(ProposalStatus.READY_FOR_REVIEW);
    }

    @Test
    void rejectsSubmittingForReviewWithoutItems() {
        Proposal p = draftProposal();
        assertThatThrownBy(() -> p.submitForReview(ACTOR)).isInstanceOf(ProposalHasNoItemsException.class);
    }

    @Test
    void rejectsSubmittingForReviewWhenTheTotalIsNotPositive() {
        Proposal p = proposalWithSubtotal("0.00", 1); // subtotal 0, total 0
        assertThatThrownBy(() -> p.submitForReview(ACTOR)).isInstanceOf(ProposalTotalRequiredException.class);
    }

    @Test
    void rejectsSubmittingOrEditingWhenTheProposalIsNotADraft() {
        Proposal p = proposalWithSubtotal("100.00", 1);
        ReflectionTestUtils.setField(p, "status", ProposalStatus.READY_FOR_REVIEW);

        assertThatThrownBy(() -> p.submitForReview(ACTOR)).isInstanceOf(ProposalNotEditableException.class);
        assertThatThrownBy(() -> p.updateCommercialDetails(null, null, null, null, null, ACTOR))
                .isInstanceOf(ProposalNotEditableException.class);
    }
}
