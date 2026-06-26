package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.CustomerStatus;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
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
 * API integration tests for creating/consolidating a Customer Profile from a Commercial Order
 * (Customer Management — Sprint 7 Slice 1). Covers the happy path (the consolidated profile), the authorization
 * sad paths (401/403), validation (400), the unknown source order (404), and the acceptance criteria that the
 * action starts the customer Active, preserves the commercial origin and contacts, and creates/alters no
 * Receivable/Booking/Commission/Customer-Care data.
 */
class CustomerApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CommercialOrderRepository orders;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private CustomerRepository customers;

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
        // Customers reference commercial_orders/proposals/opportunities (once consolidated), so delete them
        // before those tables; receivables reference customers, so they go first.
        jdbc.update("DELETE FROM receivables");
        jdbc.update("DELETE FROM customers");
        jdbc.update("DELETE FROM commercial_order_items");
        orders.deleteAll();
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void consolidatesACustomerProfileFromTheOrderStartingActiveAndPreservingOriginAndContacts() throws Exception {
        String mgr = login("comercial", "comercial123");
        UUID lead = insertLead("Graduate");
        UUID order = createOrder(mgr, lead);

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"commercialOrderId":"%s","name":"Maria S. Silva","document":"12345678901",
                                 "documentType":"CPF","email":"maria@example.com","phone":"11999999999",
                                 "whatsapp":"11888888888","preferredContactMethod":"WHATSAPP","notes":"VIP"}
                                """
                                        .formatted(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sourceCommercialOrderId").value(order.toString()))
                .andExpect(jsonPath("$.sourceProposalId").isNotEmpty())
                .andExpect(jsonPath("$.sourceOpportunityId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Maria S. Silva"))
                .andExpect(jsonPath("$.document").value("12345678901"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.phone").value("11999999999"))
                .andExpect(jsonPath("$.whatsapp").value("11888888888"))
                .andExpect(jsonPath("$.preferredContactMethod").value("WHATSAPP"))
                .andExpect(jsonPath("$.notes").value("VIP"));

        // Idempotent consolidation — the customer materialized at deal-close was enriched in place, not duplicated.
        assertThat(customers.count()).isEqualTo(1);
        Customer customer = customers.findByLeadId(lead).orElseThrow();
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.sourceCommercialOrderId()).isEqualTo(order);
        // No Customer Care / Receivable / Booking / Commission data was created.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivables", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_requests", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commissions", Long.class))
                .isZero();
    }

    @Test
    void theBackOfficeOperationsUserMayAlsoConsolidate() throws Exception {
        String mgr = login("comercial", "comercial123");
        String ops = login("operacoes", "operacoes123");
        UUID lead = insertLead("Ops");
        UUID order = createOrder(mgr, lead);

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value("Lead Ops"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsACommercialUserWithoutTheCreateScope() throws Exception {
        String mgr = login("comercial", "comercial123");
        UUID lead = insertLead("NoScope");
        UUID order = createOrder(mgr, lead);
        String seller = login("vendedor", "vendedor123");

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAMissingSourceOrderWith400() throws Exception {
        String mgr = login("comercial", "comercial123");

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[?(@.field=='commercialOrderId')]").isNotEmpty());
    }

    @Test
    void rejectsAnUnknownSourceOrderWith404() throws Exception {
        String mgr = login("comercial", "comercial123");

        mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commercialOrderId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("order.not-found"));
    }

    /** Creates an Accepted Proposal and an Order from it, returning the new order id. */
    private UUID createOrder(String token, UUID lead) throws Exception {
        UUID opp = insertOpportunity("Graduate", lead);
        UUID proposal = insertProposal(opp, lead);
        bringToAccepted(token, proposal);
        String body = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** Brings the Proposal to ACCEPTED with a travel-package item. */
    private void bringToAccepted(String token, UUID proposal) throws Exception {
        mvc.perform(post("/api/proposals/" + proposal + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"%s\",\"description\":\"x\",\"quantity\":1,\"unitValue\":500.00}"
                                .formatted(proposalItemTypeId("TRAVEL_PACKAGE"))))
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
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId) {
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
                MANAGER.toString(),
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
                        ?, 'READY_FOR_PROPOSAL', NULL, cast(? as uuid), cast(? as uuid))
                """,
                id.toString(),
                leadId.toString(),
                name,
                originId.toString(),
                MANAGER.toString(),
                "Pacote " + name,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertLead(String name) {
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
                MANAGER.toString(),
                MANAGER.toString(),
                MANAGER.toString());
        return id;
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
