import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

/**
 * Exposes the running application version (SemVer), fetched once from the public `/api/version`
 * endpoint, for display in the UI (login screen, shell footer).
 */
@Injectable({ providedIn: 'root' })
export class VersionService {
  private readonly http = inject(HttpClient);
  readonly version = signal<string>('');

  constructor() {
    this.http.get<{ version: string }>('/api/version').subscribe({
      next: (res) => this.version.set(res.version),
      error: () => this.version.set(''),
    });
  }
}
