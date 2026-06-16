package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end (MockMvc, real Postgres) of Lead deduplication at registration: a new Lead that shares a
 * phone/WhatsApp number or e-mail with an OPEN Lead is rejected with 409 {@code lead.duplicate} and the
 * existing Lead id; numbers match across the phone and WhatsApp fields; the e-mail match is
 * case-insensitive; and the same contact can be registered again once the previous Lead is Lost.
 */
class LeadDedupApiIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private LossReasonRepository lossReasons;

    @BeforeEach
    void clean() {
        leads.deleteAll();
    }

    @Test
    void rejectsADuplicatePhoneAmongOpenLeads() throws Exception {
        String token = manager();
        String firstId = create(body("Maria", "11999990001", null, null), token);

        mvc.perform(request(body("Maria 2", "11999990001", null, null), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("lead.duplicate"))
                .andExpect(jsonPath("$.fields[?(@.field=='leadId')].message", hasItem(firstId)));
    }

    @Test
    void rejectsADuplicateEmailCaseInsensitive() throws Exception {
        String token = manager();
        String firstId = create(body("Joao", null, null, "joao@example.com"), token);

        mvc.perform(request(body("Joao 2", null, null, "JOAO@EXAMPLE.COM"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("lead.duplicate"))
                .andExpect(jsonPath("$.fields[?(@.field=='leadId')].message", hasItem(firstId)));
    }

    @Test
    void matchesANumberAcrossThePhoneAndWhatsappFields() throws Exception {
        String token = manager();
        create(body("Ana", "11999990002", null, null), token); // number in phone
        // The same number arriving as WhatsApp (no phone) still collides.
        mvc.perform(request(body("Ana 2", null, "11999990002", null), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("lead.duplicate"));
    }

    @Test
    void allowsRegisteringTheSameContactOnceThePreviousLeadIsLost() throws Exception {
        String token = manager();
        String firstId = create(body("Pedro", "11999990003", null, null), token);

        UUID reasonId = lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        mvc.perform(post("/api/leads/" + firstId + "/lose")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossReasonId\":\"%s\"}".formatted(reasonId)))
                .andExpect(status().isOk());

        // The previous lead is Lost, so the same phone is free again.
        mvc.perform(request(body("Pedro again", "11999990003", null, null), token))
                .andExpect(status().isCreated());
    }

    @Test
    void allowsDistinctContacts() throws Exception {
        String token = manager();
        create(body("Lucas", "11999990004", null, "lucas@example.com"), token);

        mvc.perform(request(body("Outro", "11999990005", null, "outro@example.com"), token))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> body(String name, String phone, String whatsapp, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put(
                "originId",
                origins.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString());
        if (phone != null) {
            body.put("phone", phone);
        }
        if (whatsapp != null) {
            body.put("whatsapp", whatsapp);
        }
        if (email != null) {
            body.put("email", email);
        }
        return body;
    }

    private MockHttpServletRequestBuilder request(Map<String, Object> body, String token) throws Exception {
        return post("/api/leads")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private String create(Map<String, Object> body, String token) throws Exception {
        String response = mvc.perform(request(body, token))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String manager() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comercial\",\"password\":\"comercial123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
