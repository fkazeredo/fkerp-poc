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

/** Operational Lead-list filters. {@code responsible} is a user id or the literal `unassigned`. */
export interface LeadFilters {
  status?: LeadStatus[];
  originId?: string | null;
  responsible?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  q?: string | null;
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
}
