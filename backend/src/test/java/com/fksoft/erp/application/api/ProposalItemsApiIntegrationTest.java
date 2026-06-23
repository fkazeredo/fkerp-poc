package com.fksoft.erp.application.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.erp.AbstractIntegrationTest;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end (MockMvc, real Postgres) of managing the items of a Draft Proposal: adding/editing/removing
 * items recomputes the Proposal total; description/quantity/unit value are required (400); an inconsistent
 * discount is rejected (422); items are editable only while Draft; visibility holds (a representative may
 * not edit another user's Proposal; Finance and consultation-only Board have no write access). The contract
 * carries commercial-offer data only — never Booking/Financial. Opportunities, leads and proposals are
 * seeded via JDBC.
 */
class ProposalItemsApiIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTANTE = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
    private UUID mgrProposal;
    private UUID repProposal;

    @BeforeEach
    void seed() {
        proposals.deleteAll();
        opportunities.deleteAll();
        leads.deleteAll();
        originId = origins.findByActiveTrueOrderBySortOrderAsc().get(0).id();
        phoneSeq = 0;

        UUID mgrLead = insertLead("Mgr", MANAGER);
        UUID mgrOpp = insertOpportunity("Mgr", "READY_FOR_PROPOSAL", MANAGER, mgrLead);
        mgrProposal = insertProposal(mgrOpp, mgrLead, MANAGER);

        UUID repLead = insertLead("Rep", REPRESENTANTE);
        UUID repOpp = insertOpportunity("Rep", "READY_FOR_PROPOSAL", REPRESENTANTE, repLead);
        repProposal = insertProposal(repOpp, repLead, REPRESENTANTE);
    }

    @Test
    void addsItemsAndReflectsTheProposalTotal() throws Exception {
        String mgr = manager();
        addItem(
                        mgr,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("TRAVEL_PACKAGE")
                                + "\",\"description\":\"Pacote\",\"quantity\":2,\"unitValue\":100.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("TRAVEL_PACKAGE"))
                .andExpect(jsonPath("$.items[0].typeLabel").value("Pacote de viagem"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(notNullValue()));

        String body = addItem(
                        mgr,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("SERVICE_FEE")
                                + "\",\"description\":\"Taxa\",\"quantity\":1,\"unitValue\":50.00}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<List<Object>>read(body, "$.items"))
                .hasSize(2);
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.total")).isEqualByComparingTo("250.00");
    }

    @Test
    void rejectsAnUnknownItemTypeWithUnprocessableEntity() throws Exception {
        addItem(
                        manager(),
                        mgrProposal,
                        "{\"typeId\":\"" + UUID.randomUUID()
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.item-type-not-available"));
    }

    @Test
    void appliesAPercentDiscountToTheTotal() throws Exception {
        String body = addItem(
                        manager(),
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":2,\"unitValue\":100.00,\"discountType\":\"PERCENT\",\"discountValue\":10}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.total")).isEqualByComparingTo("180.00");
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        addItem(manager(), mgrProposal, "{\"typeId\":\"" + proposalItemTypeId("OTHER") + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvalidDiscount() throws Exception {
        addItem(
                        manager(),
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":100.00,\"discountType\":\"PERCENT\",\"discountValue\":150}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.item-invalid"));
    }

    @Test
    void editsAnItemAndRecomputesTheTotal() throws Exception {
        String mgr = manager();
        String added = addItem(
                        mgr,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("TRAVEL_PACKAGE")
                                + "\",\"description\":\"Pacote\",\"quantity\":1,\"unitValue\":100.00}")
                .andReturn()
                .getResponse()
                .getContentAsString();
        String itemId = JsonPath.read(added, "$.items[0].id");

        String body = mvc.perform(put("/api/proposals/" + mgrProposal + "/items/" + itemId)
                        .header("Authorization", "Bearer " + mgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"" + proposalItemTypeId("TRAVEL_PACKAGE")
                                + "\",\"description\":\"Pacote\",\"quantity\":3,\"unitValue\":100.00}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.total")).isEqualByComparingTo("300.00");
    }

    @Test
    void removesAnItemAndRecomputesTheTotal() throws Exception {
        String mgr = manager();
        String added = addItem(
                        mgr,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("TRAVEL_PACKAGE")
                                + "\",\"description\":\"Pacote\",\"quantity\":2,\"unitValue\":100.00}")
                .andReturn()
                .getResponse()
                .getContentAsString();
        String itemId = JsonPath.read(added, "$.items[0].id");

        String body = mvc.perform(delete("/api/proposals/" + mgrProposal + "/items/" + itemId)
                        .header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<List<Object>>read(body, "$.items"))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(decimal(body, "$.total")).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsEditingItemsWhenTheProposalIsNotADraft() throws Exception {
        jdbc.update("UPDATE proposals SET status = 'SENT' WHERE id = cast(? as uuid)", mgrProposal.toString());
        addItem(
                        manager(),
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("proposal.not-editable"));
    }

    @Test
    void representativeCannotAddToAnotherUsersProposal() throws Exception {
        String rep = login("representante", "representante123");
        addItem(
                        rep,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("proposal.access-denied"));
    }

    @Test
    void financeHasNoAccessToItems() throws Exception {
        String fin = login("financeiro", "financeiro123");
        addItem(
                        fin,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}")
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWithoutTheUpdateScope() throws Exception {
        // diretor consults all (sales:proposal:read:all) but holds no sales:proposal:update scope.
        String dir = login("diretor", "diretor123");
        addItem(
                        dir,
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}")
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/proposals/" + mgrProposal + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesOnlyCommercialOfferItemFields() throws Exception {
        String body = addItem(
                        manager(),
                        mgrProposal,
                        "{\"typeId\":\"" + proposalItemTypeId("OTHER")
                                + "\",\"description\":\"x\",\"quantity\":1,\"unitValue\":10.00}")
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.Map<String, Object> item = JsonPath.read(body, "$.items[0]");
        org.assertj.core.api.Assertions.assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "type",
                        "typeLabel",
                        "description",
                        "quantity",
                        "unitValue",
                        "discountType",
                        "discountValue",
                        "lineTotal");
    }

    private ResultActions addItem(String token, UUID proposalId, String body) throws Exception {
        return mvc.perform(post("/api/proposals/" + proposalId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static BigDecimal decimal(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    private UUID insertProposal(UUID opportunityId, UUID leadId, UUID responsibleId) {
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
                responsibleId == null ? null : responsibleId.toString(),
                "Proposta " + opportunityId,
                MANAGER.toString(),
                MANAGER.toString());
        return id;
    }

    private UUID insertOpportunity(String name, String stage, UUID responsibleId, UUID leadId) {
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
                stage,
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
