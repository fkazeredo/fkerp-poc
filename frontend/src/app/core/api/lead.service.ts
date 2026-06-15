import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Origin {
  id: string;
  code: string;
  label: string;
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

/** API client for the Commercial / CRM lead-intake endpoints. */
@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);

  origins(): Observable<Origin[]> {
    return this.http.get<Origin[]>('/api/crm/origins');
  }

  create(lead: CreateLead): Observable<LeadCreated> {
    return this.http.post<LeadCreated>('/api/leads', lead);
  }
}
