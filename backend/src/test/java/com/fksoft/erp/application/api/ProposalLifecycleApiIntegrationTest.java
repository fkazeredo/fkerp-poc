package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end (MockMvc, real Postgres) of the Proposal totals/discount/validity slice: editing a Draft
 * Proposal's commercial details (validity, terms, payment notes, Proposal-level discount) recomputes the
 * subtotal/total; the discount can never make the total negative; submitting for review requires items and a
 * positive total and moves the Proposal to READY_FOR_REVIEW. Editing and submitting are Draft-only and
 * visibility-gated. The contract carries commercial-offer data only — never Receivable/Payment/Booking.
 */
class ProposalLifecycleApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        UUID mgrOpp = insertOpportunity("Mgr", "READY_FOR_PROPOSAL", MANAGER, mgrLead);
        mgrProposal = insertProposal(mgrOpp, mgrLead, MANAGER);
    }

    @Test
    void editsCommercialDetailsAndAppliesAProposalDiscount() throws Exception {
        String mgr = manager();
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("TRAVEL_PACKAGE")
                        + "\",\"description\":\"Pacote\",\"quantity\":3,\"unitValue\":100.00}");

        String body = mvc.perform(
                        put("/api/proposals/" + mgrProposal)
                                .header("Authorization", "Bearer " + mgr)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"validUntil\":\"2026-12-31\",\"commercialTerms\":\"À vista\",\"paymentNotes\":\"50% na reserva\",\"discountType\":\"AMOUNT\",\"discountValue\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validUntil").value("2026-12-31"))
                .andExpect(jsonPath("$.commercialTerms").value("À vista"))
                .andExpect(jsonPath("$.paymentNotes").value("50% na reserva"))
                .andExpect(jsonPath("$.discountType").value("AMOUNT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.subtotal")).isEqualByComparingTo("300.00");
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.total")).isEqualByComparingTo("250.00");
    }

    @Test
    void rejectsAProposalDiscountAboveTheSubtotal() throws Exception {
        String mgr = manager();
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00}");

        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountType\":\"AMOUNT\",\"discountValue\":150.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.discount-invalid"));
    }

    @Test
    void submitsForReviewWhenItHasItemsAPositiveTotalValidityAndResponsible() throws Exception {
        String mgr = manager();
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}");
        // A validity date is required to submit (the proposal already has a responsible).
        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());

        // The submit response is the refreshed detail: the transition is recorded in the status history
        // (from → to, by whom) and the source Lead reference is present.
        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].from").value("DRAFT"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"))
                .andExpect(jsonPath("$.statusHistory[0].at").value(notNullValue()))
                .andExpect(jsonPath("$.sourceLead.name").value("Lead Mgr"))
                .andExpect(jsonPath("$.sourceLead.phone").value(notNullValue()));
    }

    @Test
    void rejectsSubmittingForReviewWithoutAValidityDate() throws Exception {
        String mgr = manager();
        // mgrProposal has a responsible (MANAGER) but no validity date.
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00}");

        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.validity-required"));
    }

    @Test
    void rejectsSubmittingForReviewWithoutAResponsible() throws Exception {
        String mgr = manager();
        // A Proposal with no responsible (created from an unassigned source), with item + validity set.
        UUID lead = insertLead("Unassigned", null);
        UUID opp = insertOpportunity("Unassigned", "READY_FOR_PROPOSAL", MANAGER, lead);
        UUID unassigned = insertProposal(opp, lead, null);
        addItem(
                mgr,
                unassigned,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00}");
        mvc.perform(put("/api/proposals/" + unassigned)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/proposals/" + unassigned + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.responsible-required"));
    }

    @Test
    void freshDraftDetailShowsTheSourceLeadAndAnEmptyStatusHistory() throws Exception {
        mvc.perform(get("/api/proposals/" + mgrProposal).header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.statusHistory.length()").value(0))
                .andExpect(jsonPath("$.sourceLead.name").value("Lead Mgr"))
                .andExpect(jsonPath("$.sourceLead.phone").value(notNullValue()));
    }

    @Test
    void rejectsSubmittingForReviewWithoutItems() throws Exception {
        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.no-items"));
    }

    @Test
    void rejectsSubmittingForReviewWhenTheTotalIsNotPositive() throws Exception {
        String mgr = manager();
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"grátis\",\"quantity\":1,\"unitValue\":0.00}");

        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.total-required"));
    }

    @Test
    void rejectsEditingOrSubmittingWhenTheProposalIsNotADraft() throws Exception {
        String mgr = manager();
        addItem(
                mgr,
                mgrProposal,
                "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                        + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00}");
        // Force the Proposal out of DRAFT: with the data-driven workflow, the transition graph is driven by
        // current_state_id (the FK), so move both the denormalized status and the FK to the SENT state.
        jdbc.update(
                """
                UPDATE proposals SET status = 'SENT', current_state_id = (
                    SELECT s.id FROM workflow_states s
                    JOIN workflow_definitions d ON d.id = s.definition_id
                    WHERE d.code = 'proposal' AND s.code = 'SENT')
                WHERE id = cast(? as uuid)
                """,
                mgrProposal.toString());

        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialTerms\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-editable"));
        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-editable"));
    }

    @Test
    void representativeCannotEditAnotherUsersProposal() throws Exception {
        String rep = login("representante", "representante123");
        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .header("Authorization", "Bearer " + rep)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialTerms\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("proposal.access-denied"));
    }

    @Test
    void financeHasNoAccessToEditOrSubmit() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialTerms\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultationOnlyBoardHasNoUpdateScope() throws Exception {
        String dir = login("diretor", "diretor123");
        mvc.perform(post("/api/proposals/" + mgrProposal + "/submit").header("Authorization", "Bearer " + dir))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(put("/api/proposals/" + mgrProposal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialTerms\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions addItem(String token, UUID proposalId, String body) throws Exception {
        return mvc.perform(post("/api/proposals/" + proposalId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private static BigDecimal decimal(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
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

    private UUID insertOpportunity(String name, String stage, UUID responsibleId, UUID leadId) {
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
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                stage,
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
