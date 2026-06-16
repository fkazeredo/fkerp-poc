import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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

/** API client for the commercial Opportunity endpoints. */
@Injectable({ providedIn: 'root' })
export class OpportunityService {
  private readonly http = inject(HttpClient);

  create(payload: CreateOpportunity): Observable<OpportunityCreated> {
    return this.http.post<OpportunityCreated>('/api/opportunities', payload);
  }
}
