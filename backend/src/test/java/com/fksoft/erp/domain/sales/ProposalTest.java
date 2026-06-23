package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException;
import com.fksoft.erp.domain.sales.model.CustomerRejectionReason;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.model.ProposalStatusChange;
import com.fksoft.erp.domain.sales.model.SendingChannel;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Domain invariants of the Proposal aggregate. The lifecycle is a fixed enum state machine with pre-defined
 * transitions enforced on the entity ({@code apply*} methods): submit (DRAFT→READY_FOR_REVIEW, needs items/
 * total/validity/responsible), approve, reject (needs a reason), send, accept, decline. Covers the
 * transitions, their guards and the status history.
 */
class ProposalTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    private final ProposalRejectionReason priceTooHigh =
            ProposalRejectionReason.create("PRICE_TOO_HIGH", "Preço muito alto", 1);
    private final CustomerRejectionReason choseCompetitor =
            CustomerRejectionReason.create("CHOSE_COMPETITOR", "Escolheu concorrente", 1);
    private final SendingChannel email = SendingChannel.create("EMAIL", "E-mail", 1);

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
    void submitRecordsTheStatusChangeWithTheActor() {
        Proposal proposal = readyDraft();
        proposal.addItem(
                ProposalItemTypeFixtures.OTHER,
                new ProposalItemCommand(
                        ProposalItemTypeFixtures.OTHER.id(), "linha", 1, new BigDecimal("10.00"), null, null),
                CREATOR);
        UUID submitter = UUID.randomUUID();

        proposal.applySubmit(submitter);

        assertThat(proposal.status()).isEqualTo(ProposalStatus.READY_FOR_REVIEW);
        assertThat(proposal.statusChanges()).hasSize(1);
        ProposalStatusChange change = proposal.statusChanges().get(0);
        assertThat(change.fromStatus()).isEqualTo("DRAFT");
        assertThat(change.toStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(change.changedBy()).isEqualTo(submitter);
        assertThat(change.changedAt()).isNotNull();
    }

    @Test
    void approveMovesAReviewedProposalToApprovedAndRecordsTheTransition() {
        Proposal p = submittedProposal();
        UUID approver = UUID.randomUUID();

        p.applyApprove(approver);

        assertThat(p.status()).isEqualTo(ProposalStatus.APPROVED);
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.fromStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(last.toStatus()).isEqualTo("APPROVED");
        assertThat(last.changedBy()).isEqualTo(approver);
    }

    @Test
    void rejectMovesAReviewedProposalToRejectedWithReasonAndRecordsTheTransition() {
        Proposal p = submittedProposal();
        UUID approver = UUID.randomUUID();

        p.applyReject(approver, priceTooHigh, "acima do orçamento");

        assertThat(p.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.isOpen()).isFalse(); // terminal — frees the Opportunity for a new Proposal
        assertThat(p.rejectionReason()).isEqualTo(priceTooHigh);
        assertThat(p.rejectionNote()).isEqualTo("acima do orçamento");
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.toStatus()).isEqualTo("REJECTED");
        assertThat(last.changedBy()).isEqualTo(approver);
    }

    @Test
    void rejectRequiresAReason() {
        Proposal p = submittedProposal();
        assertThatThrownBy(() -> p.applyReject(CREATOR, null, "sem motivo"))
                .isInstanceOf(ProposalRejectionReasonRequiredException.class);
    }

    @Test
    void markAsSentMovesAnApprovedProposalToSentWithChannelAndRecordsTheTransition() {
        Proposal p = approvedProposal();
        UUID sender = UUID.randomUUID();

        p.applySend(sender, email);

        assertThat(p.status()).isEqualTo(ProposalStatus.SENT);
        assertThat(p.isOpen()).isTrue(); // stays open for the client's decision
        assertThat(p.sendingChannel()).isEqualTo(email);
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.fromStatus()).isEqualTo("APPROVED");
        assertThat(last.toStatus()).isEqualTo("SENT");
        assertThat(last.changedBy()).isEqualTo(sender);
        assertThat(last.changedAt()).isNotNull();
    }

    @Test
    void markAsSentAcceptsANullChannel() {
        Proposal p = approvedProposal();

        p.applySend(CREATOR, null); // the channel is optional

        assertThat(p.status()).isEqualTo(ProposalStatus.SENT);
        assertThat(p.sendingChannel()).isNull();
    }

    @Test
    void acceptByCustomerMovesASentProposalToAcceptedWithNoteAndStaysOpen() {
        Proposal p = sentProposal();
        UUID register = UUID.randomUUID();

        p.applyAccept(register, "Cliente confirmou por e-mail");

        assertThat(p.status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.isOpen()).isTrue(); // the winning offer keeps the Opportunity; prepares the Order
        assertThat(p.acceptanceNote()).isEqualTo("Cliente confirmou por e-mail");
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.fromStatus()).isEqualTo("SENT");
        assertThat(last.toStatus()).isEqualTo("ACCEPTED");
        assertThat(last.changedBy()).isEqualTo(register);
        assertThat(last.changedAt()).isNotNull();
    }

    @Test
    void acceptByCustomerAcceptsANullNote() {
        Proposal p = sentProposal();

        p.applyAccept(CREATOR, null); // the confirmation note is optional

        assertThat(p.status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.acceptanceNote()).isNull();
    }

    @Test
    void declineByCustomerMovesASentProposalToRejectedWithReasonAndIsTerminal() {
        Proposal p = sentProposal();
        UUID register = UUID.randomUUID();

        p.applyDecline(register, choseCompetitor, "foi com a concorrência");

        assertThat(p.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.isOpen()).isFalse(); // terminal — frees the Opportunity for a new Proposal
        assertThat(p.customerRejectionReason()).isEqualTo(choseCompetitor);
        assertThat(p.customerRejectionNote()).isEqualTo("foi com a concorrência");
        ProposalStatusChange last = p.statusChanges().get(p.statusChanges().size() - 1);
        assertThat(last.fromStatus()).isEqualTo("SENT");
        assertThat(last.toStatus()).isEqualTo("REJECTED");
        assertThat(last.changedBy()).isEqualTo(register);
    }

    @Test
    void declineByCustomerRequiresAReason() {
        Proposal p = sentProposal();
        assertThatThrownBy(() -> p.applyDecline(CREATOR, null, "sem motivo"))
                .isInstanceOf(ProposalRejectionReasonRequiredException.class);
    }

    private Proposal sentProposal() {
        Proposal p = approvedProposal();
        p.applySend(UUID.randomUUID(), email);
        return p;
    }

    private Proposal approvedProposal() {
        Proposal p = submittedProposal();
        p.applyApprove(UUID.randomUUID());
        return p;
    }

    private Proposal submittedProposal() {
        Proposal p = readyDraft(); // command() already carries a validity date and a responsible
        p.addItem(
                ProposalItemTypeFixtures.OTHER,
                new ProposalItemCommand(
                        ProposalItemTypeFixtures.OTHER.id(), "linha", 1, new BigDecimal("10.00"), null, null),
                CREATOR);
        p.applySubmit(CREATOR);
        return p;
    }

    private Proposal readyDraft() {
        Opportunity o = opportunity(OpportunityStage.READY_FOR_PROPOSAL);
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);
        return Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR);
    }
}
