package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of creating a Commercial Order from an Accepted Proposal: an authorized
 * commercial user (holding {@code sales:order:create}) creates the Order, which snapshots the Proposal's items
 * and total, preserves the source Proposal/Opportunity/Lead and the responsible, starts PENDING_BOOKING when it
 * has a bookable item (else BOOKING_NOT_REQUIRED), and closes the source Opportunity as WON. Only Accepted
 * Proposals can create an Order (else 422), a Proposal has at most one active Order (else 409), and callers
 * without the create authority get 403. Creating the Order creates no Booking, Receivable, Payment or Commission
 * data.
 */
class CommercialOrderApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private CommercialOrderRepository ordersRepo;

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

    @BeforeEach
    void seed() {
        ordersRepo.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
    }

    // This class runs first (alphabetically) and creates orders/proposals/opportunities/leads; clean them all
    // up (FK-safe order) so later test classes — which wipe those parents — are not blocked by the FKs on this
    // shared container.
    @AfterEach
    void cleanup() {
        ordersRepo.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void createsAnOrderFromAnAcceptedProposalPreservingEverythingAndWinningTheOpportunity() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Won", MANAGER);
        UUID opp = insertOpportunity("Won", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToAccepted(mgr, proposal, "TRAVEL_PACKAGE");

        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(created, "$.id");

        // The detail preserves the Proposal/Opportunity/responsible/items/total and starts PENDING_BOOKING.
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_BOOKING"))
                .andExpect(jsonPath("$.number").isNumber()) // the sequential PC-000n number
                .andExpect(jsonPath("$.proposalId").value(proposal.toString()))
                .andExpect(jsonPath("$.opportunityId").value(opp.toString()))
                .andExpect(jsonPath("$.responsibleId").value(MANAGER.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TRAVEL_PACKAGE"))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.sourceProposal.id").value(proposal.toString()))
                .andExpect(jsonPath("$.sourceOpportunity.stage").value("WON"));

        // The source Opportunity is now WON.
        mvc.perform(get("/api/opportunities/" + opp).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("WON"));

        // The accepted Proposal now exposes its active Commercial Order id.
        mvc.perform(get("/api/proposals/" + proposal).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commercialOrderId").value(orderId));
    }

    @Test
    void startsBookingNotRequiredWhenNoItemRequiresBooking() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Svc", MANAGER);
        UUID opp = insertOpportunity("Svc", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToAccepted(mgr, proposal, "SERVICE_FEE");

        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKING_NOT_REQUIRED"));
    }

    @Test
    void aSellerCreatesAnOrderFromTheirOwnAcceptedProposal() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Seller", MANAGER);
        UUID opp = insertOpportunity("Seller", lead);
        UUID proposal = insertProposal(opp, lead, SELLER);
        bringToAccepted(mgr, proposal, "CAR_RENTAL");

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated());
    }

    @Test
    void cannotCreateAnOrderFromANonAcceptedProposal() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Sent", MANAGER);
        UUID opp = insertOpportunity("Sent", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToSent(mgr, proposal, "TRAVEL_PACKAGE"); // SENT, not accepted

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-accepted"));
    }

    @Test
    void cannotCreateADuplicateActiveOrderForTheSameProposal() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Dup", MANAGER);
        UUID opp = insertOpportunity("Dup", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToAccepted(mgr, proposal, "TRAVEL_PACKAGE");

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order.already-exists"));
    }

    @Test
    void aDirectorConsultingCannotCreateAnOrderButCanReadIt() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Dir", MANAGER);
        UUID opp = insertOpportunity("Dir", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToAccepted(mgr, proposal, "TRAVEL_PACKAGE");

        // diretor has sales:order:read:all but no create.
        String diretor = login("diretor", "diretor123");
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isForbidden());

        // The manager creates it; the director may read it (read:all).
        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + diretor))
                .andExpect(status().isOk());
    }

    @Test
    void financeCannotCreateOrReadOrders() throws Exception {
        String mgr = manager();
        UUID lead = insertLead("Fin", MANAGER);
        UUID opp = insertOpportunity("Fin", lead);
        UUID proposal = insertProposal(opp, lead, MANAGER);
        bringToAccepted(mgr, proposal, "TRAVEL_PACKAGE");
        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(created, "$.id");

        String fin = login("financeiro", "financeiro123");
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + fin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedCreate() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    /** Brings the Proposal to ACCEPTED with an item of the given type. */
    private void bringToAccepted(String token, UUID proposal, String itemType) throws Exception {
        bringToSent(token, proposal, itemType);
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    /** Adds an item of the given type, sets validity, submits, approves and sends the Proposal (→ SENT). */
    private void bringToSent(String token, UUID proposal, String itemType) throws Exception {
        mvc.perform(post("/api/proposals/" + proposal + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}"
                                .formatted(itemType)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
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
