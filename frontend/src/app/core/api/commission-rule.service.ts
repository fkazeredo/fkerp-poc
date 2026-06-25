import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Responsible } from './lead.service';

/** The commercial actor a Commission Rule targets. */
export type CommissionTargetType = 'SELLER' | 'SALES_REPRESENTATIVE' | 'COMMERCIAL_RESPONSIBLE';

/** A Commission Rule in the management list. Rule (configuration) data only — never Commission/Payment data. */
export interface CommissionRuleListItem {
  id: string;
  name: string;
  percentage: number;
  targetType: CommissionTargetType;
  targetUserId: string | null;
  targetUserName: string | null;
  active: boolean;
  startDate: string;
  endDate: string | null;
}

/** Full detail of a Commission Rule. */
export interface CommissionRuleDetail extends CommissionRuleListItem {
  notes: string | null;
  createdAt: string;
}

/** Payload to create or update a Commission Rule. */
export interface SaveCommissionRule {
  name: string;
  percentage: number;
  targetType: CommissionTargetType;
  targetUserId?: string | null;
  startDate: string;
  endDate?: string | null;
  notes?: string | null;
  allowAboveLimit?: boolean;
}

export interface CommissionRuleCreated {
  id: string;
  active: boolean;
}

/** API client for the Commission Rule endpoints (Commission Management, Sprint 6). */
@Injectable({ providedIn: 'root' })
export class CommissionRuleService {
  private readonly http = inject(HttpClient);

  /** Lists the commission rules; by default only active ones. */
  list(includeInactive = false): Observable<CommissionRuleListItem[]> {
    const params = new HttpParams().set('includeInactive', includeInactive);
    return this.http.get<CommissionRuleListItem[]>('/api/commission/rules', { params });
  }

  detail(id: string): Observable<CommissionRuleDetail> {
    return this.http.get<CommissionRuleDetail>(`/api/commission/rules/${id}`);
  }

  create(payload: SaveCommissionRule): Observable<CommissionRuleCreated> {
    return this.http.post<CommissionRuleCreated>('/api/commission/rules', payload);
  }

  update(id: string, payload: SaveCommissionRule): Observable<CommissionRuleDetail> {
    return this.http.put<CommissionRuleDetail>(`/api/commission/rules/${id}`, payload);
  }

  activate(id: string): Observable<CommissionRuleDetail> {
    return this.http.post<CommissionRuleDetail>(`/api/commission/rules/${id}/activate`, {});
  }

  deactivate(id: string): Observable<CommissionRuleDetail> {
    return this.http.post<CommissionRuleDetail>(`/api/commission/rules/${id}/deactivate`, {});
  }

  /** The selectable responsible people (shared CRM lookup) for targeting a specific user. */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }
}
