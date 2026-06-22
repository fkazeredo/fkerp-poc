package com.fksoft.erp.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.infra.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end (MockMvc, real Postgres) of the minimum Opportunity indicators: the period-scoped volume
 * (total / lost / by stage / by origin / by responsible) plus the current-snapshot pipeline (active,
 * ready for proposal, overdue close, active value, value by responsible), visibility scoping (a
 * representative's numbers cover only their own pipeline), the read scopes (Finance has none → 403), and
 * that the contract carries commercial pipeline data only — never Proposal, Sale, Booking or Financial
 * fields. Opportunities (and their source leads) are inserted directly via JDBC to set up varied
 * stages/owners/origins/values/dates cheaply.
 */
class OpportunityIndicatorsApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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

    private UUID website;
    private UUID instagram;
    private int phoneSeq;

    @BeforeEach
    void seed() {
        opportunities.deleteAll(); // FK to leads — clear opportunities first
        leads.deleteAll();
        List<Origin> active = origins.findByActiveTrueOrderBySortOrderAsc();
        website = active.get(0).id();
        instagram = active.get(1).id();
        phoneSeq = 0;

        // Manager-owned mix across stages/origins, recent.
        insertOpportunity("Alpha", "NEW_OPPORTUNITY", MANAGER, website, val("1000.00"), future(), 0);
        insertOpportunity("Bravo", "DISCOVERY", MANAGER, website, val("2500.00"), past(), 0);
        insertOpportunity("Charlie", "READY_FOR_PROPOSAL", MANAGER, instagram, val("4000.00"), future(), 0);
        insertOpportunity("Delta", "NEW_OPPORTUNITY", null, website, val("500.00"), null, 0);
        insertOpportunity("Echo", "PRODUCT_FIT", VENDEDOR, instagram, null, null, 0);
        insertOpportunity("Foxtrot", "LOST", MANAGER, website, null, null, 0);
        insertOpportunity("Gamma", "READY_FOR_PROPOSAL", REPRESENTANTE, website, val("3000.00"), past(), 0);

        // Open, but created two months ago — excluded from the period volume, still in the snapshot.
        insertOpportunity("OldOpen", "PRODUCT_FIT", MANAGER, website, val("7000.00"), future(), 60);
    }

    @Test
    void managerSeesGlobalVolumeIncludingLost() throws Exception {
        String body = managerIndicators();
        // Volume (all-time, no period param).
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(8);
        assertThat(JsonPath.<Integer>read(body, "$.lost")).isEqualTo(1);
        // By stage.
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStage[?(@.stage=='NEW_OPPORTUNITY')].count"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStage[?(@.stage=='READY_FOR_PROPOSAL')].count"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byStage[?(@.stage=='LOST')].count"))
                .containsExactly(1);
        // By origin / responsible.
        assertThat(JsonPath.<List<Integer>>read(body, "$.byOrigin[?(@.origin=='Website')].count"))
                .containsExactly(6);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName=='comercial')].count"))
                .containsExactly(5);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byResponsible[?(@.responsibleName == null)].count"))
                .containsExactly(1); // unassigned (Delta)
    }

    @Test
    void managerSeesTheCurrentPipelineSnapshot() throws Exception {
        String body = managerIndicators();
        assertThat(JsonPath.<Integer>read(body, "$.active")).isEqualTo(7); // all non-LOST, incl. OldOpen
        assertThat(JsonPath.<Integer>read(body, "$.readyForProposal")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.overdueClose")).isEqualTo(2); // Bravo + Gamma
        assertThat(decimal(body, "$.activePipelineValue")).isEqualByComparingTo("18000");
        assertThat(firstDecimal(body, "$.valueByResponsible[?(@.responsibleName=='comercial')].value"))
                .isEqualByComparingTo("14500"); // Alpha+Bravo+Charlie+OldOpen
    }

    @Test
    void thePeriodNarrowsVolumeButNotTheSnapshot() throws Exception {
        // A period starting tomorrow contains nothing created yet → volume zero…
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        String body = mvc.perform(get("/api/opportunities/indicators")
                        .param("createdFrom", tomorrow)
                        .header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(0);
        // …but the pipeline snapshot ignores the period.
        assertThat(JsonPath.<Integer>read(body, "$.active")).isEqualTo(7);
        assertThat(decimal(body, "$.activePipelineValue")).isEqualByComparingTo("18000");
    }

    @Test
    void representativeIndicatorsAreScopedToTheirOwnPipeline() throws Exception {
        String rep = login("representante", "representante123");
        String body = mvc.perform(get("/api/opportunities/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.total")).isEqualTo(1); // only Gamma
        assertThat(JsonPath.<Integer>read(body, "$.active")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.readyForProposal")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.overdueClose")).isEqualTo(1);
        assertThat(decimal(body, "$.activePipelineValue")).isEqualByComparingTo("3000");
        // The manager's Opportunities never leak into a representative's numbers.
        mvc.perform(get("/api/opportunities/indicators").header("Authorization", "Bearer " + rep))
                .andExpect(jsonPath("$.byResponsible[*].responsibleName", not(hasItem("comercial"))));
    }

    @Test
    void exposesOnlyCommercialIndicatorFields() throws Exception {
        Map<String, Object> root = JsonPath.read(managerIndicators(), "$");
        // The contract is exactly these fields — no Proposal / Sale / Booking / Financial / Commission data.
        assertThat(root.keySet())
                .containsExactlyInAnyOrder(
                        "total",
                        "lost",
                        "byStage",
                        "byOrigin",
                        "byResponsible",
                        "active",
                        "readyForProposal",
                        "overdueClose",
                        "activePipelineValue",
                        "valueByResponsible");
    }

    @Test
    void financeHasNoAccessToIndicators() throws Exception {
        String fin = login("financeiro", "financeiro123");
        mvc.perform(get("/api/opportunities/indicators").header("Authorization", "Bearer " + fin))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutAReadScope() throws Exception {
        String noRead = tokens.issueAccessToken(new AuthenticatedUser(VENDEDOR, "v", Set.of("crm:opportunity:create")));
        mvc.perform(get("/api/opportunities/indicators").header("Authorization", "Bearer " + noRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/opportunities/indicators")).andExpect(status().isUnauthorized());
    }

    private String managerIndicators() throws Exception {
        return mvc.perform(get("/api/opportunities/indicators").header("Authorization", "Bearer " + manager()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static BigDecimal decimal(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    // A JsonPath filter (e.g. [?(...)].value) yields a List; take its first element as a BigDecimal.
    private static BigDecimal firstDecimal(String body, String filterPath) {
        List<Object> values = JsonPath.read(body, filterPath);
        return new BigDecimal(values.get(0).toString());
    }

    private static BigDecimal val(String value) {
        return new BigDecimal(value);
    }

    private static Date past() {
        return Date.valueOf(LocalDate.now(ZoneOffset.UTC).minusDays(5));
    }

    private static Date future() {
        return Date.valueOf(LocalDate.now(ZoneOffset.UTC).plusDays(30));
    }

    private void insertOpportunity(
            String name,
            String stage,
            UUID responsibleId,
            UUID originId,
            BigDecimal estimatedValue,
            Date expectedCloseDate,
            int createdDaysAgo) {
        UUID leadId = insertLead(name, responsibleId, originId);
        jdbc.update(
                """
                INSERT INTO opportunities (id, version, lead_id, name, origin_id, responsible_person_id,
                                           main_interest, stage, estimated_value, expected_close_date, loss_reason,
                                           created_at, updated_at, created_by, updated_by)
                VALUES (cast(? as uuid), 0, cast(? as uuid), ?, cast(? as uuid), cast(? as uuid),
                        ?, ?, ?, ?, ?, now() - make_interval(days => ?), now(), cast(? as uuid), cast(? as uuid))
                """,
                UUID.randomUUID().toString(),
                leadId.toString(),
                name,
                originId.toString(),
                responsibleId == null ? null : responsibleId.toString(),
                "Pacote " + name,
                stage,
                estimatedValue,
                expectedCloseDate,
                "LOST".equals(stage) ? "OTHER" : null,
                createdDaysAgo,
                MANAGER.toString(),
                MANAGER.toString());
    }

    private UUID insertLead(String name, UUID responsibleId, UUID originId) {
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
