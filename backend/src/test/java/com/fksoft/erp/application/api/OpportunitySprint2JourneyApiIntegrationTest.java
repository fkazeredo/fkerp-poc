package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.InteractionResultRepository;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityActivityResultRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityActivityTypeRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityLossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 2 end-to-end validation (MockMvc, real Postgres): the two coherent commercial journeys, each
 * exercised through the real API from a Qualified Lead onward (no JDBC shortcuts for the happy path), so
 * the Opportunity slices compose as one business flow rather than isolated operations.
 *
 * <ul>
 *   <li><b>Main:</b> a Qualified Lead originates an Opportunity (NEW_OPPORTUNITY); its responsible moves
 *       it forward through the strict funnel (Discovery → Product Fit → Ready for Proposal), records
 *       commercial activities and updates the estimated value / expected closing date. Visibility holds
 *       throughout, and the Ready-for-Proposal Opportunity carries enough to seed a Sprint 3 Proposal —
 *       while no Proposal/Sale/Booking/Financial data is created or exposed.
 *   <li><b>Alternative (Lost):</b> an Opportunity is worked, found to have no fit and marked Lost (a
 *       reason is mandatory). The Lost Opportunity leaves the default operational list but the manager
 *       still finds it via the LOST filter, and the source Lead remains historically traceable.
 * </ul>
 */
