import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

interface JwtClaims {
  sub?: string;
  scope?: string;
}

/**
 * Holds the access token in memory and talks to the auth endpoints. The refresh token lives in an
 * httpOnly cookie (sent automatically same-origin), so it is never handled by JS. The token's scopes
 * and subject are decoded for UX gating only — the backend stays the source of truth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly accessToken = signal<string | null>(null);

  private readonly claims = computed<JwtClaims | null>(() => decodeJwt(this.accessToken()));
  readonly scopes = computed<string[]>(() =>
    (this.claims()?.scope ?? '').split(' ').filter((s) => s.length > 0),
  );

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/auth/login', { username, password }, { withCredentials: true })
      .pipe(tap((res) => this.accessToken.set(res.accessToken)));
  }

  refresh(): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .pipe(tap((res) => this.accessToken.set(res.accessToken)));
  }

  logout(): Observable<void> {
    this.accessToken.set(null);
    return this.http.post<void>('/api/auth/logout', {}, { withCredentials: true });
  }

  isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }

  hasScope(scope: string): boolean {
    return this.scopes().includes(scope);
  }

  userId(): string | null {
    return this.claims()?.sub ?? null;
  }

  /** Any read tier (own / pool / all) grants access to the Lead list and detail. */
  canSeeLeads(): boolean {
    return (
      this.hasScope('crm:lead:read') ||
      this.hasScope('crm:lead:read:unassigned') ||
      this.hasScope('crm:lead:read:all')
    );
  }

  canCreateLead(): boolean {
    return this.hasScope('crm:lead:create');
  }

  /** Operate = qualify / lose / reassign / register interaction (consultation-only users lack it). */
  canOperateLead(): boolean {
    return this.hasScope('crm:lead:update');
  }
}

function decodeJwt(token: string | null): JwtClaims | null {
  if (!token) {
    return null;
  }
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    let base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4 !== 0) {
      base64 += '=';
    }
    return JSON.parse(atob(base64)) as JwtClaims;
  } catch {
    return null;
  }
}
