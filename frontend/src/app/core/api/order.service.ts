import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OpportunityStage } from './opportunity.service';
import { DiscountType, ProposalItemType, ProposalStatus } from './proposal.service';

/** The Commercial Order lifecycle status (Sprint 3). */
export type CommercialOrderStatus = 'PENDING_BOOKING' | 'BOOKING_NOT_REQUIRED' | 'CANCELLED';

/** A single order line — a snapshot of a Proposal item, with its computed line total. */
export interface CommercialOrderItem {
  id: string;
  type: ProposalItemType;
  description: string;
  quantity: number;
  unitValue: number;
  discountType: DiscountType | null;
  discountValue: number | null;
  lineTotal: number;
}

/** The source Proposal, preserved by the Order. */
export interface OrderSourceProposal {
  id: string;
  title: string;
  status: ProposalStatus;
}

/** The source Opportunity, kept traceable from the Order (now won). */
export interface OrderSourceOpportunity {
  id: string;
  name: string;
  stage: OpportunityStage;
}

/** The source Lead, kept traceable from the Order. */
export interface OrderSourceLead {
  id: string;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  status: string;
}

/** Full Commercial Order detail — the formal record of the closed deal. */
export interface CommercialOrderDetail {
  id: string;
  proposalId: string;
  opportunityId: string;
  leadId: string;
  status: CommercialOrderStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  items: CommercialOrderItem[];
  subtotal: number;
  total: number;
  createdAt: string;
  createdByName: string | null;
  sourceProposal: OrderSourceProposal;
  sourceOpportunity: OrderSourceOpportunity;
  sourceLead: OrderSourceLead;
}

/** Payload to create a Commercial Order from an Accepted Proposal. */
export interface CreateOrder {
  proposalId: string;
}

export interface OrderCreated {
  id: string;
}

/** API client for the Commercial Order endpoints (Sales & Proposals). */
@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);

  /** Creates a Commercial Order from an Accepted Proposal; returns the new order id. */
  create(payload: CreateOrder): Observable<OrderCreated> {
    return this.http.post<OrderCreated>('/api/orders', payload);
  }

  detail(id: string): Observable<CommercialOrderDetail> {
    return this.http.get<CommercialOrderDetail>(`/api/orders/${id}`);
  }
}