class OpportunitySprint2JourneyApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private OpportunityRepository opportunities;

    @Autowired
    private OpportunityActivityTypeRepository activityTypes;

    @Autowired
    private OpportunityActivityResultRepository activityResults;

    @Autowired
    private OpportunityLossReasonRepository lossReasons;

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private InteractionTypeRepository interactionTypes;

    @Autowired
    private InteractionResultRepository interactionResults;

    @BeforeEach
    void clean() {
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
    }

    @Test
    void mainFlow_qualifiedLead_throughPipeline_readyForProposal() throws Exception {
        String manager = login("comercial", "comercial123");
        String name = "Journey Main " + UUID.randomUUID();

        // 1-2-3. A Qualified Lead originates an Opportunity, which starts at NEW_OPPORTUNITY.
        String leadId = qualifiedLead(manager, name, MANAGER);
        String oppId = createOpportunity(leadId, manager);
        detail(oppId, manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.responsibleName").value("comercial"))
                .andExpect(jsonPath("$.unassigned").value(false))
                // The Opportunity seeds the main interest from the Lead qualification and stays traceable.
                .andExpect(jsonPath("$.mainInterest").value("Pacote corporativo"))
                .andExpect(jsonPath("$.sourceLead.id").value(leadId))
                .andExpect(jsonPath("$.sourceLead.status").value("QUALIFIED"));

        // 4. The responsible moves it forward to Discovery (recorded in the stage history).
        stage(oppId, "DISCOVERY", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DISCOVERY"))
                .andExpect(jsonPath("$.stageHistory[0].from").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.stageHistory[0].to").value("DISCOVERY"))
                .andExpect(jsonPath("$.stageHistory[0].by").value("comercial"));

        // 5. A commercial activity is registered (with a planned next action).
        String next = LocalDate.now().plusDays(7).toString();
        activity(oppId, manager, "MEETING", "CLIENT_ENGAGED", "Reunião de descoberta", next)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].type").value("Reunião"))
                .andExpect(jsonPath("$.activities[0].result").value("Cliente engajado"))
                .andExpect(jsonPath("$.activities[0].registeredBy").value("comercial"))
                .andExpect(jsonPath("$.nextActionDate").value(next))
                .andExpect(jsonPath("$.stage").value("DISCOVERY")); // an activity never moves the stage

        // 6. The estimated value and expected closing date are updated.
        edit(
                        oppId,
                        "{\"estimatedValue\":25000.00,\"expectedCloseDate\":\"2026-12-15\",\"productType\":\"Software corporativo\"}",
                        manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedValue").value(notNullValue()))
                .andExpect(jsonPath("$.expectedCloseDate").value("2026-12-15"))
                .andExpect(jsonPath("$.mainInterest").value("Pacote corporativo")); // unchanged

        // 7. Forward to Product Fit.
        stage(oppId, "PRODUCT_FIT", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PRODUCT_FIT"));

        // 8. Another activity identifies the fit.
        activity(oppId, manager, "MEETING", "PRODUCT_FIT_IDENTIFIED", "Aderência confirmada", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].result").value("Aderência identificada"));

        // 9. Forward to Ready for Proposal — the funnel's last active stage.
        stage(oppId, "READY_FOR_PROPOSAL", manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY_FOR_PROPOSAL"));

        // 10. The Ready-for-Proposal Opportunity carries enough for a Sprint 3 Proposal — and NO Proposal,
        // Sale, Booking or Financial data is created or exposed (the contract is commercial-only).
        detail(oppId, manager)
                .andExpect(jsonPath("$.stage").value("READY_FOR_PROPOSAL"))
                .andExpect(jsonPath("$.estimatedValue").value(notNullValue()))
                .andExpect(jsonPath("$.expectedCloseDate").value("2026-12-15"))
                .andExpect(jsonPath("$.mainInterest").value("Pacote corporativo"))
                .andExpect(jsonPath("$.sourceLead.id").value(leadId))
                .andExpect(jsonPath("$.stageHistory.length()").value(3))
                .andExpect(jsonPath("$.activities.length()").value(2))
                .andExpect(jsonPath("$.proposal").doesNotExist())
                .andExpect(jsonPath("$.sale").doesNotExist())
                .andExpect(jsonPath("$.booking").doesNotExist())
                .andExpect(jsonPath("$.financial").doesNotExist())
                .andExpect(jsonPath("$.commission").doesNotExist());

        // It stays in the default operational list (it is not LOST).
        mvc.perform(get("/api/opportunities").param("q", name).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));

        // Visibility holds throughout: finance has no access, an unrelated rep cannot see it, the manager can.
        String finance = login("financeiro", "financeiro123");
        detail(oppId, finance).andExpect(status().isForbidden());

        String otherRep = login("representante", "representante123");
        detail(oppId, otherRep)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("opportunity.access-denied"));

        detail(oppId, manager).andExpect(status().isOk());
    }

    @Test
    void alternativeFlow_qualifiedLead_lostAndTraceable() throws Exception {
        String manager = login("comercial", "comercial123");
        String name = "Journey Lost " + UUID.randomUUID();

        String leadId = qualifiedLead(manager, name, MANAGER);
        String oppId = createOpportunity(leadId, manager);

        // An activity shows there is no fit.
        activity(oppId, manager, "PHONE_CALL", "NOT_INTERESTED", "Cliente sem interesse", null)
                .andExpect(status().isOk());

        // Marking Lost requires a reason — the empty request is rejected, a valid reason succeeds.
        lose(oppId, "{}", manager).andExpect(status().isBadRequest());
        lose(
                        oppId,
                        "{\"lossReasonId\":\"%s\",\"note\":\"Produto não atende ao perfil\"}"
                                .formatted(lossReasons
                                        .findByCode("PRODUCT_MISMATCH")
                                        .orElseThrow()
                                        .id()),
                        manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LOST"))
                .andExpect(jsonPath("$.loss.reason").value("Produto não aderente"))
                .andExpect(jsonPath("$.loss.lostBy").value("comercial"))
                .andExpect(jsonPath("$.loss.lostAt").value(notNullValue()))
                .andExpect(jsonPath("$.loss.note").value("Produto não atende ao perfil"));

        // The Lost Opportunity leaves the default operational list…
        mvc.perform(get("/api/opportunities").param("q", name).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem(name))));

        // …but the manager still finds it through the explicit LOST filter.
        mvc.perform(get("/api/opportunities")
                        .param("q", name)
                        .param("stage", "LOST")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));

        // The source Lead remains historically traceable from the Opportunity, and on its own.
        detail(oppId, manager)
                .andExpect(jsonPath("$.stage").value("LOST"))
                .andExpect(jsonPath("$.sourceLead.id").value(leadId))
                .andExpect(jsonPath("$.sourceLead.status").value("QUALIFIED"));
        mvc.perform(get("/api/leads/" + leadId).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));

        // Losing again is rejected — LOST is terminal.
        lose(
                        oppId,
                        "{\"lossReasonId\":\"%s\"}"
                                .formatted(lossReasons
                                        .findByCode("OTHER")
                                        .orElseThrow()
                                        .id()),
                        manager)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.cannot-mark-lost"));
    }

    private String createOpportunity(String leadId, String token) throws Exception {
        String response = mvc.perform(post("/api/opportunities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadId\":\"%s\",\"productType\":\"Software\",\"estimatedValue\":15000.00}"
                                .formatted(leadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("NEW_OPPORTUNITY"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions detail(String oppId, String token) throws Exception {
        return mvc.perform(get("/api/opportunities/" + oppId).header("Authorization", "Bearer " + token));
    }

    private ResultActions stage(String oppId, String stage, String token) throws Exception {
        return mvc.perform(post("/api/opportunities/" + oppId + "/stage")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"%s\"}".formatted(stage)));
    }

    private ResultActions activity(
            String oppId, String token, String type, String result, String description, String nextActionDate)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("typeId", activityTypes.findByCode(type).orElseThrow().id());
        body.put("resultId", activityResults.findByCode(result).orElseThrow().id());
        body.put("description", description);
        body.put("occurredAt", Instant.now().minusSeconds(60).toString());
        if (nextActionDate != null) {
            body.put("nextActionDate", nextActionDate);
        }
        return mvc.perform(post("/api/opportunities/" + oppId + "/activities")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions edit(String oppId, String body, String token) throws Exception {
        return mvc.perform(put("/api/opportunities/" + oppId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions lose(String oppId, String body, String token) throws Exception {
        return mvc.perform(post("/api/opportunities/" + oppId + "/lose")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String qualifiedLead(String token, String name, UUID responsibleId) throws Exception {
        String leadId = createLead(token, name, responsibleId);
        contact(leadId, token);
        mvc.perform(post("/api/leads/" + leadId + "/qualify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Pacote corporativo\"}"))
                .andExpect(status().isOk());
        return leadId;
    }

    private void contact(String leadId, String token) throws Exception {
        UUID typeId = interactionTypes.findByCode("PHONE_CALL").orElseThrow().id();
        UUID resultId =
                interactionResults.findByCode("CONTACT_MADE").orElseThrow().id();
        mvc.perform(post("/api/leads/" + leadId + "/interactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"x\",\"occurredAt\":\"%s\"}"
                                .formatted(typeId, resultId, Instant.now())))
                .andExpect(status().isOk());
    }

    private String createLead(String token, String name, UUID responsibleId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put(
                "phone",
                "119%08d"
                        .formatted(
                                java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000)));
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        if (responsibleId != null) {
            body.put("responsiblePersonId", responsibleId.toString());
        }
        String response = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
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
