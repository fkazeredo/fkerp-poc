package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.LossReasonRepository;
import com.fksoft.erp.domain.crm.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the operational Lead list: visibility, the lost-by-default
 * rule, every filter, search, the {@code PageResponse} shape and the read scopes. Test leads are
 * inserted directly (status transitions / assignment do not exist in the domain yet).
 */
class LeadListApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEED_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Autowired
    private LeadRepository leads;

    @Autowired
    private OriginRepository origins;

    @Autowired
    private LossReasonRepository lossReasons;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID websiteOriginId;

    @BeforeEach
    void seed() {
        leads.deleteAll();
        websiteOriginId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        Instant now = Instant.now();
        insertLead("Alpha", LeadStatus.NEW, USER_A, "11999990001", null, now);
        insertLead("Bravo", LeadStatus.CONTACTED, USER_B, "11999990002", null, now);
        insertLead("Charlie", LeadStatus.LOST, USER_A, "11999990003", null, now);
        insertLead("Delta", LeadStatus.NEW, null, null, "delta@example.com", now);
    }

    @Test
    void listsForAuthorizedUserAndExcludesLostByDefault() throws Exception {
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Bravo", "Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Charlie"))))
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void showsLostOnlyWhenExplicitlyFiltered() throws Exception {
        mvc.perform(get("/api/leads").param("status", "LOST").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Charlie")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))));
    }

    @Test
    void filtersByStatus() throws Exception {
        mvc.perform(get("/api/leads").param("status", "NEW").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void filtersByOrigin() throws Exception {
        mvc.perform(get("/api/leads")
                        .param("originId", websiteOriginId.toString())
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].origin", hasItem("Website")));
    }

    @Test
    void filtersByResponsible() throws Exception {
        mvc.perform(get("/api/leads")
                        .param("responsible", USER_A.toString())
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void filtersUnassignedAndFlagsThem() throws Exception {
        mvc.perform(get("/api/leads").param("responsible", "unassigned").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem("Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Alpha"))))
                .andExpect(jsonPath("$.content[?(@.name=='Delta')].unassigned", hasItem(true)));
    }

    @Test
    void filtersByCreationPeriod() throws Exception {
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString().substring(0, 10);
        mvc.perform(get("/api/leads").param("createdFrom", tomorrow).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content").isEmpty());
        String today = Instant.now().toString().substring(0, 10);
        mvc.perform(get("/api/leads").param("createdFrom", today).header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")));
    }

    @Test
    void searchesByNameAndByContact() throws Exception {
        mvc.perform(get("/api/leads").param("q", "alph").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem("Alpha")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
        mvc.perform(get("/api/leads").param("q", "delta@exa").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItem("Delta")));
    }

    @Test
    void regularUserSeesOnlyOwnPlusUnassigned() throws Exception {
        String tokenA = tokens.issueAccessToken(new AuthenticatedUser(USER_A, "ua", Set.of("crm:lead:read")));
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Delta")))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Bravo"))));
    }

    @Test
    void managerSeesEveryLead() throws Exception {
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.content[*].name", hasItems("Alpha", "Bravo", "Delta")));
    }

    @Test
    void paginatesWithEnvelope() throws Exception {
        mvc.perform(get("/api/leads").param("size", "2").header("Authorization", "Bearer " + manager()))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsWithoutReadScope() throws Exception {
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(USER_A, "ua", Set.of("crm:lead:create")));
        mvc.perform(get("/api/leads").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsResponsiblesForAssignmentAndFilter() throws Exception {
        mvc.perform(get("/api/crm/responsibles").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("comercial")));
    }

    @Test
    void createdLeadWithResponsibleIsListableByThatResponsible() throws Exception {
        String token = manager();
        mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Echo","phone":"11999990009","originId":"%s","responsiblePersonId":"%s"}
                                """
                                        .formatted(websiteOriginId, SEED_USER)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/leads")
                        .param("responsible", SEED_USER.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[*].name", hasItem("Echo")))
                .andExpect(jsonPath("$.content[?(@.name=='Echo')].responsibleName", hasItem("comercial")));
    }

    private String manager() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comercial\",\"password\":\"comercial123\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private void insertLead(
            String name, LeadStatus status, UUID responsibleId, String phone, String email, Instant at) {
        // A lost lead must carry a loss reason (DB CHECK chk_leads_lost_has_reason).
        String lossReasonId = status == LeadStatus.LOST
                ? lossReasons.findByActiveTrueOrderBySortOrderAsc().get(0).id().toString()
                : null;
        jdbc.update(
                """
                INSERT INTO leads (id, name, phone, whatsapp, email, origin_id, status,
                                   responsible_person_id, loss_reason_id, created_at, updated_at,
                                   created_by, updated_by)
                VALUES (cast(? as uuid), ?, ?, NULL, ?, cast(? as uuid), ?, cast(? as uuid),
                        cast(? as uuid), ?, ?, cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                name,
                phone,
                email,
                websiteOriginId.toString(),
                status.name(),
                responsibleId == null ? null : responsibleId.toString(),
                lossReasonId,
                Timestamp.from(at),
                Timestamp.from(at),
                SEED_USER.toString(),
                SEED_USER.toString());
    }
}
