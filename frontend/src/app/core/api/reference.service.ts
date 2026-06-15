import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ReferenceItem {
  id: string;
  code: string;
  label: string;
  active: boolean;
  sortOrder: number;
}

/** Generic API client for the CRM reference-data cadastros (origins, loss reasons, …). */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly http = inject(HttpClient);

  list(path: string, includeInactive = false): Observable<ReferenceItem[]> {
    return this.http.get<ReferenceItem[]>(`/api/crm/${path}?includeInactive=${includeInactive}`);
  }

  create(
    path: string,
    body: { code: string; label: string; sortOrder: number },
  ): Observable<ReferenceItem> {
    return this.http.post<ReferenceItem>(`/api/crm/${path}`, body);
  }

  update(
    path: string,
    id: string,
    body: { label: string; sortOrder: number; active: boolean },
  ): Observable<ReferenceItem> {
    return this.http.put<ReferenceItem>(`/api/crm/${path}/${id}`, body);
  }

  deactivate(path: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/crm/${path}/${id}`);
  }
}
