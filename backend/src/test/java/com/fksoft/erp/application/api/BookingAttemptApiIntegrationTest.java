package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of registering a manual booking attempt: the operations user logs what
 * was done (type, result, description, date, optional next action, optional item link) on a Booking Request;
 * the attempt is append-only history that appears in the detail, moves the request PENDING → IN_PROGRESS, and
 * never confirms the booking, never changes a booking item's status, and never creates Financial/Commission
 * data. The latest attempt then surfaces on the list. Authorization is enforced (the booking:request:update
 * scope + canSee; missing fields/future date → 400; unknown request → 404; unknown linked item → 404).
 */
class BookingAttemptApiIntegrationTest extends AbstractIntegrationTest {

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
        jdbc.update("DELETE FROM booking_attempts");
        jdbc.update("DELETE FROM booking_items");
        jdbc.update("DELETE FROM booking_requests");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    private String valid() {
        return ("{\"typeId\":\"%s\",\"resultId\":\"%s\","
                        + "\"description\":\"Liguei para o fornecedor\",\"occurredAt\":\"2026-06-10T10:00:00Z\","
                        + "\"nextActionDate\":\"2026-06-25\"}")
                .formatted(
                        refId("booking_attempt_types", "SUPPLIER_PHONE_CONTACT"),
                        refId("booking_attempt_results", "WAITING_FOR_SUPPLIER"));
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    @Test
    void registersAnAttemptThatAppearsInTheDetailAndMovesPendingToInProgress() throws Exception {
        UUID request = pendingRequest();

        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk())
                // The attempt moved the request to IN_PROGRESS but confirmed nothing.
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].type").value("Contato telefônico com fornecedor"))
                .andExpect(jsonPath("$.attempts[0].result").value("Aguardando fornecedor"))
                .andExpect(jsonPath("$.attempts[0].description").value("Liguei para o fornecedor"))
                .andExpect(jsonPath("$.attempts[0].nextActionDate").value("2026-06-25"))
                .andExpect(jsonPath("$.attempts[0].registeredByName").value("operacoes"))
                .andExpect(jsonPath("$.attempts[0].bookingItemId").doesNotExist())
                // The booking items keep their statuses (no automatic confirmation).
                .andExpect(jsonPath("$.items[?(@.type=='TRAVEL_PACKAGE')].status", Matchers.hasItem("PENDING")))
                .andExpect(jsonPath("$.itemsConfirmed").value(0));
    }

    @Test
    void theManagerCanAlsoRegisterAnAttempt() throws Exception {
        UUID request = pendingRequest();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + login("comercial", "comercial123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk());
    }

    @Test
    void requiresTypeResultDateAndDescription() throws Exception {
        UUID request = pendingRequest();
        String token = operator();
        UUID type = refId("booking_attempt_types", "OTHER");
        UUID result = refId("booking_attempt_results", "STARTED");
        // Missing type.
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(result)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        // Missing description.
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"%s\",\"resultId\":\"%s\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                .formatted(type, result)))
                .andExpect(status().isBadRequest());
        // Missing date.
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\"}"
                                .formatted(type, result)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAFutureDate() throws Exception {
        UUID request = pendingRequest();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2099-01-01T10:00:00Z\"}"
                                        .formatted(
                                                refId("booking_attempt_types", "OTHER"),
                                                refId("booking_attempt_results", "STARTED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='occurredAt')]").exists());
    }

    @Test
    void nextActionDateIsOptional() throws Exception {
        UUID request = pendingRequest();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Conferi internamente\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                        .formatted(
                                                refId("booking_attempt_types", "INTERNAL_VERIFICATION"),
                                                refId("booking_attempt_results", "STARTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].nextActionDate").value(Matchers.nullValue()));
    }

    @Test
    void canLinkAnAttemptToABookingItem() throws Exception {
        UUID request = pendingRequest();
        UUID itemId = jdbc.queryForObject(
                "SELECT id FROM booking_items WHERE booking_request_id = ?::uuid AND type = 'TRAVEL_PACKAGE' LIMIT 1",
                UUID.class,
                request.toString());
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"bookingItemId\":\"%s\",\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"Disponível\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                        .formatted(
                                                itemId,
                                                refId("booking_attempt_types", "MANUAL_AVAILABILITY_CHECK"),
                                                refId("booking_attempt_results", "AVAILABILITY_FOUND"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].bookingItemId").value(itemId.toString()));
    }

    @Test
    void rejectsAnItemThatIsNotInTheRequest() throws Exception {
        UUID request = pendingRequest();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"bookingItemId\":\"%s\",\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"2026-06-10T10:00:00Z\"}"
                                        .formatted(
                                                UUID.randomUUID(),
                                                refId("booking_attempt_types", "OTHER"),
                                                refId("booking_attempt_results", "STARTED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.item-not-found"));
    }

    @Test
    void directorWithoutTheUpdateScopeIsForbidden() throws Exception {
        UUID request = pendingRequest();
        // diretor (004) holds booking:request:read:all but no booking:request:update.
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + login("diretor", "diretor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerIsForbidden() throws Exception {
        UUID request = pendingRequest();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + login("vendedor", "vendedor123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWhoCannotSeeTheRequestIsForbidden() throws Exception {
        UUID request = pendingRequest();
        // Holds the update scope but only the own read tier, and is neither operator nor responsible.
        String token = tokens.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "other", Set.of("booking:request:update", "booking:request:read")));
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("booking.access-denied"));
    }

    @Test
    void unknownRequestIsNotFound() throws Exception {
        mvc.perform(post("/api/bookings/" + UUID.randomUUID() + "/attempts")
                        .header("Authorization", "Bearer " + operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking.not-found"));
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/bookings/" + UUID.randomUUID() + "/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLatestAttemptSurfacesOnTheList() throws Exception {
        UUID request = pendingRequest();
        String token = operator();
        mvc.perform(post("/api/bookings/" + request + "/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid()))
                .andExpect(status().isOk());
        // The list now exposes the latest attempt instant for that request.
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastBookingAttemptAt").value(Matchers.notNullValue()));
    }

    /** Seeds a PENDING booking request (a travel package + a service fee), responsible = the manager. */
    private UUID pendingRequest() {
        UUID lead = insertLead();
        UUID opp = insertOpportunity(lead);
        UUID proposal = insertProposal(opp, lead);
        UUID order = insertOrder(proposal, opp, lead);
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO booking_requests (id, version, commercial_order_id, proposal_id, opportunity_id,
                                              lead_id, responsible_person_id, booking_operator_id, status,
                                              created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
                        cast(? as uuid), cast(? as uuid), 'PENDING', now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                requestId.toString(),
                order.toString(),
                proposal.toString(),
                opp.toString(),
                lead.toString(),
                MANAGER.toString(),
                OPERATOR.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        for (String[] item :
                new String[][] {{"TRAVEL_PACKAGE", "PENDING", "true"}, {"SERVICE_FEE", "NOT_REQUIRED", "false"}}) {
            jdbc.update(
                    """
                    INSERT INTO booking_items (id, booking_request_id, order_item_id, type, description,
                                               quantity, requires_booking, status, created_at)
                    VALUES (cast(? as uuid), cast(? as uuid), cast(? as uuid), ?, ?, 1, ?, ?, now())
                    """,
                    UUID.randomUUID().toString(),
                    requestId.toString(),
                    UUID.randomUUID().toString(),
                    item[0],
                    "Item " + item[0],
                    Boolean.parseBoolean(item[2]),
                    item[1]);
        }
        return requestId;
    }

    private UUID insertOrder(UUID proposalId, UUID opportunityId, UUID leadId) {
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
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO proposals (id, version, opportunity_id, lead_id, responsible_person_id, title,
                                       status, subtotal, total, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), cast(? as uuid), cast(? as uuid), 'Reserva',
                        'ACCEPTED', 100.00, 100.00, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                opportunityId.toString(),
                leadId.toString(),
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, loss_reason, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), 'Opp Reserva', cast(? as uuid), cast(? as uuid),
                        'Pacote', ?, NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                originId.toString(),
                MANAGER.toString(),
                "WON",
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead() {
        UUID id = UUID.randomUUID();
        String phone = "1190000%04d".formatted(phoneSeq++);
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), 'Lead Reserva', ?, NULL, NULL, cast(? as uuid), 'NEW', cast(? as uuid),
                        NULL, now(), now(), cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                phone,
                originId.toString(),
                MANAGER.toString(),
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
