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
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Booking Request list: it carries the operational
 * columns (no Financial / Payment / Commission data), respects the read tiers (the operations user, manager
 * and director list; sellers/representatives get 403), excludes the terminal CONFIRMED + CANCELLED requests by
 * default while keeping FAILED visible, filters by status / operator / commercial responsible / item type /
 * has-failed-items / creation period / source order, and carries the requiring/confirmed item counts and the
 * source Order number as the human identifier. Data is seeded via JDBC.
 */
class BookingRequestListApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
    void exposesOnlyOperationalColumns() throws Exception {
        seedRequest("Contrato Acme", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE", "SERVICE_FEE"});

        String body = mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].proposalTitle").value("Contrato Acme"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].commercialOrderNumber").isNumber())
                .andExpect(jsonPath("$.content[0].itemsRequiringBooking").value(1))
                .andExpect(jsonPath("$.content[0].confirmedItems").value(0))
                .andExpect(jsonPath("$.content[0].lastBookingAttemptAt").value(Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> content = JsonPath.read(body, "$.content");
        // The list item contract is exactly these fields — no Financial / Payment / Commission data.
        org.assertj.core.api.Assertions.assertThat(content.get(0).keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "commercialOrderId",
                        "commercialOrderNumber",
                        "proposalId",
                        "proposalTitle",
                        "status",
                        "bookingOperatorId",
                        "bookingOperatorName",
                        "operatorUnassigned",
                        "responsiblePersonId",
                        "responsibleName",
                        "itemsRequiringBooking",
                        "confirmedItems",
                        "createdAt",
                        "updatedAt",
                        "lastBookingAttemptAt");
    }

    @Test
    void managerAndDirectorAndOperatorCanList() throws Exception {
        seedRequest("Visivel", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});

        for (String[] cred :
                new String[][] {{"operacoes", "operacoes123"}, {"comercial", "comercial123"}, {"diretor", "diretor123"}
                }) {
            mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + login(cred[0], cred[1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.hasItem("Visivel")));
        }
    }

    @Test
    void excludesConfirmedAndCancelledByDefaultButKeepsFailed() throws Exception {
        seedRequest("Pendente", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("Falhou", "FAILED", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("Confirmada", "CONFIRMED", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("Cancelada", "CANCELLED", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});

        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.hasItem("Pendente")))
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.hasItem("Falhou")))
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.not(Matchers.hasItem("Confirmada"))))
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.not(Matchers.hasItem("Cancelada"))));

        // Explicitly asking for CONFIRMED shows it.
        mvc.perform(get("/api/bookings?status=CONFIRMED").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("Confirmada")));
    }

    @Test
    void filtersByStatus() throws Exception {
        seedRequest("Pendente", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("Falhou", "FAILED", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});

        mvc.perform(get("/api/bookings?status=FAILED").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("Falhou")));
    }

    @Test
    void filtersByCommercialResponsible() throws Exception {
        seedRequest("DoGerente", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("DoRep", "PENDING", REPRESENTANTE, OPERATOR, new String[] {"TRAVEL_PACKAGE"});

        mvc.perform(get("/api/bookings?responsible=" + REPRESENTANTE).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("DoRep")));
    }

    @Test
    void filtersByOperatorIncludingUnassigned() throws Exception {
        seedRequest("ComOperador", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("SemOperador", "PENDING", MANAGER, null, new String[] {"TRAVEL_PACKAGE"});

        mvc.perform(get("/api/bookings?operator=" + OPERATOR).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("ComOperador")));

        mvc.perform(get("/api/bookings?operator=unassigned").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("SemOperador")))
                .andExpect(jsonPath("$.content[0].operatorUnassigned").value(true));
    }

    @Test
    void filtersByItemType() throws Exception {
        seedRequest("SoPacote", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("SoCarro", "PENDING", MANAGER, OPERATOR, new String[] {"CAR_RENTAL"});

        mvc.perform(get("/api/bookings?itemType=CAR_RENTAL").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("SoCarro")));
    }

    @Test
    void filtersByHasFailedItems() throws Exception {
        UUID withFailed = seedRequest("ComFalha", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        failOneItem(withFailed);
        seedRequest("SemFalha", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});

        mvc.perform(get("/api/bookings?hasFailedItems=true").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("ComFalha")));
    }

    @Test
    void filtersBySourceOrder() throws Exception {
        UUID target = seedRequest("Alvo", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        seedRequest("Outra", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        UUID targetOrder = jdbc.queryForObject(
                "SELECT commercial_order_id FROM booking_requests WHERE id = ?::uuid", UUID.class, target.toString());

        mvc.perform(get("/api/bookings?order=" + targetOrder).header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].proposalTitle", Matchers.contains("Alvo")));
    }

    @Test
    void countsRequiringAndConfirmedItems() throws Exception {
        UUID request = seedRequest("Contagem", "PARTIALLY_CONFIRMED", MANAGER, OPERATOR, new String[] {
            "TRAVEL_PACKAGE", "CAR_RENTAL", "SERVICE_FEE"
        });
        confirmOneItem(request, "TRAVEL_PACKAGE");

        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                // Two items require booking (package + car); the service fee does not. One is confirmed.
                .andExpect(jsonPath("$.content[0].itemsRequiringBooking").value(2))
                .andExpect(jsonPath("$.content[0].confirmedItems").value(1));
    }

    @Test
    void sellerAndRepresentativeAreForbidden() throws Exception {
        seedRequest("Qualquer", "PENDING", MANAGER, OPERATOR, new String[] {"TRAVEL_PACKAGE"});
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + login("vendedor", "vendedor123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + login("representante", "representante123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotList() throws Exception {
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + login("financeiro", "financeiro123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    }

    // --- seeding helpers ---

    private void failOneItem(UUID requestId) {
        jdbc.update(
                "UPDATE booking_items SET status = 'FAILED' WHERE id = "
                        + "(SELECT id FROM booking_items WHERE booking_request_id = ?::uuid AND requires_booking LIMIT 1)",
                requestId.toString());
    }

    private void confirmOneItem(UUID requestId, String type) {
        jdbc.update(
                "UPDATE booking_items SET status = 'CONFIRMED' WHERE id = "
                        + "(SELECT id FROM booking_items WHERE booking_request_id = ?::uuid AND type = ? LIMIT 1)",
                requestId.toString(),
                type);
    }

    /** Seeds a lead+opportunity+proposal+order+booking-request chain; returns the booking request id. */
    private UUID seedRequest(String title, String status, UUID responsibleId, UUID operatorId, String[] itemTypes) {
        UUID lead = insertLead(title, responsibleId);
        UUID opp = insertOpportunity("Opp " + title, lead, responsibleId);
        UUID proposal = insertProposal(title, opp, lead, responsibleId);
        UUID order = insertOrder(proposal, opp, lead, responsibleId);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status,
                                              created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), ?, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                operatorId == null ? null : operatorId.toString(),
                status,
                MANAGER.toString(),
                MANAGER.toString());
        for (String type : itemTypes) {
            boolean requires = !"SERVICE_FEE".equals(type);
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
                    requires ? "PENDING" : "NOT_REQUIRED");
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
