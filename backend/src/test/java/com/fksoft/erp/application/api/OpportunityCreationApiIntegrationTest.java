package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.InteractionResultRepository;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end (MockMvc, real Postgres) of creating a commercial Opportunity from a Qualified Lead: only
 * a QUALIFIED lead may originate one (New/Contacted/Lost → 422), a lead originates at most one
 * (duplicate → 409 with the existing id), the creator needs the {@code crm:opportunity:create} scope
 * and must be able to see the source lead, and the Opportunity starts at NEW_OPPORTUNITY preserving the
 * lead's origin, responsible and main interest. Leads are set up through the real API.
 */
class OpportunityCreationApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private OpportunityRepository opportunities;

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
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
    }

    @Test
    void createsAnOpportunityFromAQualifiedLead() throws Exception {
        String mgr = manager();
        String leadId = qualifiedLead(mgr, "Maria", MANAGER);

        String response = mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("NEW_OPPORTUNITY"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID opportunityId = UUID.fromString(JsonPath.read(response, "$.id"));

        Opportunity saved = opportunities.findById(opportunityId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY);
        org.assertj.core.api.Assertions.assertThat(saved.leadId()).isEqualTo(UUID.fromString(leadId));
        org.assertj.core.api.Assertions.assertThat(saved.responsiblePersonId()).isEqualTo(MANAGER);
        org.assertj.core.api.Assertions.assertThat(saved.mainInterest()).isEqualTo("Pacote corporativo");
    }

    @Test
    void rejectsCreatingFromANewLead() throws Exception {
        String mgr = manager();
        String leadId = createLead(mgr, "Novo", MANAGER); // stays NEW

        mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.lead-not-qualified"));
    }

    @Test
    void rejectsCreatingFromAContactedLead() throws Exception {
        String mgr = manager();
        String leadId = createLead(mgr, "Em contato", MANAGER);
        contact(leadId, mgr); // NEW -> CONTACTED, not qualified

        mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.lead-not-qualified"));
    }

    @Test
    void rejectsCreatingFromALostLead() throws Exception {
        String mgr = manager();
        String leadId = createLead(mgr, "Perdido", MANAGER);
        UUID reasonId = lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        mvc.perform(post("/api/leads/" + leadId + "/lose")
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(reasonId)))
                .andExpect(status().isOk());

        mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("opportunity.lead-not-qualified"));
    }

    @Test
    void rejectsASecondOpportunityForTheSameLead() throws Exception {
        String mgr = manager();
        String leadId = qualifiedLead(mgr, "Dupla", MANAGER);

        String first = mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        mvc.perform(opportunity(leadId, mgr))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("opportunity.already-exists-for-lead"))
                .andExpect(jsonPath("$.fields[?(@.field=='opportunityId')].message", hasItem(firstId)));
    }

    @Test
    void rejectsCreatingWithoutTheCreateScope() throws Exception {
        String mgr = manager();
        String leadId = qualifiedLead(mgr, "SemEscopo", MANAGER);
        // 'diretor' sees all leads but has no crm:opportunity:create scope.
        String director = login("diretor", "diretor123");

        mvc.perform(opportunity(leadId, director)).andExpect(status().isForbidden());
    }

    @Test
    void representativeCannotCreateFromAnotherUsersLead() throws Exception {
        String mgr = manager();
        String leadId = qualifiedLead(mgr, "DoGerente", MANAGER); // owned by the manager
        String rep = login("representante", "representante123"); // own-only visibility

        mvc.perform(opportunity(leadId, rep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("lead.access-denied"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder opportunity(
            String leadId, String token) {
        return post("/api/opportunities")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leadId\":\"%s\",\"productType\":\"Software\",\"estimatedValue\":1500.00}"
                        .formatted(leadId));
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
