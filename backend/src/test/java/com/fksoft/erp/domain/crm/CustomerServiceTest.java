package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.service.CustomerService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests of the Customer Application Service: idempotent materialization of a Customer from its Lead. */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customers;

    @Mock
    private LeadRepository leads;

    @InjectMocks
    private CustomerService service;

    @Test
    void findOrCreateReturnsTheExistingCustomerWithoutCreatingAnother() {
        UUID leadId = UUID.randomUUID();
        Customer existing = mock(Customer.class);
        when(customers.findByLeadId(leadId)).thenReturn(Optional.of(existing));

        Customer result = service.findOrCreateFromLead(leadId, UUID.randomUUID());

        assertThat(result).isSameAs(existing);
        verify(leads, never()).findById(any());
        verify(customers, never()).save(any());
    }

    @Test
    void findOrCreateMaterializesFromTheLeadWhenAbsent() {
        UUID leadId = UUID.randomUUID();
        UUID by = UUID.randomUUID();
        when(customers.findByLeadId(leadId)).thenReturn(Optional.empty());
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(leadId);
        when(lead.name()).thenReturn("Maria Silva");
        when(lead.email()).thenReturn("maria@example.com");
        when(leads.findById(leadId)).thenReturn(Optional.of(lead));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer result = service.findOrCreateFromLead(leadId, by);

        assertThat(result.leadId()).isEqualTo(leadId);
        assertThat(result.name()).isEqualTo("Maria Silva");
        assertThat(result.createdBy()).isEqualTo(by);
        verify(customers).save(any(Customer.class));
    }

    @Test
    void findOrCreateThrowsWhenTheSourceLeadDoesNotExist() {
        UUID leadId = UUID.randomUUID();
        when(customers.findByLeadId(leadId)).thenReturn(Optional.empty());
        when(leads.findById(leadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreateFromLead(leadId, UUID.randomUUID()))
                .isInstanceOf(LeadNotFoundException.class);
    }
}
