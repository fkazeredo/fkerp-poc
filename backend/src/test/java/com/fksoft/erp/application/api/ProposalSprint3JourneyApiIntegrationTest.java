package com.fksoft.erp.application.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 3 end-to-end validation (MockMvc, real Postgres): the three coherent Sales &amp; Proposals journeys,
 * each driven through the real API so the Proposal/Order slices compose as ONE business flow rather than
 * isolated operations. Each journey starts from an Opportunity already in READY_FOR_PROPOSAL (Sprint 2's
 * outcome, seeded directly — the funnel itself is validated by the Sprint 2 journey test).
 *
 * <ul>
 *   <li><b>Main:</b> create a Proposal (DRAFT) → add items → confirm total → set validity + terms → submit
 *       for review → approve → mark sent → register customer acceptance → create the Commercial Order. The
 *       Order starts PENDING_BOOKING, snapshots the items/total/source references, and the source Opportunity
 *       becomes WON. No Booking/Receivable/Payment/Commission data is created or exposed.
 *   <li><b>Alternative 1 (internal rejection):</b> a Proposal under review is rejected with a reason; the
 *       REJECTED Proposal cannot be marked sent and cannot create a Commercial Order, and stays traceable.
 *   <li><b>Alternative 2 (customer rejection):</b> a Sent Proposal is declined by the customer with a reason;
 *       the REJECTED Proposal creates no Commercial Order, the source Opportunity stays READY_FOR_PROPOSAL
 *       (not Won) and traceable.
 * </ul>
 */
