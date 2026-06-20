import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';
import { OpportunityStage } from './opportunity.service';

/** The 8-state commercial Proposal lifecycle. A new Proposal starts at DRAFT. */
export type ProposalStatus =
  | 'DRAFT'
  | 'READY_FOR_REVIEW'
  | 'APPROVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED';

/** Payload to create a Proposal from a READY_FOR_PROPOSAL Opportunity. */
export interface CreateProposal {
  opportunityId: string;
  responsiblePersonId?: string | null;
  title: string;
  notes?: string | null;
  validUntil?: string | null;
  commercialTerms?: string | null;
}

export interface ProposalCreated {
  id: string;
  status: ProposalStatus;
}

/** The source Opportunity, kept traceable from the Proposal. */
export interface ProposalSourceOpportunity {
  id: string;
  name: string;
  stage: OpportunityStage;
}

/** The source Lead, kept traceable from the Proposal (the contact's system of record). */
export interface ProposalSourceLead {
  id: string;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  status: string;
}

/** A single Proposal status-change entry (from → to, when, by whom). Newest first in the detail. */
export interface ProposalStatusChange {
  from: ProposalStatus;
  to: ProposalStatus;
  at: string;
  by: string | null;
}

/** Type of a Proposal item (commercial-offer line). */
export type ProposalItemType = 'TRAVEL_PACKAGE' | 'CAR_RENTAL' | 'SERVICE_FEE' | 'OTHER';

/** How a line discount is expressed — an absolute amount or a percentage. */
export type DiscountType = 'AMOUNT' | 'PERCENT';

/** The fixed set of internal-review rejection reasons. */
export type ProposalRejectionReason =
  | 'PRICE_TOO_HIGH'
  | 'DISCOUNT_OUT_OF_POLICY'
  | 'INCOMPLETE_INFORMATION'
  | 'TERMS_NOT_ACCEPTABLE'
  | 'VALIDITY_TOO_SHORT'
  | 'DUPLICATE'
  | 'OTHER';

/** The fixed set of channels an approved Proposal can be sent/presented to the client through. */
export type SendingChannel =
  | 'EMAIL'
  | 'WHATSAPP'
  | 'PHONE_PRESENTATION'
  | 'IN_PERSON_PRESENTATION'
  | 'OTHER';

/** The fixed set of reasons a client can reject a sent Proposal (distinct from the internal-review reasons). */
export type CustomerRejectionReason =
  | 'PRICE_TOO_HIGH'
  | 'CHOSE_COMPETITOR'
  | 'TRAVEL_POSTPONED'
  | 'TRAVEL_CANCELLED'
  | 'CHANGED_DESTINATION'
  | 'NO_RESPONSE'
  | 'PRODUCT_MISMATCH'
  | 'OTHER';

/** A single commercial-offer line, with its computed line total. */
export interface ProposalItem {
  id: string;
  type: ProposalItemType;
  description: string;
  quantity: number;
  unitValue: number;
  discountType: DiscountType | null;
  discountValue: number | null;
  lineTotal: number;
}

/** Payload to add or update a Proposal item. */
export interface ProposalItemPayload {
  type: ProposalItemType;
  description: string;
  quantity: number;
  unitValue: number;
  discountType?: DiscountType | null;
  discountValue?: number | null;
}

/** Payload to edit a Draft Proposal's commercial details (validity, terms, payment notes, discount). */
export interface UpdateProposal {
  validUntil?: string | null;
  commercialTerms?: string | null;
  paymentNotes?: string | null;
  discountType?: DiscountType | null;
  discountValue?: number | null;
}

/** Full Proposal detail. Exposes commercial-offer data only — never sale/order/booking/financial. */
export interface ProposalDetail {
  id: string;
  opportunityId: string;
  leadId: string;
  status: ProposalStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  title: string;
  notes: string | null;
  validUntil: string | null;
  commercialTerms: string | null;
  paymentNotes: string | null;
  items: ProposalItem[];
  subtotal: number;
  discountType: DiscountType | null;
  discountValue: number | null;
  total: number;
  createdAt: string;
  updatedAt: string;
  sourceOpportunity: ProposalSourceOpportunity;
  sourceLead: ProposalSourceLead;
  statusHistory: ProposalStatusChange[];
  rejectionReason: ProposalRejectionReason | null;
  rejectionNote: string | null;
  sendingChannel: SendingChannel | null;
  acceptanceNote: string | null;
  customerRejectionReason: CustomerRejectionReason | null;
  customerRejectionNote: string | null;
  commercialOrderId: string | null;
}

