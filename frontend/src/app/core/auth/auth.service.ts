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

  /** Whether the user may create a commercial Opportunity from a qualified lead. */
  canCreateOpportunity(): boolean {
    return this.hasScope('crm:opportunity:create');
  }

  /** Any Opportunity read tier (own / pool / all) grants access to the Opportunity list and detail. */
  canSeeOpportunities(): boolean {
    return (
      this.hasScope('crm:opportunity:read') ||
      this.hasScope('crm:opportunity:read:unassigned') ||
      this.hasScope('crm:opportunity:read:all')
    );
  }

  /** Operate = mark an Opportunity as lost (consultation-only users lack it). */
  canOperateOpportunity(): boolean {
    return this.hasScope('crm:opportunity:update');
  }

  /** Whether the user may create a commercial Proposal from a ready Opportunity (Sales & Proposals). */
  canCreateProposal(): boolean {
    return this.hasScope('sales:proposal:create');
  }

  /** Any Proposal read tier (own / pool / all) grants access to the Proposal list and detail. */
  canSeeProposals(): boolean {
    return (
      this.hasScope('sales:proposal:read') ||
      this.hasScope('sales:proposal:read:unassigned') ||
      this.hasScope('sales:proposal:read:all')
    );
  }

  /** Operate = edit/items/submit a Proposal (Draft management and submit for review). */
  canOperateProposal(): boolean {
    return this.hasScope('sales:proposal:update');
  }

  /** Whether the user may approve or reject a Proposal under internal review (a separate authority). */
  canApproveProposal(): boolean {
    return this.hasScope('sales:proposal:approve');
  }

  /** Whether the user may create a Commercial Order from an Accepted Proposal (Sales & Proposals). */
  canCreateOrder(): boolean {
    return this.hasScope('sales:order:create');
  }

  /** Any Commercial Order read tier (own / pool / all) grants access to the Order detail. */
  canSeeOrders(): boolean {
    return (
      this.hasScope('sales:order:read') ||
      this.hasScope('sales:order:read:unassigned') ||
      this.hasScope('sales:order:read:all')
    );
  }

  /** Any Booking Request read tier (own / pool / all) grants access to the reservation list (Booking Operations). */
  canSeeBookings(): boolean {
    return (
      this.hasScope('booking:request:read') ||
      this.hasScope('booking:request:read:unassigned') ||
      this.hasScope('booking:request:read:all')
    );
  }

  /** Operate = register manual booking attempts (and future operate actions). Consultation users lack it. */
  canOperateBookings(): boolean {
    return this.hasScope('booking:request:update');
  }

  /** Any Receivable read tier (own / all) grants access to the Receivable list and detail (Financial Operations). */
  canSeeReceivables(): boolean {
    return this.hasScope('financial:receivable:read') || this.hasScope('financial:receivable:read:all');
  }

  /** Whether the user may create a Receivable from a Commercial Order with a confirmed booking. */
  canCreateReceivable(): boolean {
    return this.hasScope('financial:receivable:create');
  }

  /** Whether the user may register a payment against a Receivable installment (the financial operator). */
  canRegisterPayment(): boolean {
    return this.hasScope('financial:payment:register');
  }

  /** Whether the user may reverse a registered payment (a payment-entry correction; the financial operator). */
  canReversePayment(): boolean {
    return this.hasScope('financial:payment:reverse');
  }

  /** Whether the user may manage commission rules (a commercial/financial manager — Commission Management). */
  canManageCommissionRules(): boolean {
    return this.hasScope('commission:rule:manage');
  }

  /** Whether the user may generate an Expected Commission from a Commercial Order (Commission Management). */
  canCreateCommission(): boolean {
    return this.hasScope('commission:create');
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
