package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.model.ContactMethod;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.service.CustomerService;
import com.fksoft.erp.domain.crm.service.data.CreateCustomerCommand;
import com.fksoft.erp.domain.crm.service.data.CustomerDetail;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the Customer Application Service: idempotent materialization of a Customer from its Lead, and the
 * create/consolidate of a Customer Profile from a Commercial Order.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customers;

    @Mock
    private LeadRepository leads;

    @Mock
    private CommercialOrderRepository orders;

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

    @Test
    void createFromOrderConsolidatesTheExistingCustomerForTheOrdersLead() {
        UUID leadId = UUID.randomUUID();
        UUID by = UUID.randomUUID();
        CommercialOrder order = mock(CommercialOrder.class);
        UUID orderId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        when(order.id()).thenReturn(orderId);
        when(order.leadId()).thenReturn(leadId);
        when(order.proposalId()).thenReturn(proposalId);
        when(order.opportunityId()).thenReturn(opportunityId);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(leadId);
        when(lead.name()).thenReturn("Maria Silva");
        Customer existing = Customer.fromLead(lead, by);
        when(customers.findByLeadId(leadId)).thenReturn(Optional.of(existing));
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDetail detail = service.createFromOrder(
                new CreateCustomerCommand(
                        orderId, "Maria S.", "123", "CPF", null, null, null, ContactMethod.EMAIL, "vip"),
                by);

        assertThat(detail.sourceCommercialOrderId()).isEqualTo(orderId);
        assertThat(detail.sourceProposalId()).isEqualTo(proposalId);
        assertThat(detail.sourceOpportunityId()).isEqualTo(opportunityId);
        assertThat(detail.name()).isEqualTo("Maria S.");
        assertThat(detail.document()).isEqualTo("123");
        assertThat(detail.preferredContactMethod()).isEqualTo(ContactMethod.EMAIL);
        assertThat(detail.notes()).isEqualTo("vip");
        verify(customers).save(existing);
    }

    @Test
    void createFromOrderThrowsWhenTheSourceOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromOrder(
                        new CreateCustomerCommand(orderId, null, null, null, null, null, null, null, null),
                        UUID.randomUUID()))
                .isInstanceOf(CommercialOrderNotFoundException.class);
        verify(customers, never()).save(any());
    }
}
