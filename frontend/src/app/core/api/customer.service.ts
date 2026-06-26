import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** The Customer lifecycle status (Customer Management — Sprint 7). */
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';

/** The Customer's preferred contact channel. */
export type ContactMethod = 'EMAIL' | 'PHONE' | 'WHATSAPP';

/** Full Customer Profile detail (Customer Management) — the customer master plus its preserved commercial origin. */
export interface CustomerDetail {
  id: string;
  leadId: string;
  sourceCommercialOrderId: string | null;
  sourceProposalId: string | null;
  sourceOpportunityId: string | null;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  preferredContactMethod: ContactMethod | null;
  document: string | null;
  documentType: string | null;
  billingAddress: string | null;
  notes: string | null;
  status: CustomerStatus;
  createdAt: string;
}

/** Payload to create or consolidate a Customer Profile from a Commercial Order (only the order id is required). */
export interface CreateCustomer {
  commercialOrderId: string;
  name?: string | null;
  document?: string | null;
  documentType?: string | null;
  email?: string | null;
  phone?: string | null;
  whatsapp?: string | null;
  preferredContactMethod?: ContactMethod | null;
  notes?: string | null;
}

/** API client for Customer Management. */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);

  /**
   * Creates or consolidates a Customer Profile from a Commercial Order, returning the consolidated profile.
   *
   * @param payload the source order id plus the optional profile fields
   */
  createFromOrder(payload: CreateCustomer): Observable<CustomerDetail> {
    return this.http.post<CustomerDetail>('/api/customers', payload);
  }
}