/** Proposal list item (operational list of the Sales module). */
export interface ProposalListItem {
  id: string;
  opportunityId: string;
  opportunityName: string | null;
  title: string;
  status: ProposalStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  total: number;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Operational Proposal-list filters. Empty {@code status} excludes the terminal-negative outcomes
 * (REJECTED/EXPIRED/CANCELLED); include them to see inactive Proposals. {@code responsible} is a user id
 * or the literal `unassigned`; the date bounds are ISO `yyyy-MM-dd`. {@code q} searches the Proposal title
 * and the source Opportunity name.
 */
export interface ProposalFilters {
  status?: ProposalStatus[];
  responsible?: string | null;
  opportunityId?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  totalMin?: number | null;
  totalMax?: number | null;
  q?: string | null;
}

/** Proposal count for a lifecycle status (volume, in period). */
export interface ProposalStatusCount {
  status: ProposalStatus;
  count: number;
}

/** Proposal count for a responsible person; `responsibleName === null` means unassigned. */
export interface ProposalResponsibleCount {
  responsibleName: string | null;
  count: number;
}

/**
 * Minimum Proposal-flow indicators. Two scopes: the **volume** figures ({@code total}, {@code byStatus},
 * {@code byResponsible}, {@code proposedAmount}, {@code acceptedAmount}, {@code rejectedCount}) cover the
 * selected period (by creation date); the **operational** figures ({@code waitingForReview},
 * {@code waitingForCustomerDecision}) are a current snapshot of all the visible Proposals, independent of
 * the period.
 */
export interface ProposalIndicators {
  total: number;
  byStatus: ProposalStatusCount[];
  byResponsible: ProposalResponsibleCount[];
  proposedAmount: number;
  acceptedAmount: number;
  rejectedCount: number;
  waitingForReview: number;
  waitingForCustomerDecision: number;
}

/** API client for the commercial Proposal endpoints (Sales & Proposals). */
@Injectable({ providedIn: 'root' })
export class ProposalService {
  private readonly http = inject(HttpClient);

  /** Minimum Proposal-flow indicators in an optional period (ISO dates); absent dates = all-time. */
  indicators(
    createdFrom: string | null = null,
    createdTo: string | null = null,
  ): Observable<ProposalIndicators> {
    let params = new HttpParams();
    if (createdFrom) {
      params = params.set('createdFrom', createdFrom);
    }
    if (createdTo) {
      params = params.set('createdTo', createdTo);
    }
    return this.http.get<ProposalIndicators>('/api/proposals/indicators', { params });
  }

  /** The selectable responsible people (shared with the CRM module). */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  /** Creates a Proposal from a READY_FOR_PROPOSAL Opportunity. */
  create(payload: CreateProposal): Observable<ProposalCreated> {
    return this.http.post<ProposalCreated>('/api/proposals', payload);
  }

  detail(id: string): Observable<ProposalDetail> {
    return this.http.get<ProposalDetail>(`/api/proposals/${id}`);
  }

  /** Edits a Draft Proposal's commercial details (validity/terms/payment notes/discount); returns the detail. */
  updateDetails(id: string, payload: UpdateProposal): Observable<ProposalDetail> {
    return this.http.put<ProposalDetail>(`/api/proposals/${id}`, payload);
  }

  /** Submits a Draft Proposal for review (requires items and a positive total); returns the detail. */
  submitForReview(id: string): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/submit`, {});
  }

  /** Approves a Proposal under review (Ready for review → Approved); returns the detail. */
  approve(id: string): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/approve`, {});
  }

  /** Rejects a Proposal under review with a reason (and optional note); returns the detail. */
  reject(
    id: string,
    reason: ProposalRejectionReason,
    note: string | null,
  ): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/reject`, { reason, note });
  }

  /** Marks an approved Proposal as sent to the client (Approved → Sent); the channel is optional. */
  markSent(id: string, channel: SendingChannel | null): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/send`, { channel });
  }

  /** Registers that the client accepted a sent Proposal (Sent → Accepted); the note is optional. */
  accept(id: string, note: string | null): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/accept`, { note });
  }

  /** Registers that the client rejected a sent Proposal (Sent → Rejected) with a reason (+ optional note). */
  decline(
    id: string,
    reason: CustomerRejectionReason,
    note: string | null,
  ): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/decline`, { reason, note });
  }

  /** Adds an item to a Draft Proposal; returns the refreshed detail (with the recomputed total). */
  addItem(id: string, payload: ProposalItemPayload): Observable<ProposalDetail> {
    return this.http.post<ProposalDetail>(`/api/proposals/${id}/items`, payload);
  }

  /** Updates an item of a Draft Proposal; returns the refreshed detail. */
  updateItem(id: string, itemId: string, payload: ProposalItemPayload): Observable<ProposalDetail> {
    return this.http.put<ProposalDetail>(`/api/proposals/${id}/items/${itemId}`, payload);
  }

  /** Removes an item from a Draft Proposal; returns the refreshed detail. */
  removeItem(id: string, itemId: string): Observable<ProposalDetail> {
    return this.http.delete<ProposalDetail>(`/api/proposals/${id}/items/${itemId}`);
  }

  /** Operational, paginated Proposal list filtered by the given criteria and the caller's visibility. */
  list(
    filters: ProposalFilters,
    page = 0,
    size = 20,
  ): Observable<PageResponse<ProposalListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    if (filters.responsible) {
      params = params.set('responsible', filters.responsible);
    }
    if (filters.opportunityId) {
      params = params.set('opportunityId', filters.opportunityId);
    }
    if (filters.createdFrom) {
      params = params.set('createdFrom', filters.createdFrom);
    }
    if (filters.createdTo) {
      params = params.set('createdTo', filters.createdTo);
    }
    if (filters.validFrom) {
      params = params.set('validFrom', filters.validFrom);
    }
    if (filters.validTo) {
      params = params.set('validTo', filters.validTo);
    }
    if (filters.totalMin != null) {
      params = params.set('totalMin', filters.totalMin);
    }
    if (filters.totalMax != null) {
      params = params.set('totalMax', filters.totalMax);
    }
    if (filters.q && filters.q.trim().length > 0) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<ProposalListItem>>('/api/proposals', { params });
  }
}