class ProposalSprint3JourneyApiIntegrationTest extends AbstractIntegrationTest {

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
        orders.deleteAll(); // FK to proposals
        proposals.deleteAll(); // FK to opportunities/leads
        opportunities.deleteAll();
        leads.deleteAll();
    }

    @Test
    void mainFlow_readyOpportunity_throughProposalLifecycle_toCommercialOrder() throws Exception {
        String manager = login("comercial", "comercial123");
        UUID lead = insertLead("Main", MANAGER);
        UUID opp = insertReadyOpportunity("Main", lead);

        // 1-3. Create the Proposal from the READY_FOR_PROPOSAL Opportunity — it starts DRAFT, linked to the source.
        String created = mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Proposta Main\"}".formatted(opp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String proposal = JsonPath.read(created, "$.id");

        proposalDetail(proposal, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.opportunityId").value(opp.toString()))
                .andExpect(jsonPath("$.leadId").value(lead.toString()))
                .andExpect(jsonPath("$.responsibleName").value("comercial"));

        // 4-5. Add a bookable item; the total is recomputed inside the aggregate (2 x 500 = 1000).
        addItem(proposal, manager, "TRAVEL_PACKAGE", 2, "500.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TRAVEL_PACKAGE"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(1000.00))
                .andExpect(jsonPath("$.total").value(1000.00));

        // 6. Set the validity date and the commercial terms (Draft-only edit).
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\",\"commercialTerms\":\"À vista\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validUntil").value("2026-12-31"))
                .andExpect(jsonPath("$.commercialTerms").value("À vista"))
                .andExpect(jsonPath("$.total").value(1000.00));

        // 7. Submit for review (Draft → Ready for review).
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));

        // 8. The approver approves (Ready for review → Approved), recorded in the history.
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusHistory[0].to").value("APPROVED"))
                .andExpect(jsonPath("$.statusHistory[0].by").value("comercial"));

        // 9. The commercial user marks it as sent (Approved → Sent) with a channel.
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelId\":\"%s\"}".formatted(refId("sending_channels", "EMAIL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sendingChannel").value("E-mail"));

        // 10. The commercial user records the customer's acceptance (Sent → Accepted).
        mvc.perform(post("/api/proposals/" + proposal + "/accept")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Cliente confirmou por e-mail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptanceNote").value("Cliente confirmou por e-mail"));

        // 11. Create the Commercial Order from the Accepted Proposal.
        String orderCreated = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(orderCreated, "$.id");

        // 12-14. The Order starts PENDING_BOOKING, snapshots the items/total/source references, and carries
        // enough for a Sprint 4 Booking (bookable item type + quantity + pending-booking status). The contract
        // is commercial-only — NO Booking/Receivable/Payment/Commission data is created or exposed.
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_BOOKING"))
                .andExpect(jsonPath("$.requiresBooking").value(true))
                .andExpect(jsonPath("$.number").isNumber())
                .andExpect(jsonPath("$.proposalId").value(proposal))
                .andExpect(jsonPath("$.opportunityId").value(opp.toString()))
                .andExpect(jsonPath("$.leadId").value(lead.toString()))
                .andExpect(jsonPath("$.responsibleId").value(MANAGER.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TRAVEL_PACKAGE"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(1000.00))
                .andExpect(jsonPath("$.sourceProposal.id").value(proposal))
                // The commercial context is surfaced from the preserved Proposal — ready for Sprint 4 booking.
                .andExpect(jsonPath("$.sourceProposal.commercialTerms").value("À vista"))
                .andExpect(jsonPath("$.sourceProposal.validUntil").value("2026-12-31"))
                .andExpect(jsonPath("$.sourceOpportunity.stage").value("WON"))
                .andExpect(jsonPath("$.booking").doesNotExist())
                .andExpect(jsonPath("$.receivable").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist())
                .andExpect(jsonPath("$.commission").doesNotExist());

        // The source Opportunity is now WON (closed-won) on its own endpoint.
        mvc.perform(get("/api/opportunities/" + opp).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("WON"));

        // Finance now reads the Order (it holds sales:order:read:all to originate Receivables from confirmed
        // bookings, Sprint 5) — but it cannot create or modify it; the manager reads it too.
        String finance = login("financeiro", "financeiro123");
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
    }

    @Test
    void alternativeFlow_internalRejection_cannotBeSentOrOrderedAndStaysTraceable() throws Exception {
        String manager = login("comercial", "comercial123");
        UUID lead = insertLead("Reject", MANAGER);
        UUID opp = insertReadyOpportunity("Reject", lead);
        String proposal = proposalUnderReview(manager, opp);

        // The approver rejects with a reason (Ready for review → Rejected).
        mvc.perform(post("/api/proposals/" + proposal + "/reject")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\",\"note\":\"Acima do orçamento\"}"
                                .formatted(refId("proposal_rejection_reasons", "PRICE_TOO_HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Preço muito alto"));

        // A REJECTED Proposal cannot be marked sent…
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-approved"));

        // …and cannot create a Commercial Order.
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-accepted"));

        // It stays traceable (still readable, with the reason recorded).
        proposalDetail(proposal, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Preço muito alto"));
    }

    @Test
    void alternativeFlow_customerRejection_createsNoOrderAndOpportunityStaysTraceable() throws Exception {
        String manager = login("comercial", "comercial123");
        UUID lead = insertLead("Decline", MANAGER);
        UUID opp = insertReadyOpportunity("Decline", lead);
        String proposal = sentProposal(manager, opp);

        // The customer declines the Sent Proposal with a reason (Sent → Rejected, terminal).
        mvc.perform(post("/api/proposals/" + proposal + "/decline")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonId\":\"%s\",\"note\":\"Foi com a concorrência\"}"
                                .formatted(refId("customer_rejection_reasons", "CHOSE_COMPETITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.customerRejectionReason").value("Escolheu concorrente"));

        // No Commercial Order can be created from a customer-rejected Proposal.
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\"}".formatted(proposal)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-accepted"));

        // The source Opportunity stays READY_FOR_PROPOSAL (it was never won) and remains traceable.
        mvc.perform(get("/api/opportunities/" + opp).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY_FOR_PROPOSAL"));

        // The customer-rejected Proposal remains readable, with the customer reason recorded.
        proposalDetail(proposal, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.customerRejectionReason").value("Escolheu concorrente"))
                .andExpect(jsonPath("$.customerRejectionNote").value("Foi com a concorrência"));
    }

    /** Drives a fresh Proposal to READY_FOR_REVIEW (item + validity + submit) and returns its id. */
    private String proposalUnderReview(String token, UUID opportunityId) throws Exception {
        String created = mvc.perform(post("/api/proposals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opportunityId\":\"%s\",\"title\":\"Proposta\"}".formatted(opportunityId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String proposal = JsonPath.read(created, "$.id");
        addItem(proposal, token, "TRAVEL_PACKAGE", 1, "500.00").andExpect(status().isOk());
        mvc.perform(put("/api/proposals/" + proposal)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validUntil\":\"2026-12-31\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
        return proposal;
    }

    /** Drives a fresh Proposal to SENT (review → approve → send) and returns its id. */
    private String sentProposal(String token, UUID opportunityId) throws Exception {
        String proposal = proposalUnderReview(token, opportunityId);
        mvc.perform(post("/api/proposals/" + proposal + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + proposal + "/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        return proposal;
    }

    private ResultActions addItem(String proposal, String token, String type, int quantity, String unitValue)
            throws Exception {
        return mvc.perform(post("/api/proposals/" + proposal + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeId\":\"%s\",\"description\":\"Item\",\"quantity\":%d,\"unitValue\":%s}"
                        .formatted(proposalItemTypeId(type), quantity, unitValue)));
    }

    private ResultActions proposalDetail(String proposal, String token) throws Exception {
        return mvc.perform(get("/api/proposals/" + proposal).header("Authorization", "Bearer " + token));
    }

    private UUID refId(String table, String code) {
        return UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM " + table + " WHERE code = ?", String.class, code));
    }

    private UUID insertReadyOpportunity(String name, UUID leadId) {
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
