import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Origin {
  id: string;
  code: string;
  label: string;
}

export interface Responsible {
  id: string;
  name: string;
}

export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'LOST';

export type PendingReason =
  | 'UNASSIGNED'
  | 'NEW_WITHOUT_INTERACTION'
  | 'OVERDUE_NEXT_CONTACT'
  | 'CONTACTED_WITHOUT_OUTCOME';

export interface PendingItem {
  id: string;
  name: string;
  mainContact: string | null;
  status: LeadStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  createdAt: string;
  nextContactAt: string | null;
  reasons: PendingReason[];
}

export interface CreateLead {
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  originId: string;
  responsiblePersonId: string | null;
  initialNote: string | null;
}

export interface LeadCreated {
  id: string;
  name: string;
  status: string;
}

export interface LeadListItem {
  id: string;
  name: string;
  mainContact: string | null;
  origin: string;
  status: LeadStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  createdAt: string;
  lastInteractionAt: string | null;
  lastInteractionType: string | null;
  nextContactAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface OriginCount {
  origin: string;
  count: number;
}

/** {@code responsibleName === null} means the unassigned bucket. */
export interface ResponsibleCount {
  responsibleName: string | null;
  count: number;
}

/** Minimum top-of-funnel indicators over the Leads the caller can see, in an optional period. */
export interface LeadIndicators {
  total: number;
  newLeads: number;
  contacted: number;
  qualified: number;
  lost: number;
  waitingFirstContact: number;
  byOrigin: OriginCount[];
  byResponsible: ResponsibleCount[];
}

/** Operational Lead-list filters. {@code responsible} is a user id or the literal `unassigned`. */
export interface LeadFilters {
  status?: LeadStatus[];
  originId?: string | null;
  responsible?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  q?: string | null;
}

export interface InteractionItem {
  id: string;
  type: string;
  result: string | null;
  content: string | null;
  occurredAt: string;
  nextContactAt: string | null;
  registeredBy: string | null;
}

/** Payload to register a Lead interaction. Dates are ISO-8601 instants. */
export interface RegisterInteraction {
  typeId: string;
  resultId: string;
  description: string;
  occurredAt: string;
  nextContactAt: string | null;
}

export interface AssignmentItem {
  from: string | null;
  to: string | null;
  by: string | null;
  at: string;
}

export interface QualificationInfo {
  qualifiedAt: string;
  qualifiedBy: string | null;
  mainInterest: string;
  note: string | null;
}

export interface LossInfo {
  reason: string | null;
  lostAt: string;
  lostBy: string | null;
  note: string | null;
}

export interface LeadDetail {
  id: string;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  origin: string;
  status: LeadStatus;
  responsibleId: string | null;
  responsibleName: string | null;
  unassigned: boolean;
  createdAt: string;
  updatedAt: string;
  nextContactAt: string | null;
  interactions: InteractionItem[];
  assignments: AssignmentItem[];
  qualification: QualificationInfo | null;
  loss: LossInfo | null;
}

/** API client for the Commercial / CRM lead endpoints. */
@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);

  origins(): Observable<Origin[]> {
    return this.http.get<Origin[]>('/api/crm/origins');
  }

  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  create(lead: CreateLead): Observable<LeadCreated> {
    return this.http.post<LeadCreated>('/api/leads', lead);
  }

  list(filters: LeadFilters, page = 0, size = 20): Observable<PageResponse<LeadListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    if (filters.originId) {
      params = params.set('originId', filters.originId);
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
    if (filters.q && filters.q.trim().length > 0) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<LeadListItem>>('/api/leads', { params });
  }

  pending(page = 0, size = 20): Observable<PageResponse<PendingItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<PendingItem>>('/api/leads/pending', { params });
  }

  /** Top-of-funnel indicators in an optional period (ISO dates); absent dates mean all-time. */
  indicators(
    createdFrom: string | null = null,
    createdTo: string | null = null,
  ): Observable<LeadIndicators> {
    let params = new HttpParams();
    if (createdFrom) {
      params = params.set('createdFrom', createdFrom);
    }
    if (createdTo) {
      params = params.set('createdTo', createdTo);
    }
    return this.http.get<LeadIndicators>('/api/leads/indicators', { params });
  }

  detail(id: string): Observable<LeadDetail> {
    return this.http.get<LeadDetail>(`/api/leads/${id}`);
  }

  qualify(id: string, mainInterest: string, note: string | null): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`/api/leads/${id}/qualify`, { mainInterest, note });
  }

  lose(id: string, lossReasonId: string, note: string | null): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`/api/leads/${id}/lose`, { lossReasonId, note });
  }

  reassign(id: string, responsiblePersonId: string | null): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`/api/leads/${id}/reassign`, { responsiblePersonId });
  }

  recordInteraction(id: string, interaction: RegisterInteraction): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`/api/leads/${id}/interactions`, interaction);
  }
}
