package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalNotUnderReviewException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.model.ProposalStatusChange;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Domain invariants of the Proposal aggregate factory (created from a READY_FOR_PROPOSAL Opportunity). */
class ProposalTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    private Opportunity opportunity(OpportunityStage stage) {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn(stage);
        return o;
    }

    private CreateProposalCommand command() {
        return new CreateProposalCommand(
                OPP_ID, RESPONSIBLE, "Proposta corporativa", "anotação", LocalDate.parse("2026-12-31"), "termos");
    }

    @Test
    void createsFromReadyOpportunityAsDraft() {
        Opportunity o = opportunity(OpportunityStage.READY_FOR_PROPOSAL);
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);

        Proposal proposal = Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR);

        assertThat(proposal.status()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(proposal.isOpen()).isTrue();
        assertThat(proposal.opportunityId()).isEqualTo(OPP_ID);
        assertThat(proposal.leadId()).isEqualTo(LEAD_ID); // source Lead reference preserved
        assertThat(proposal.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(proposal.title()).isEqualTo("Proposta corporativa");
        assertThat(proposal.notes()).isEqualTo("anotação");
        assertThat(proposal.validUntil()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(proposal.commercialTerms()).isEqualTo("termos");
    }

    @ParameterizedTest
    @EnumSource(
            value = OpportunityStage.class,
            names = {"NEW_OPPORTUNITY", "DISCOVERY", "PRODUCT_FIT", "LOST"})
    void rejectsCreatingFromANonReadyOpportunity(OpportunityStage stage) {
        Opportunity o = opportunity(stage);

        assertThatThrownBy(() -> Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR))
                .isInstanceOf(OpportunityNotReadyForProposalException.class);
    }

    @Test
    void aFreshDraftHasNoStatusHistory() {
        assertThat(readyDraft().statusChanges()).isEmpty();
    }

    @Test
    void submitForReviewRecordsTheStatusChangeWithTheActor() {
        Proposal proposal = readyDraft();
        proposal.addItem(
                new ProposalItemCommand(ProposalItemType.OTHER, "linha", 1, new BigDecimal("10.00"), null, null),
                CREATOR);
        UUID submitter = UUID.randomUUID();

        proposal.submitForReview(submitter);

        assertThat(proposal.status()).isEqualTo(ProposalStatus.READY_FOR_REVIEW);
        assertThat(proposal.statusChanges()).hasSize(1);
        ProposalStatusChange change = proposal.statusChanges().get(0);
        assertThat(change.fromStatus()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(change.toStatus()).isEqualTo(ProposalStatus.READY_FOR_REVIEW);
        assertThat(change.changedBy()).isEqualTo(submitter);
        assertThat(change.changedAt()).isNotNull();
    }

    @Test
    void approveMovesAReviewedProposalToApprovedAndRecordsTheTransition() {
        Proposal p = submittedProposal();
        UUID approver = UUID.randomUUID();

        p.approve(approver);

        assertThat(p.status()).isEqualTo(ProposalStatus.APPROVED);
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.fromStatus()).isEqualTo(ProposalStatus.READY_FOR_REVIEW);
        assertThat(last.toStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(last.changedBy()).isEqualTo(approver);
    }

    @Test
    void rejectMovesAReviewedProposalToRejectedWithReasonAndRecordsTheTransition() {
        Proposal p = submittedProposal();
        UUID approver = UUID.randomUUID();

        p.reject(approver, ProposalRejectionReason.PRICE_TOO_HIGH, "acima do orçamento");

        assertThat(p.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.isOpen()).isFalse(); // terminal — frees the Opportunity for a new Proposal
        assertThat(p.rejectionReason()).isEqualTo(ProposalRejectionReason.PRICE_TOO_HIGH);
        assertThat(p.rejectionNote()).isEqualTo("acima do orçamento");
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.toStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(last.changedBy()).isEqualTo(approver);
    }

    @Test
    void rejectRequiresAReason() {
        Proposal p = submittedProposal();
        assertThatThrownBy(() -> p.reject(CREATOR, null, "sem motivo"))
                .isInstanceOf(ProposalRejectionReasonRequiredException.class);
    }

    @Test
    void approveAndRejectRequireReadyForReview() {
        Proposal draft = readyDraft(); // still a DRAFT (not submitted)
        assertThatThrownBy(() -> draft.approve(CREATOR)).isInstanceOf(ProposalNotUnderReviewException.class);
        assertThatThrownBy(() -> draft.reject(CREATOR, ProposalRejectionReason.OTHER, null))
                .isInstanceOf(ProposalNotUnderReviewException.class);
    }

    private Proposal submittedProposal() {
        Proposal p = readyDraft(); // command() already carries a validity date and a responsible
        p.addItem(
                new ProposalItemCommand(ProposalItemType.OTHER, "linha", 1, new BigDecimal("10.00"), null, null),
                CREATOR);
        p.submitForReview(CREATOR);
        return p;
    }

    private Proposal readyDraft() {
        Opportunity o = opportunity(OpportunityStage.READY_FOR_PROPOSAL);
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);
        return Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR);
    }
}
