package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the internal Proposal lifecycle transitions: an approver (the
 * Manager, holding {@code sales:proposal:approve}) approves a Proposal under review (→ APPROVED) or rejects it
 * with a reason (→ REJECTED, recording the reason/note); a commercial operator (holding
 * {@code sales:proposal:update}) then marks an approved Proposal as sent to the client (→ SENT, with an
 * optional channel). Each transition is recorded in the status history. Only Ready-for-Review Proposals can be
 * approved/rejected (else 422), rejection requires a reason (else 400), only Approved Proposals can be marked
 * sent (else 422), and callers without the relevant authority get 403. None of these actions create
 * Sale/Order/Booking/Financial/Commission data. Rejecting frees the Opportunity for a new Proposal; a SENT
 * Proposal stays open (so the Opportunity keeps it).
 */
class ProposalApprovalApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;
    private UUID mgrProposal;

    @BeforeEach
    void seed() {
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;

        UUID mgrLead = insertLead("Mgr", MANAGER);
        UUID mgrOpp = insertOpportunity("Mgr", mgrLead);
        mgrProposal = insertProposal(mgrOpp, mgrLead, MANAGER);
    }

    @Test
    void managerApprovesAProposalUnderReview() throws Exception {
        String mgr = manager();
        bringToReview(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusHistory[0].from").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("APPROVED"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void managerRejectsAProposalUnderReviewWithAReason() throws Exception {
        String mgr = manager();
        bringToReview(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/reject")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\",\"note\":\"Acima do orçamento do cliente\"}"
                                .formatted(refId("proposal_rejection_reasons", "PRICE_TOO_HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Preço muito alto"))
                .andExpect(jsonPath("$.rejectionNote").value("Acima do orçamento do cliente"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("REJECTED"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"))
                .andExpect(jsonPath("$.statusHistory[0].at").value(notNullValue()));
    }

    @Test
    void rejectingRequiresAReason() throws Exception {
        String mgr = manager();
        bringToReview(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/reject")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"sem motivo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("reasonId")));
    }

    @Test
    void cannotApproveAProposalThatIsNotUnderReview() throws Exception {
        // mgrProposal is still a DRAFT (never submitted).
        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-under-review"));
    }

    @Test
    void aSellerCannotApproveOrReject() throws Exception {
        bringToReview(manager(), mgrProposal);
        String seller = login("vendedor", "vendedor123");

        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/" + mgrProposal + "/reject")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRepresentativeCannotApprove() throws Exception {
        bringToReview(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve")
                        .header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDirectorConsultingCannotApprove() throws Exception {
        // diretor has read:all (consultation) but no approve scope.
        bringToReview(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve")
                        .header("Authorization", "Bearer " + login("diretor", "diretor123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotApprove() throws Exception {
        bringToReview(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve")
                        .header("Authorization", "Bearer " + login("financeiro", "financeiro123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedApprove() throws Exception {
        mvc.perform(post("/api/proposals/" + mgrProposal + "/approve")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectingFreesTheOpportunityForANewProposal() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Free", MANAGER);
        UUID opp = insertOpportunity("Free", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToReview(mgr, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/reject")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\"}".formatted(refId("proposal_rejection_reasons", "DUPLICATE"))))
                .andExpect(status().isOk());

        // The previous Proposal is terminal, so the Opportunity may originate a new one.
        mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Revisada\"}".formatted(opp)))
                .andExpect(status().isCreated());
    }

    @Test
    void managerMarksAnApprovedProposalAsSentWithChannel() throws Exception {
        String mgr = manager();
        bringToApproved(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelId\":\"%s\"}".formatted(refId("sending_channels", "EMAIL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sendingChannel").value("E-mail"))
                .andExpect(jsonPath("$.statusHistory[0].from").value("APPROVED"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("SENT"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"))
                .andExpect(jsonPath("$.statusHistory[0].at").value(notNullValue()));
    }

    @Test
    void marksAnApprovedProposalAsSentWithoutAChannel() throws Exception {
        String mgr = manager();
        bringToApproved(mgr, mgrProposal);

        // The channel is optional.
        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sendingChannel").doesNotExist());
    }

    @Test
    void aSellerMarksTheirOwnApprovedProposalAsSent() throws Exception {
        // The seller operates the deal (sales:proposal:update) and owns it; the manager approves it first.
        String mgr = manager();
        UUID lead = insertLead("Seller", MANAGER);
        UUID opp = insertOpportunity("Seller", lead);
        UUID proposal = insertProposal(opp, lead, SELLER);
        bringToApproved(mgr, proposal);

        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelId\":\"%s\"}".formatted(refId("sending_channels", "WHATSAPP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sendingChannel").value("WhatsApp"));
    }

    @Test
    void cannotMarkAsSentAProposalThatIsNotApproved() throws Exception {
        // mgrProposal is still a DRAFT (never approved).
        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-approved"));
    }

    @Test
    void cannotMarkAsSentAProposalUnderReview() throws Exception {
        String mgr = manager();
        bringToReview(mgr, mgrProposal); // READY_FOR_REVIEW, not yet approved

        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-approved"));
    }

    @Test
    void aSentProposalStaysOpenSoTheOpportunityKeepsIt() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Open", MANAGER);
        UUID opp = insertOpportunity("Open", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToApproved(mgr, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // SENT is non-terminal: the Opportunity still has an open Proposal → it cannot originate a new one.
        mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Outra\"}".formatted(opp)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("proposal.already-exists-for-opportunity"));
    }

    @Test
    void aDirectorConsultingCannotMarkAsSent() throws Exception {
        // diretor has read:all (consultation) but no update scope.
        bringToApproved(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + login("diretor", "diretor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotMarkAsSent() throws Exception {
        bringToApproved(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .header("Authorization", "Bearer " + login("financeiro", "financeiro123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedSend() throws Exception {
        mvc.perform(post("/api/proposals/" + mgrProposal + "/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    /** Brings the Proposal to APPROVED: reviews it then approves it (both as the given approver token). */
    private void bringToApproved(String token, UUID proposal) throws Exception {
        bringToReview(token, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    /** Adds an item, sets a validity date, and submits the Proposal so it reaches READY_FOR_REVIEW. */
    private void bringToReview(String token, UUID proposal) throws Exception {
        mvc.perform(post("/api/proposals/" + proposal + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, 'DRAFT',
                        cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Proposta " + opportunityId,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(String name, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                MANAGER.toString(),
                "Pacote " + name,
                "READY_FOR_PROPOSAL",
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(phoneSeq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), ?, ?, NULL, NULL, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                "Lead " + name,
                phone,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private String manager() throws Exception {
        return login("comercial", "comercial123");
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
