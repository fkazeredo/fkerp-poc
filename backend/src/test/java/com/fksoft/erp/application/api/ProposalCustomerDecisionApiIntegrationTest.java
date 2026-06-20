package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
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
 * End-to-end (MockMvc, real Postgres) of registering the client's decision on a sent Proposal: an operator
 * (holding {@code sales:proposal:update}) accepts a Sent Proposal (→ ACCEPTED, with an optional confirmation
 * note) or rejects it on the client's behalf (→ REJECTED, with a required customer-rejection reason + an
 * optional note); each records the transition in the status history. Only Sent Proposals can be decided (else
 * 422), rejection requires a reason (else 400), and callers without the operate authority (director / finance)
 * get 403. Neither action creates Booking, Financial, Commission or Commercial Order data. An accepted Proposal
 * stays open (the Opportunity keeps it); a customer-rejected Proposal is terminal and frees the Opportunity.
 */
class ProposalCustomerDecisionApiIntegrationTest extends AbstractIntegrationTest {

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
    void registersClientAcceptanceWithAConfirmationNote() throws Exception {
        String mgr = manager();
        bringToSent(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Cliente confirmou por e-mail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptanceNote").value("Cliente confirmou por e-mail"))
                .andExpect(jsonPath("$.statusHistory[0].from").value("SENT"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("ACCEPTED"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"))
                .andExpect(jsonPath("$.statusHistory[0].at").value(notNullValue()))
                .andExpect(jsonPath("$.customerRejectionReason").doesNotExist());
    }

    @Test
    void registersClientAcceptanceWithoutANote() throws Exception {
        String mgr = manager();
        bringToSent(mgr, mgrProposal);

        // The confirmation note is optional.
        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptanceNote").doesNotExist());
    }

    @Test
    void registersClientRejectionWithAReason() throws Exception {
        String mgr = manager();
        bringToSent(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/decline")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CHOSE_COMPETITOR\",\"note\":\"Foi com a concorrência\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.customerRejectionReason").value("CHOSE_COMPETITOR"))
                .andExpect(jsonPath("$.customerRejectionNote").value("Foi com a concorrência"))
                .andExpect(jsonPath("$.statusHistory[0].from").value("SENT"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("REJECTED"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"));
    }

    @Test
    void decliningRequiresAReason() throws Exception {
        String mgr = manager();
        bringToSent(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/decline")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"sem motivo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("reason")));
    }

    @Test
    void cannotAcceptAProposalThatIsNotSent() throws Exception {
        // Bring it only to APPROVED (not sent).
        String mgr = manager();
        bringToApproved(mgr, mgrProposal);

        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-sent"));
    }

    @Test
    void cannotDeclineAProposalThatIsNotSent() throws Exception {
        // mgrProposal is still a DRAFT (never sent).
        mvc.perform(post("/api/proposals/" + mgrProposal + "/decline")
                        .header("Authorization", "Bearer " + manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-sent"));
    }

    @Test
    void aSellerDecidesTheirOwnSentProposal() throws Exception {
        // The seller operates the deal (sales:proposal:update) and owns it; the manager brings it to Sent.
        String mgr = manager();
        UUID lead = insertLead("Seller", MANAGER);
        UUID opp = insertOpportunity("Seller", lead);
        UUID proposal = insertProposal(opp, lead, SELLER);
        bringToSent(mgr, proposal);

        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void aDirectorConsultingCannotDecide() throws Exception {
        // diretor has read:all (consultation) but no update scope.
        bringToSent(manager(), mgrProposal);
        String diretor = login("diretor", "diretor123");

        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .header("Authorization", "Bearer " + diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/" + mgrProposal + "/decline")
                        .header("Authorization", "Bearer " + diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotDecide() throws Exception {
        bringToSent(manager(), mgrProposal);
        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .header("Authorization", "Bearer " + login("financeiro", "financeiro123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedDecision() throws Exception {
        mvc.perform(post("/api/proposals/" + mgrProposal + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAcceptedProposalStaysOpenSoTheOpportunityKeepsIt() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Won", MANAGER);
        UUID opp = insertOpportunity("Won", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToSent(mgr, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // ACCEPTED is non-terminal: the Opportunity still has an open (winning) Proposal → no new one.
        mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Outra\"}".formatted(opp)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("proposal.already-exists-for-opportunity"));
    }

    @Test
    void aCustomerRejectedProposalFreesTheOpportunityForANewProposal() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Lost", MANAGER);
        UUID opp = insertOpportunity("Lost", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToSent(mgr, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/decline")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"TRAVEL_CANCELLED\"}"))
                .andExpect(status().isOk());

        // The previous Proposal is terminal, so the Opportunity may originate a new one.
        mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Revisada\"}".formatted(opp)))
                .andExpect(status().isCreated());
    }

    /** Brings the Proposal to SENT: approve it then mark it as sent (both as the given operator token). */
    private void bringToSent(String token, UUID proposal) throws Exception {
        bringToApproved(token, proposal);
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    /** Brings the Proposal to APPROVED: review it then approve it (both as the given approver token). */
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
                OpportunityStage.READY_FOR_PROPOSAL.name(),
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
