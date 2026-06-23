package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Lead;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Customer aggregate (the commercial graduation of a Lead). */
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
        assertThat(customer.active()).isTrue();
        assertThat(customer.createdBy()).isEqualTo(by);
        assertThat(customer.updatedBy()).isEqualTo(by);
        // The document and billing address are placeholders for a later slice — empty now.
        assertThat(customer.document()).isNull();
        assertThat(customer.documentType()).isNull();
        assertThat(customer.billingAddress()).isNull();
    }
}
