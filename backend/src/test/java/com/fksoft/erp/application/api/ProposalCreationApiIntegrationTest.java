package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end (MockMvc, real Postgres) of creating a commercial Proposal from a READY_FOR_PROPOSAL
 * Opportunity: only a ready Opportunity may originate one (earlier stages and LOST → 422), an Opportunity
 * has at most one open Proposal at a time (a second → 409; a new one is allowed once the previous is
 * terminal), the creator needs {@code sales:proposal:create} and must be able to see the source
 * Opportunity, the Proposal starts DRAFT linked to the Opportunity (and source Lead), and visibility
 * holds on the list/detail. The contract carries commercial-offer data only — never Sale/Order/Booking/
 * Financial. Opportunities (and their source leads) are inserted via JDBC to set up varied stages/owners.
 */
class ProposalCreationApiIntegrationTest extends AbstractIntegrationTest {

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
    private UUID readyMgr;
    private UUID discoveryMgr;
    private UUID lostMgr;
    private UUID readyRep;

    @BeforeEach
    void seed() {
        proposals.deleteAll(); // FK to opportunities/leads
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
        readyMgr = insertOpportunity("ReadyMgr", OpportunityStage.READY_FOR_PROPOSAL, MANAGER);
        discoveryMgr = insertOpportunity("DiscoveryMgr", OpportunityStage.DISCOVERY, MANAGER);
        lostMgr = insertOpportunity("LostMgr", OpportunityStage.LOST, MANAGER);
        readyRep = insertOpportunity("ReadyRep", OpportunityStage.READY_FOR_PROPOSAL, REPRESENTANTE);
    }

    @Test
    void createsProposalFromReadyOpportunityAsDraftLinkedToTheSource() throws Exception {
        String mgr = manager();
        String response = createProposal(mgr, readyMgr, "Proposta corporativa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(response, "$.id");

        mvc.perform(get("/api/proposals/" + id).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Proposta corporativa"))
                .andExpect(jsonPath("$.opportunityId").value(readyMgr.toString()))
                .andExpect(jsonPath("$.sourceOpportunity.id").value(readyMgr.toString()))
                .andExpect(jsonPath("$.sourceOpportunity.stage").value("READY_FOR_PROPOSAL"))
                .andExpect(jsonPath("$.leadId").value(notNullValue()))
                .andExpect(jsonPath("$.responsibleName").value("comercial"));
    }

    @Test
    void rejectsCreatingFromANonReadyOpportunity() throws Exception {
        createProposal(manager(), discoveryMgr, "X")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.opportunity-not-ready"));
    }

    @Test
    void rejectsCreatingFromALostOpportunity() throws Exception {
        createProposal(manager(), lostMgr, "X")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.opportunity-not-ready"));
    }

    @Test
    void rejectsASecondActiveProposalForTheSameOpportunity() throws Exception {
        String mgr = manager();
        String first = createProposal(mgr, readyMgr, "Primeira")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        createProposal(mgr, readyMgr, "Segunda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("proposal.already-exists-for-opportunity"))
                .andExpect(jsonPath("$.fields[?(@.field=='proposalId')].message", hasItem(firstId)));
    }

    @Test
    void allowsANewProposalAfterThePreviousIsCancelled() throws Exception {
        String mgr = manager();
        createProposal(mgr, readyMgr, "Primeira").andExpect(status().isCreated());
        // The previous Proposal reaches a terminal-negative outcome (simulated directly).
        jdbc.update(
                "UPDATE proposals SET status = 'CANCELLED' WHERE opportunity_id = cast(? as uuid)",
                readyMgr.toString());

        createProposal(mgr, readyMgr, "Revisada").andExpect(status().isCreated());
    }

    @Test
    void representativeCannotCreateFromAnotherUsersOpportunity() throws Exception {
        // The representative holds sales:proposal:create but cannot see the manager's Opportunity.
        String rep = login("representante", "representante123");
        createProposal(rep, readyMgr, "X")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));
    }

    @Test
    void financeHasNoAccessToCreate() throws Exception {
        String fin = login("financeiro", "financeiro123");
        createProposal(fin, readyMgr, "X").andExpect(status().isForbidden());
    }

    @Test
    void rejectsCreatingWithoutTheCreateScope() throws Exception {
        // diretor consults all (sales:proposal:read:all) but holds no sales:proposal:create scope.
        String dir = login("diretor", "diretor123");
        createProposal(dir, readyMgr, "X").andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedCreate() throws Exception {
        mvc.perform(post("/api/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"X\"}".formatted(readyMgr)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAndDetailRespectVisibility() throws Exception {
        String mgr = manager();
        String rep = login("representante", "representante123");
        String mgrProposal = JsonPath.read(
                createProposal(mgr, readyMgr, "Do gerente")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        String repProposal = JsonPath.read(
                createProposal(rep, readyRep, "Do representante")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        // Manager (read:all) sees both; the representative sees only their own.
        mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", hasItem("Do gerente")))
                .andExpect(jsonPath("$.content[*].title", hasItem("Do representante")));
        mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.content[*].title", hasItem("Do representante")))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Do gerente"))));

        // The representative cannot open the manager's Proposal; the manager can open the rep's.
        mvc.perform(get("/api/proposals/" + mgrProposal).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("proposal.access-denied"));
        mvc.perform(get("/api/proposals/" + repProposal).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk());

        // Finance has no access to the Proposal list.
        mvc.perform(get("/api/proposals").header("Authorization", "Bearer " + login("financeiro", "financeiro123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesOnlyCommercialOfferFields() throws Exception {
        String mgr = manager();
        String id = JsonPath.read(
                createProposal(mgr, readyMgr, "Contrato")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        String body = mvc.perform(get("/api/proposals/" + id).header("Authorization", "Bearer " + mgr))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<String, Object> detail = JsonPath.read(body, "$");
        // The contract is exactly these fields — no Sale / Sales Order / Booking / Financial / Commission.
        org.assertj.core.api.Assertions.assertThat(detail.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "opportunityId",
                        "leadId",
                        "status",
                        "responsibleId",
                        "responsibleName",
                        "unassigned",
                        "title",
                        "notes",
                        "validUntil",
                        "commercialTerms",
                        "createdAt",
                        "updatedAt",
                        "sourceOpportunity");
    }

    private ResultActions createProposal(String token, UUID opportunityId, String title) throws Exception {
        return mvc.perform(post("/api/proposals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opportunityId\":\"%s\",\"title\":\"%s\"}".formatted(opportunityId, title)));
    }

    private UUID insertOpportunity(String name, OpportunityStage stage, UUID responsibleId) {
        UUID leadId = insertLead(name, responsibleId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                stage.name(),
                stage == OpportunityStage.LOST ? "OTHER" : null,
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
