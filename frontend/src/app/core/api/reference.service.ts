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

/**
 * Generic API client for the reference-data cadastros across the bounded contexts (CRM origins / loss
 * reasons / activity types, Sales rejection reasons / sending channels / item types, Booking attempt types /
 * failure reasons, …). The {@link base} selects the context segment ({@code crm} by default, or
 * {@code sales} / {@code booking}); the {@link path} is the resource under it.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly http = inject(HttpClient);

  list(path: string, includeInactive = false, base = 'crm'): Observable<ReferenceItem[]> {
    return this.http.get<ReferenceItem[]>(
      `/api/${base}/${path}?includeInactive=${includeInactive}`,
    );
  }

  create(
    path: string,
    body: { code: string; label: string; sortOrder: number },
    base = 'crm',
  ): Observable<ReferenceItem> {
    return this.http.post<ReferenceItem>(`/api/${base}/${path}`, body);
  }

  update(
    path: string,
    id: string,
    body: { label: string; sortOrder: number; active: boolean },
    base = 'crm',
  ): Observable<ReferenceItem> {
    return this.http.put<ReferenceItem>(`/api/${base}/${path}/${id}`, body);
  }

  deactivate(path: string, id: string, base = 'crm'): Observable<void> {
    return this.http.delete<void>(`/api/${base}/${path}/${id}`);
  }
}
