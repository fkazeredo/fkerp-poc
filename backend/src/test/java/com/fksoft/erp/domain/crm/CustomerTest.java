package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.ContactMethod;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.CustomerStatus;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.service.data.CreateCustomerCommand;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Customer aggregate (Customer Management — the commercial graduation of a Lead). */
class CustomerTest {

    @Test
    void fromLeadSnapshotsTheLeadIdNameAndContactsAndStartsActive() {
        UUID leadId = UUID.randomUUID();
        UUID by = UUID.randomUUID();
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(leadId);
        when(lead.name()).thenReturn("Maria Silva");
        when(lead.phone()).thenReturn("11999999999");
        when(lead.whatsapp()).thenReturn(null);
        when(lead.email()).thenReturn("maria@example.com");

        Customer customer = Customer.fromLead(lead, by);

        assertThat(customer.id()).isNotNull();
        assertThat(customer.leadId()).isEqualTo(leadId);
        assertThat(customer.name()).isEqualTo("Maria Silva");
        assertThat(customer.phone()).isEqualTo("11999999999");
        assertThat(customer.whatsapp()).isNull();
        assertThat(customer.email()).isEqualTo("maria@example.com");
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.createdBy()).isEqualTo(by);
        assertThat(customer.updatedBy()).isEqualTo(by);
        // The document, billing address and commercial origin are placeholders until consolidation — empty now.
        assertThat(customer.document()).isNull();
        assertThat(customer.documentType()).isNull();
        assertThat(customer.billingAddress()).isNull();
        assertThat(customer.sourceCommercialOrderId()).isNull();
        assertThat(customer.sourceProposalId()).isNull();
        assertThat(customer.sourceOpportunityId()).isNull();
        assertThat(customer.preferredContactMethod()).isNull();
        assertThat(customer.notes()).isNull();
    }

    @Test
    void consolidateFromOrderPreservesTheCommercialOriginAndEnrichesTheProfile() {
        Customer customer =
                Customer.fromLead(lead("Maria Silva", "11999999999", "maria@example.com"), UUID.randomUUID());
        UUID editor = UUID.randomUUID();
        CommercialOrder order = order();

        customer.consolidateFromOrder(
                order,
                new CreateCustomerCommand(
                        order.id(),
                        "Maria S. Silva",
                        "12345678901",
                        "CPF",
                        "maria.new@example.com",
                        "11888888888",
                        "11777777777",
                        ContactMethod.WHATSAPP,
                        "VIP client"),
                editor);

        // Commercial origin preserved from the order.
        assertThat(customer.sourceCommercialOrderId()).isEqualTo(order.id());
        assertThat(customer.sourceProposalId()).isEqualTo(order.proposalId());
        assertThat(customer.sourceOpportunityId()).isEqualTo(order.opportunityId());
        // Profile enriched with the supplied data.
        assertThat(customer.name()).isEqualTo("Maria S. Silva");
        assertThat(customer.document()).isEqualTo("12345678901");
        assertThat(customer.documentType()).isEqualTo("CPF");
        assertThat(customer.email()).isEqualTo("maria.new@example.com");
        assertThat(customer.phone()).isEqualTo("11888888888");
        assertThat(customer.whatsapp()).isEqualTo("11777777777");
        assertThat(customer.preferredContactMethod()).isEqualTo(ContactMethod.WHATSAPP);
        assertThat(customer.notes()).isEqualTo("VIP client");
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.updatedBy()).isEqualTo(editor);
    }

    @Test
    void consolidateKeepsTheLeadSnapshotForFieldsLeftAbsent() {
        Customer customer =
                Customer.fromLead(lead("Maria Silva", "11999999999", "maria@example.com"), UUID.randomUUID());
        CommercialOrder order = order();

        // All profile fields null — only the source order id is provided.
        customer.consolidateFromOrder(
                order,
                new CreateCustomerCommand(order.id(), null, null, null, null, null, null, null, null),
                UUID.randomUUID());

        assertThat(customer.name()).isEqualTo("Maria Silva");
        assertThat(customer.phone()).isEqualTo("11999999999");
        assertThat(customer.email()).isEqualTo("maria@example.com");
        assertThat(customer.sourceCommercialOrderId()).isEqualTo(order.id());
    }

    @Test
    void reconsolidatingFromAnotherOrderKeepsTheOriginalCommercialOrigin() {
        Customer customer = Customer.fromLead(lead("Maria Silva", null, "maria@example.com"), UUID.randomUUID());
        CommercialOrder first = order();
        CommercialOrder second = order();

        customer.consolidateFromOrder(
                first,
                new CreateCustomerCommand(first.id(), null, null, null, null, null, null, null, null),
                UUID.randomUUID());
        customer.consolidateFromOrder(
                second,
                new CreateCustomerCommand(second.id(), null, null, null, null, null, null, null, null),
                UUID.randomUUID());

        // The origin is the first order — preserved, not overwritten by the second consolidation.
        assertThat(customer.sourceCommercialOrderId()).isEqualTo(first.id());
        assertThat(customer.sourceProposalId()).isEqualTo(first.proposalId());
        assertThat(customer.sourceOpportunityId()).isEqualTo(first.opportunityId());
    }

    private static Lead lead(String name, String phone, String email) {
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(UUID.randomUUID());
        when(lead.name()).thenReturn(name);
        when(lead.phone()).thenReturn(phone);
        when(lead.whatsapp()).thenReturn(null);
        when(lead.email()).thenReturn(email);
        return lead;
    }

    private static CommercialOrder order() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.id()).thenReturn(UUID.randomUUID());
        when(order.proposalId()).thenReturn(UUID.randomUUID());
        when(order.opportunityId()).thenReturn(UUID.randomUUID());
        return order;
    }
}
