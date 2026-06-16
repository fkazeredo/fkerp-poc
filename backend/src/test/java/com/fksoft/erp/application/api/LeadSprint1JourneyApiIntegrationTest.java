package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.InteractionResultRepository;
import com.fksoft.erp.domain.crm.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.LossReasonRepository;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sprint 1 end-to-end validation (MockMvc, real Postgres): the two coherent business journeys, each
 * exercised across the real actors with their real tokens (no JDBC shortcuts), so the slices compose
 * as one flow rather than isolated operations.
 *
 * <ul>
 *   <li><b>Main:</b> an Instagram Lead is created unassigned, the manager finds it in the unassigned
 *       pool, assigns it to a seller, the seller logs a WhatsApp effective contact (NEW → CONTACTED)
 *       and qualifies it with a main interest (→ QUALIFIED). Visibility holds throughout, and the
 *       qualified Lead carries enough to seed a Sprint 2 Opportunity.
 *   <li><b>Alternative (Lost):</b> a referral Lead is worked by its responsible, who finds no
 *       interest and marks it Lost (a reason is mandatory). The Lost Lead leaves the default list but
 *       the manager still finds it via the LOST filter and opens it for history.
 * </ul>
 */
class LeadSprint1JourneyApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private LossReasonRepository lossReasons;

    @Autowired
    private InteractionTypeRepository interactionTypes;

    @Autowired
    private InteractionResultRepository interactionResults;

    @BeforeEach
    void clean() {
        leads.deleteAll();
    }

    @Test
    void mainFlow_instagramLead_unassigned_assigned_contacted_qualified() throws Exception {
        String manager = login("comercial", "comercial123");
        String name = "Journey Main " + UUID.randomUUID();

        // 1. Create an Instagram Lead with no responsible → NEW, unassigned.
        String id = createLead(manager, name, "INSTAGRAM", null);
        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.unassigned").value(true));

        // 2. The manager identifies it as unassigned — in the pending worklist and the unassigned filter.
        mvc.perform(get("/api/leads/pending").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name=='" + name + "')].unassigned", hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.name=='" + name + "')].reasons[*]", hasItem("UNASSIGNED")));
        mvc.perform(get("/api/leads").param("responsible", "unassigned").header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));

        // 3. The manager assigns the Lead to the seller.
        mvc.perform(reassign(id, VENDEDOR, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleName").value("vendedor"))
                .andExpect(jsonPath("$.unassigned").value(false));

        // 4. The seller logs a WhatsApp effective contact → the Lead becomes CONTACTED.
        String seller = login("vendedor", "vendedor123");
        interact(id, seller, "WHATSAPP", "CONTACT_MADE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.interactions[*].type", hasItem("WhatsApp")));

        // 5. The seller records the commercial interest and qualifies the Lead → QUALIFIED.
        mvc.perform(post("/api/leads/" + id + "/qualify")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainInterest\":\"Plano corporativo\",\"note\":\"bom perfil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"))
                // Sprint-2-ready payload: who/when + the main interest carry over.
                .andExpect(jsonPath("$.qualification.mainInterest").value("Plano corporativo"))
                .andExpect(jsonPath("$.qualification.qualifiedBy").value("vendedor"))
                .andExpect(jsonPath("$.qualification.qualifiedAt", notNullValue()));

        // 6. Visibility holds: finance has no access, an unrelated rep cannot see it, the manager can.
        String finance = login("financeiro", "financeiro123");
        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());

        String otherRep = login("representante", "representante123");
        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + otherRep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));

        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));
    }

    @Test
    void alternativeFlow_referralLead_attempted_lost_historicallyAccessible() throws Exception {
        String manager = login("comercial", "comercial123");
        String name = "Journey Lost " + UUID.randomUUID();

        // 1. Create a referral Lead, owned by the representative.
        String id = createLead(manager, name, "REFERRAL", REPRESENTANTE);

        // 2. The responsible attempts contact and finds no interest.
        String rep = login("representante", "representante123");
        interact(id, rep, "WHATSAPP", "NOT_INTERESTED").andExpect(status().isOk());

        // 3. Marking Lost requires a reason — the empty request is rejected, a valid reason succeeds.
        mvc.perform(lose(id, "{}", rep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("lossReasonId")));
        mvc.perform(lose(id, "{\"lossReasonId\":\"%s\"}".formatted(lossReasonId("NO_INTEREST")), rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.reason", notNullValue()))
                .andExpect(jsonPath("$.loss.lostBy").value("representante"));

        // 4. The Lost Lead leaves the default operational list.
        mvc.perform(get("/api/leads").param("q", name).header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.content[*].name", not(hasItem(name))));

        // 5. The manager still finds it through the explicit LOST filter.
        mvc.perform(get("/api/leads")
                        .param("q", name)
                        .param("status", "LOST")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));

        // 6. It remains historically accessible in the detail, with its loss reason.
        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.loss.reason", notNullValue()));
    }

    private ResultActions interact(String id, String token, String typeCode, String resultCode) throws Exception {
        UUID typeId = interactionTypes.findByCode(typeCode).orElseThrow().id();
        UUID resultId = interactionResults.findByCode(resultCode).orElseThrow().id();
        return mvc.perform(post("/api/leads/" + id + "/interactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeId\":\"%s\",\"resultId\":\"%s\",\"description\":\"contato\",\"occurredAt\":\"%s\"}"
                        .formatted(typeId, resultId, Instant.now())));
    }

    private MockHttpServletRequestBuilder reassign(String id, UUID toResponsibleId, String token) {
        return post("/api/leads/" + id + "/reassign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsiblePersonId\":\"%s\"}".formatted(toResponsibleId));
    }

    private MockHttpServletRequestBuilder lose(String id, String body, String token) {
        return post("/api/leads/" + id + "/lose")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private UUID lossReasonId(String code) {
        return lossReasons.findByCode(code).orElseThrow().id();
    }

    private String createLead(String token, String name, String originCode, UUID responsibleId) throws Exception {
        UUID originId = origins.findByCode(originCode).orElseThrow().id();
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", "11999990000");
        body.put("originId", originId.toString());
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
