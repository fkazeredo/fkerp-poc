import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from './lead.service';
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

/** Type of a Proposal item (commercial-offer line). */
export type ProposalItemType = 'TRAVEL_PACKAGE' | 'CAR_RENTAL' | 'SERVICE_FEE' | 'OTHER';

/** How a line discount is expressed — an absolute amount or a percentage. */
export type DiscountType = 'AMOUNT' | 'PERCENT';

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
}

/** Proposal list item (operational list of the Sales module). */
export interface ProposalListItem {
  id: string;
  opportunityId: string;
  title: string;
  status: ProposalStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  total: number;
  validUntil: string | null;
  createdAt: string;
}

/** API client for the commercial Proposal endpoints (Sales & Proposals). */
@Injectable({ providedIn: 'root' })
export class ProposalService {
  private readonly http = inject(HttpClient);

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

  list(page = 0, size = 20): Observable<PageResponse<ProposalListItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ProposalListItem>>('/api/proposals', { params });
  }
}
