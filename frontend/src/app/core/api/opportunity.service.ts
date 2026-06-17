import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Origin, PageResponse, Responsible } from './lead.service';

export type OpportunityStage =
  | 'NEW_OPPORTUNITY'
  | 'DISCOVERY'
  | 'PRODUCT_FIT'
  | 'READY_FOR_PROPOSAL'
  | 'LOST';

/** Payload to create an Opportunity from a Qualified Lead. */
export interface CreateOpportunity {
  leadId: string;
  responsiblePersonId?: string | null;
  productType?: string | null;
  estimatedValue?: number | null;
  expectedCloseDate?: string | null;
  initialNote?: string | null;
}

export interface OpportunityCreated {
  id: string;
  stage: OpportunityStage;
}

/**
 * Operational Opportunity list item. {@code leadId} links to the source Lead (still the system of
 * record for contacts/history). {@code lastActivityAt}/{@code nextActionDate} are reserved for the
 * future Opportunity-activities slice and are always null for now.
 */
export interface OpportunityListItem {
  id: string;
  leadId: string;
  name: string;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  stage: OpportunityStage;
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  createdAt: string;
  lastActivityAt: string | null;
  nextActionDate: string | null;
}

/** The source Lead, kept traceable from the Opportunity detail. */
export interface OpportunitySourceLead {
  id: string;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  status: string;
}

/** Loss outcome, present only when the Opportunity is LOST. */
export interface OpportunityLoss {
  reason: string | null;
  lostAt: string;
  lostBy: string | null;
  note: string | null;
}

/** A single pipeline stage-movement entry (newest first in the detail). */
export interface OpportunityStageChange {
  from: OpportunityStage;
  to: OpportunityStage;
  at: string;
  by: string | null;
}

export type OpportunityActivityType =
  | 'PHONE_CALL'
  | 'WHATSAPP'
  | 'EMAIL'
  | 'MEETING'
  | 'INTERNAL_NOTE'
  | 'DOCUMENT_REQUEST'
  | 'PRICE_DISCUSSION'
  | 'TRAVEL_REQUIREMENT_CLARIFICATION'
  | 'OTHER';

export type OpportunityActivityResult =
  | 'CLIENT_ENGAGED'
  | 'NEEDS_FOLLOW_UP'
  | 'WAITING_FOR_CLIENT'
  | 'WAITING_FOR_INTERNAL_INFO'
  | 'PRODUCT_FIT_IDENTIFIED'
  | 'READY_FOR_PROPOSAL'
  | 'NOT_INTERESTED'
  | 'OTHER';

/** A single commercial activity in the negotiation history (newest first in the detail). */
export interface OpportunityActivity {
  id: string;
  type: OpportunityActivityType;
  result: OpportunityActivityResult;
  description: string;
  occurredAt: string;
  nextActionDate: string | null;
  registeredBy: string | null;
}

/** Payload to register a commercial activity on an Opportunity. */
export interface RegisterActivity {
  type: OpportunityActivityType;
  result: OpportunityActivityResult;
  description: string;
  occurredAt: string;
  nextActionDate: string | null;
}

/** Payload to edit an Opportunity's commercial details (each field; null clears it). */
export interface UpdateOpportunityDetails {
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  productType: string | null;
  notes: string | null;
}

/**
 * Full Opportunity detail. {@code loss} is present only when LOST; {@code activities},
 * {@code stageHistory} and {@code nextActionDate} are reserved for future slices (empty/null for now).
 */
export interface OpportunityDetail {
  id: string;
  leadId: string;
  name: string;
  stage: OpportunityStage;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  origin: string;
  mainInterest: string | null;
  productType: string | null;
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  sourceLead: OpportunitySourceLead;
  loss: OpportunityLoss | null;
  stageHistory: OpportunityStageChange[];
  activities: OpportunityActivity[];
  nextActionDate: string | null;
}

/**
 * Operational Opportunity-list filters. Empty {@code stage} excludes LOST (it shows only when chosen).
 * {@code responsible} is a user id or the literal `unassigned`; the date bounds are ISO `yyyy-MM-dd`.
 */
export interface OpportunityFilters {
  stage?: OpportunityStage[];
  responsible?: string | null;
  originId?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  closeFrom?: string | null;
  closeTo?: string | null;
  valueMin?: number | null;
  valueMax?: number | null;
  q?: string | null;
}

/** API client for the commercial Opportunity endpoints. */
@Injectable({ providedIn: 'root' })
export class OpportunityService {
  private readonly http = inject(HttpClient);

  origins(): Observable<Origin[]> {
    return this.http.get<Origin[]>('/api/crm/origins');
  }

  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  create(payload: CreateOpportunity): Observable<OpportunityCreated> {
    return this.http.post<OpportunityCreated>('/api/opportunities', payload);
  }

  detail(id: string): Observable<OpportunityDetail> {
    return this.http.get<OpportunityDetail>(`/api/opportunities/${id}`);
  }

  lose(id: string, lossReasonId: string, note: string | null): Observable<OpportunityDetail> {
    return this.http.post<OpportunityDetail>(`/api/opportunities/${id}/lose`, { lossReasonId, note });
  }

  changeStage(id: string, stage: OpportunityStage): Observable<OpportunityDetail> {
    return this.http.post<OpportunityDetail>(`/api/opportunities/${id}/stage`, { stage });
  }

  registerActivity(id: string, activity: RegisterActivity): Observable<OpportunityDetail> {
    return this.http.post<OpportunityDetail>(`/api/opportunities/${id}/activities`, activity);
  }

  updateDetails(id: string, details: UpdateOpportunityDetails): Observable<OpportunityDetail> {
    return this.http.put<OpportunityDetail>(`/api/opportunities/${id}`, details);
  }

  list(
    filters: OpportunityFilters,
    page = 0,
    size = 20,
  ): Observable<PageResponse<OpportunityListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const stage of filters.stage ?? []) {
      params = params.append('stage', stage);
    }
    if (filters.responsible) {
      params = params.set('responsible', filters.responsible);
    }
    if (filters.originId) {
      params = params.set('originId', filters.originId);
    }
    if (filters.createdFrom) {
      params = params.set('createdFrom', filters.createdFrom);
    }
    if (filters.createdTo) {
      params = params.set('createdTo', filters.createdTo);
    }
    if (filters.closeFrom) {
      params = params.set('closeFrom', filters.closeFrom);
    }
    if (filters.closeTo) {
      params = params.set('closeTo', filters.closeTo);
    }
    if (filters.valueMin != null) {
      params = params.set('valueMin', filters.valueMin);
    }
    if (filters.valueMax != null) {
      params = params.set('valueMax', filters.valueMax);
    }
    if (filters.q && filters.q.trim().length > 0) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<OpportunityListItem>>('/api/opportunities', { params });
  }
}
