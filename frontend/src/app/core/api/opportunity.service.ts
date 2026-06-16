import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from './lead.service';

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

/** Operational Opportunity-list filters. Empty {@code stage} excludes LOST (it shows only when chosen). */
export interface OpportunityFilters {
  stage?: OpportunityStage[];
  q?: string | null;
}

/** API client for the commercial Opportunity endpoints. */
@Injectable({ providedIn: 'root' })
export class OpportunityService {
  private readonly http = inject(HttpClient);

  create(payload: CreateOpportunity): Observable<OpportunityCreated> {
    return this.http.post<OpportunityCreated>('/api/opportunities', payload);
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
    if (filters.q && filters.q.trim().length > 0) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<OpportunityListItem>>('/api/opportunities', { params });
  }
}
