package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the Booking Request detail: an authorized user opens a reservation
 * and sees its summary, the source Commercial Order / Proposal / Opportunity / Lead (kept traceable) and the
 * booking items with their statuses (the per-item confirmation/failure signal) — and never Financial / Payment
 * / Commission data. Visibility is enforced (a user who cannot see the request gets 403, an unknown id gets
 * 404, callers without a booking read tier get 403, unauthenticated get 401). Data is seeded via JDBC.
 */
class BookingRequestDetailApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-000000000006");

    @Autowired
    private CommercialOrderRepository orders;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID originId;
    private int phoneSeq;

    @BeforeEach
    void seed() {
        wipe();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;
    }

    @AfterEach
    void cleanup() {
        wipe();
    }

    private void wipe() {
        jdbc.update("DELETE FROM booking_items");
        jdbc.update("DELETE FROM booking_requests");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void operationsUserOpensTheDetailWithTraceabilityAndItemStatuses() throws Exception {
        UUID request = seedRequest(
                "Contrato Acme", "PARTIALLY_CONFIRMED", MANAGER, OPERATOR, "Reservar com urgência", new String[][] {
                    {"TRAVEL_PACKAGE", "CONFIRMED"}, {"CAR_RENTAL", "FAILED"}, {"SERVICE_FEE", "NOT_REQUIRED"}
                });

        mvc.perform(get("/api/bookings/" + request).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.toString()))
                .andExpect(jsonPath("$.status").value("PARTIALLY_CONFIRMED"))
                .andExpect(jsonPath("$.commercialOrderNumber").isNumber())
                .andExpect(jsonPath("$.notes").value("Reservar com urgência"))
                // Counts: package + car require booking (fee does not); one confirmed, one failed.
                .andExpect(jsonPath("$.itemsRequiringBooking").value(2))
                .andExpect(jsonPath("$.itemsConfirmed").value(1))
                .andExpect(jsonPath("$.itemsFailed").value(1))
                // Source traceability.
                .andExpect(jsonPath("$.sourceOrder.number").isNumber())
                .andExpect(jsonPath("$.sourceProposal.title").value("Contrato Acme"))
                .andExpect(jsonPath("$.sourceOpportunity.name").value("Opp Contrato Acme"))
                .andExpect(jsonPath("$.sourceLead.name").value("Lead Contrato Acme"))
                // Items carry their status and the link to the commercial item; no monetary field.
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].status", org.hamcrest.Matchers.hasItem("CONFIRMED")))
                .andExpect(jsonPath("$.items[*].status", org.hamcrest.Matchers.hasItem("FAILED")))
                .andExpect(jsonPath("$.items[0].orderItemId").exists());
    }

    @Test
    void exposesOnlyOperationalFields() throws Exception {
        UUID request = seedRequest(
                "Contrato", "PENDING", MANAGER, OPERATOR, null, new String[][] {{"TRAVEL_PACKAGE", "PENDING"}});

        String body = mvc.perform(get("/api/bookings/" + request).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<String, Object> detail = JsonPath.read(body, "$");
        // The detail contract is exactly these fields — no Financial / Payment / Commission data.
        Assertions.assertThat(detail.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "commercialOrderNumber",
                        "status",
                        "bookingOperatorId",
                        "bookingOperatorName",
                        "operatorUnassigned",
                        "responsiblePersonId",
                        "responsibleName",
                        "notes",
                        "itemsRequiringBooking",
                        "itemsConfirmed",
                        "itemsFailed",
                        "createdAt",
                        "updatedAt",
                        "createdByName",
                        "items",
                        "attempts",
                        "sourceOrder",
                        "sourceProposal",
                        "sourceOpportunity",
                        "sourceLead");
        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        Assertions.assertThat(items.get(0).keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "orderItemId",
                        "type",
                        "description",
                        "quantity",
                        "requiresBooking",
                        "status",
                        "confirmation");
    }

    @Test
    void managerAndDirectorCanOpenTheDetail() throws Exception {
        UUID request = seedRequest(
                "Visivel", "PENDING", MANAGER, OPERATOR, null, new String[][] {{"TRAVEL_PACKAGE", "PENDING"}});
        for (String[] cred : new String[][] {{"comercial", "comercial123"}, {"diretor", "diretor123"}}) {
            mvc.perform(get("/api/bookings/" + request).header("Authorization", "Bearer " + login(cred[0], cred[1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(request.toString()));
        }
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mvc.perform(get("/api/bookings/" + UUID.randomUUID()).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.not-found"));
    }

    @Test
    void aUserWhoCannotSeeTheRequestIsForbidden() throws Exception {
        UUID request = seedRequest(
                "Hidden", "PENDING", MANAGER, OPERATOR, null, new String[][] {{"TRAVEL_PACKAGE", "PENDING"}});
        // Own-tier read scope only, for someone who is neither the operator nor the commercial responsible.
        String token = tokens.issueAccessToken(
                new AuthenticatedUser(UUID.randomUUID(), "other", Set.of("booking:request:read")));
        mvc.perform(get("/api/bookings/" + request).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("booking.access-denied"));
    }

    @Test
    void sellerWithoutABookingReadTierIsForbidden() throws Exception {
        UUID request =
                seedRequest("Any", "PENDING", MANAGER, OPERATOR, null, new String[][] {{"TRAVEL_PACKAGE", "PENDING"}});
        mvc.perform(get("/api/bookings/" + request)
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/bookings/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    /** Seeds a lead+opportunity+proposal+order+booking-request chain; returns the booking request id. */
    private UUID seedRequest(
            String title, String status, UUID responsibleId, UUID operatorId, String notes, String[][] items) {
        UUID lead = insertLead(title, responsibleId);
        UUID opp = insertOpportunity("Opp " + title, lead, responsibleId);
        UUID proposal = insertProposal(title, opp, lead, responsibleId);
        UUID order = insertOrder(proposal, opp, lead, responsibleId);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status, notes,
                                              created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), ?, ?, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                operatorId == null ? null : operatorId.toString(),
                status,
                notes,
                MANAGER.toString(),
                MANAGER.toString());
        for (String[] item : items) {
            String type = item[0];
            String itemStatus = item[1];
            boolean requires = !"SERVICE_FEE".equals(type) && !"NOT_REQUIRED".equals(itemStatus);
            jdbc.update(
                    """
                    INSERT INTO booking_items (id, booking_request_id, order_item_id, type, description,
                                               quantity, requires_booking, status, created_at)
                    VALUES (cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, 1, ?, ?, now())
                    """,
                    UUID.randomUUID().toString(),
                    requestId.toString(),
                    UUID.randomUUID().toString(),
                    type,
                    "Item " + type,
                    requires,
                    itemStatus);
        }
        return requestId;
    }

    private UUID insertOrder(UUID proposalId, UUID opportunityId, UUID leadId, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO commercial_orders (id, version, number, proposal_id, opportunity_id, lead_id,
                                               responsible_person_id, status, subtotal, total,
                                               created_by, updated_by)
                VALUES (cast(? as uuid), 0, nextval('commercial_order_number_seq'), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PENDING_BOOKING', 100.00, 100.00,
                        cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                proposalId.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertProposal(String title, UUID opportunityId, UUID leadId, UUID responsibleId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, 'ACCEPTED',
                        100.00, 100.00, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                title,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(String name, UUID leadId, UUID responsibleId) {
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
                OpportunityStage.WON.name(),
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

    private String operator() throws Exception {
        return login("operacoes", "operacoes123");
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
