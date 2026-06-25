import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

/** The Commission lifecycle status (Commission Management, Sprint 6). Slice 2 generates only EXPECTED. */
export type CommissionStatus = 'EXPECTED' | 'ELIGIBLE' | 'APPROVED' | 'REJECTED' | 'PAID' | 'CANCELLED';

/** The basis the Expected Commission amount was calculated from. */
export type CommissionBasis = 'COMMERCIAL_AMOUNT' | 'RECEIVED_AMOUNT';

/**
 * Full Expected Commission detail. Carries commission + commercial-origin data only — never Commission Payment,
 * Accounts Payable, payroll, tax or accounting data. The human identifier of the source deal is the order number
 * ({@code orderNumber}, rendered PC-000n).
 */
export interface CommissionDetail {
  id: string;
  commercialOrderId: string;
  orderNumber: number;
  proposalId: string;
  opportunityId: string;
  leadId: string;
  beneficiaryUserId: string;
  beneficiaryName: string | null;
  ruleId: string;
  ruleName: string | null;
  rulePercentage: number;
  basisType: CommissionBasis;
  baseAmount: number;
  amount: number;
  status: CommissionStatus;
  /** When the commission became eligible (its receivable was paid), or null while still a forecast. */
  eligibleAt: string | null;
  createdAt: string;
}

/** Trivial create response for a generated Commission. */
export interface CommissionCreated {
  id: string;
}

/** API client for the Commission endpoints (Commission Management). */
@Injectable({ providedIn: 'root' })
export class CommissionService {
  private readonly http = inject(HttpClient);

  /** Generates an Expected Commission from a commercially-closed Commercial Order; returns the new id. */
  generate(commercialOrderId: string): Observable<CommissionCreated> {
    return this.http.post<CommissionCreated>('/api/commissions', { commercialOrderId });
  }

  detail(id: string): Observable<CommissionDetail> {
    return this.http.get<CommissionDetail>(`/api/commissions/${id}`);
  }

  /**
   * The active commission of a Commercial Order (or null when there is none). Feeds the Order detail's "this order's
   * commission" view, showing whether it is still a forecast (Expected) or eligible for approval (Eligible).
   */
  byOrder(commercialOrderId: string): Observable<CommissionDetail | null> {
    const params = new HttpParams().set('commercialOrderId', commercialOrderId);
    return this.http
      .get<CommissionDetail[]>('/api/commissions', { params })
      .pipe(map((list) => list[0] ?? null));
  }
}
