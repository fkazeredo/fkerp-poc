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

  list(page = 0, size = 20): Observable<PageResponse<ProposalListItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ProposalListItem>>('/api/proposals', { params });
  }
}
