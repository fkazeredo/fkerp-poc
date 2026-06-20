import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';
import { OpportunityStage } from './opportunity.service';
import { DiscountType, ProposalItemType, ProposalStatus } from './proposal.service';

/** The Commercial Order lifecycle status (Sprint 3). */
export type CommercialOrderStatus = 'PENDING_BOOKING' | 'BOOKING_NOT_REQUIRED' | 'CANCELLED';

/** The booking-need filter — maps to the PENDING_BOOKING / BOOKING_NOT_REQUIRED status. */
export type BookingNeed = 'REQUIRED' | 'NOT_REQUIRED';

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

/** Commercial Order list item (operational list of the Sales module). */
export interface CommercialOrderListItem {
  id: string;
  number: number;
  proposalId: string;
  proposalTitle: string | null;
  opportunityId: string;
  opportunityName: string | null;
  status: CommercialOrderStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  total: number;
  requiresBooking: boolean;
  createdAt: string;
}

/**
 * Operational Commercial Order-list filters. Empty {@code status} excludes the CANCELLED Orders; include it
 * to see cancelled Orders. {@code responsible} is a user id or the literal `unassigned`; the date bounds are
 * ISO `yyyy-MM-dd`. {@code bookingNeed} narrows by booking need; {@code q} searches the source Proposal title.
 */
export interface OrderFilters {
  status?: CommercialOrderStatus[];
  responsible?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  totalMin?: number | null;
  totalMax?: number | null;
  bookingNeed?: BookingNeed | null;
  q?: string | null;
}

/** Full Commercial Order detail — the formal record of the closed deal. */
export interface CommercialOrderDetail {
  id: string;
  number: number;
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

  /** The selectable responsible people (shared with the CRM module). */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  /** Creates a Commercial Order from an Accepted Proposal; returns the new order id. */
  create(payload: CreateOrder): Observable<OrderCreated> {
    return this.http.post<OrderCreated>('/api/orders', payload);
  }

  detail(id: string): Observable<CommercialOrderDetail> {
    return this.http.get<CommercialOrderDetail>(`/api/orders/${id}`);
  }

  /** Operational, paginated Commercial Order list filtered by the given criteria and the caller's visibility. */
  list(filters: OrderFilters, page = 0, size = 20): Observable<PageResponse<CommercialOrderListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    if (filters.responsible) {
      params = params.set('responsible', filters.responsible);
    }
    if (filters.createdFrom) {
      params = params.set('createdFrom', filters.createdFrom);
    }
    if (filters.createdTo) {
      params = params.set('createdTo', filters.createdTo);
    }
    if (filters.totalMin != null) {
      params = params.set('totalMin', filters.totalMin);
    }
    if (filters.totalMax != null) {
      params = params.set('totalMax', filters.totalMax);
    }
    if (filters.bookingNeed) {
      params = params.set('bookingNeed', filters.bookingNeed);
    }
    if (filters.q && filters.q.trim().length > 0) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<CommercialOrderListItem>>('/api/orders', { params });
  }
}
