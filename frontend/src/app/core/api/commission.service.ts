import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { PageResponse } from './lead.service';

/** The Commission lifecycle status (Commission Management, Sprint 6). */
export type CommissionStatus = 'EXPECTED' | 'ELIGIBLE' | 'APPROVED' | 'REJECTED' | 'PAID' | 'CANCELLED';

/** The basis the Expected Commission amount was calculated from. */
export type CommissionBasis = 'COMMERCIAL_AMOUNT' | 'RECEIVED_AMOUNT';

/** The Receivable status reflected onto a commission's source order (when there is a receivable). */
export type ReceivableStatusCode = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

/**
 * Full Commission detail. Carries commission + commercial-origin data only — never Commission Payment, Accounts
 * Payable, payroll, tax or accounting data.
 */
export interface CommissionDetail {
  id: string;
  commercialOrderId: string;
  orderNumber: number;
  proposalId: string;
  proposalReference: string | null;
  opportunityId: string;
  opportunityReference: string | null;
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
  /** The source order's active Receivable (the related-receivable reference), or null when there is none. */
  receivableId: string | null;
  receivableStatus: ReceivableStatusCode | null;
  eligibleAt: string | null;
  approvedAt: string | null;
  paidAt: string | null;
  createdByName: string | null;
  createdAt: string;
}

/**
 * Operational Commission list item — the information a commercial/financial manager needs to track expected /
 * eligible / approved / paid commissions. Carries commission + commercial-origin data only — never payroll, tax,
 * accounting or generic accounts-payable data.
 */
export interface CommissionListItem {
  id: string;
  beneficiaryUserId: string;
  beneficiaryName: string | null;
  commercialOrderId: string;
  orderNumber: number;
  proposalReference: string | null;
  opportunityReference: string | null;
  amount: number;
  baseAmount: number;
  basisType: CommissionBasis;
  rulePercentage: number;
  ruleName: string | null;
  status: CommissionStatus;
  /** The source order's active Receivable status, or null when there is none. */
  receivableStatus: ReceivableStatusCode | null;
  createdAt: string;
  eligibleAt: string | null;
  approvedAt: string | null;
  paidAt: string | null;
}

/** Optional filters for the operational Commission list. Empty status ⇒ operational (Expected/Eligible/Approved). */
export interface CommissionFilters {
  status?: CommissionStatus[];
  beneficiary?: string | null;
  order?: string | null;
  orderNumber?: number | null;
  rule?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  eligibleFrom?: string | null;
  eligibleTo?: string | null;
  paidFrom?: string | null;
  paidTo?: string | null;
  amountMin?: number | null;
  amountMax?: number | null;
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

  /** Operational, paginated Commission list filtered by the given criteria and the caller's visibility. */
  list(filters: CommissionFilters, page = 0, size = 20): Observable<PageResponse<CommissionListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    params = setIf(params, 'beneficiary', filters.beneficiary);
    params = setIf(params, 'order', filters.order);
    params = setIf(params, 'orderNumber', filters.orderNumber);
    params = setIf(params, 'rule', filters.rule);
    params = setIf(params, 'createdFrom', filters.createdFrom);
    params = setIf(params, 'createdTo', filters.createdTo);
    params = setIf(params, 'eligibleFrom', filters.eligibleFrom);
    params = setIf(params, 'eligibleTo', filters.eligibleTo);
    params = setIf(params, 'paidFrom', filters.paidFrom);
    params = setIf(params, 'paidTo', filters.paidTo);
    params = setIf(params, 'amountMin', filters.amountMin);
    params = setIf(params, 'amountMax', filters.amountMax);
    return this.http.get<PageResponse<CommissionListItem>>('/api/commissions', { params });
  }

  /**
   * The active commission of a Commercial Order (or null), for the Order detail's "this order's commission" view —
   * a thin read over the list filtered to the order and the active statuses.
   */
  byOrder(commercialOrderId: string): Observable<CommissionListItem | null> {
    return this.list(
      { order: commercialOrderId, status: ['EXPECTED', 'ELIGIBLE', 'APPROVED', 'PAID'] },
      0,
      1,
    ).pipe(map((page) => page.content[0] ?? null));
  }
}

function setIf(params: HttpParams, key: string, value: string | number | null | undefined): HttpParams {
  if (value === null || value === undefined || value === '') {
    return params;
  }
  return params.set(key, value);
}
