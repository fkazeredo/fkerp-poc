import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { PageResponse, Responsible } from './lead.service';

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
  approvedByName: string | null;
  approvalNotes: string | null;
  paidAt: string | null;
  /** The payment evidence (only when PAID): amount, date, method label, note, who. */
  paidAmount: number | null;
  paymentDate: string | null;
  paymentMethod: string | null;
  paymentNote: string | null;
  paidByName: string | null;
  /** The reject/cancel evidence (only when REJECTED/CANCELLED): reason label, note, who, when. */
  resolutionReason: string | null;
  resolutionNote: string | null;
  resolvedByName: string | null;
  resolvedAt: string | null;
  createdByName: string | null;
  createdAt: string;
}

/** Body to register a manual commission payment (Approved → Paid). */
export interface RegisterCommissionPayment {
  paymentMethodId: string;
  amount: number;
  paymentDate: string;
  note: string | null;
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

/** Per-status total of the operational summary (count + amount). */
export interface CommissionStatusTotal {
  status: CommissionStatus;
  count: number;
  totalAmount: number;
}

/** Per-beneficiary total of the operational summary (count + amount). */
export interface CommissionBeneficiaryTotal {
  beneficiaryUserId: string;
  beneficiaryName: string | null;
  count: number;
  totalAmount: number;
}

/**
 * Operational grouping of the visible (and filtered) commissions: the count + total amount by status and by
 * beneficiary, plus the overall total. An operational view (not Executive Reporting) — commission figures only,
 * never payroll, tax, accounting or accounts-payable data.
 */
export interface CommissionOperationalSummary {
  totalCount: number;
  totalAmount: number;
  byStatus: CommissionStatusTotal[];
  byBeneficiary: CommissionBeneficiaryTotal[];
}

/**
 * Minimum functional Commission Management indicators: a current snapshot (count + amount by status and by
 * beneficiary; the amount pending approval and pending payment) plus the volume in the selected period (paid in
 * the period) and the snapshot health averages (eligibility→approval and approval→payment, in seconds; null when
 * none). Commission figures only — never payroll, tax, accounting or bank data.
 */
export interface CommissionIndicators {
  byStatus: CommissionStatusTotal[];
  byBeneficiary: CommissionBeneficiaryTotal[];
  pendingApprovalCount: number;
  pendingApprovalAmount: number;
  pendingPaymentCount: number;
  pendingPaymentAmount: number;
  paidInPeriodCount: number;
  paidInPeriodAmount: number;
  avgEligibilityToApprovalSeconds: number | null;
  avgApprovalToPaymentSeconds: number | null;
}

/** Per-status totals of a commission statement (amount sums + counts for the non-voided lifecycle). */
export interface CommissionStatementTotals {
  totalExpected: number;
  totalEligible: number;
  totalApproved: number;
  totalPaid: number;
  countExpected: number;
  countEligible: number;
  countApproved: number;
  countPaid: number;
}

/** A simple commission statement for one beneficiary over an optional period: entries + per-status totals. */
export interface CommissionStatement {
  beneficiaryId: string;
  beneficiaryName: string | null;
  periodFrom: string | null;
  periodTo: string | null;
  entries: CommissionListItem[];
  totals: CommissionStatementTotals;
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
   * Approves an Eligible Commission (it becomes ready for payment); returns the refreshed detail. The optional notes
   * are recorded with the approval. The backend rejects approving a non-eligible commission (422) and a beneficiary
   * approving their own (403).
   */
  approve(id: string, notes: string | null): Observable<CommissionDetail> {
    return this.http.post<CommissionDetail>(`/api/commissions/${id}/approve`, { notes });
  }

  /**
   * Rejects an Eligible commission (terminal), with a required resolution-reason cadastro id + optional note; returns
   * the refreshed detail. The backend rejects a non-eligible commission (422) and an unknown/inactive reason (422).
   */
  reject(id: string, reasonId: string, note: string | null): Observable<CommissionDetail> {
    return this.http.post<CommissionDetail>(`/api/commissions/${id}/reject`, { reasonId, note });
  }

  /**
   * Cancels an unpaid Expected/Approved commission (terminal), with a required resolution-reason cadastro id +
   * optional note; returns the refreshed detail. The backend rejects a non-cancellable commission (422).
   */
  cancel(id: string, reasonId: string, note: string | null): Observable<CommissionDetail> {
    return this.http.post<CommissionDetail>(`/api/commissions/${id}/cancel`, { reasonId, note });
  }

  /**
   * Registers the manual payment of an Approved commission (Approved → Paid); returns the refreshed detail. The amount
   * must equal the commission amount (full payment only); the backend rejects a non-approved commission (422), a
   * mismatched amount (422) and an inactive payment method (422).
   */
  pay(id: string, body: RegisterCommissionPayment): Observable<CommissionDetail> {
    return this.http.post<CommissionDetail>(`/api/commissions/${id}/pay`, body);
  }

  /**
   * The simple commission statement for a beneficiary over an optional period. An own-tier caller may only request
   * their own beneficiary (the backend enforces it); voided (Rejected/Cancelled) commissions are excluded unless
   * {@code includeVoided} is set.
   */
  statement(
    beneficiaryId: string,
    from: string | null,
    to: string | null,
    includeVoided = false,
  ): Observable<CommissionStatement> {
    let params = new HttpParams().set('beneficiary', beneficiaryId);
    params = setIf(params, 'from', from);
    params = setIf(params, 'to', to);
    if (includeVoided) {
      params = params.set('includeVoided', 'true');
    }
    return this.http.get<CommissionStatement>('/api/commissions/statement', { params });
  }

  /** The responsible-people lookup (to pick a beneficiary for the statement). */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  /** Operational, paginated Commission list filtered by the given criteria and the caller's visibility. */
  list(filters: CommissionFilters, page = 0, size = 20): Observable<PageResponse<CommissionListItem>> {
    const params = filterParams(filters).set('page', page).set('size', size);
    return this.http.get<PageResponse<CommissionListItem>>('/api/commissions', { params });
  }

  /**
   * Operational grouping (count + total amount by status and by beneficiary) of the commissions visible to the
   * caller and matching the same filters as the list — the aggregate companion of the operational list.
   */
  summary(filters: CommissionFilters): Observable<CommissionOperationalSummary> {
    return this.http.get<CommissionOperationalSummary>('/api/commissions/summary', { params: filterParams(filters) });
  }

  /**
   * The minimum Commission Management indicators over the commissions visible to the caller, optionally scoped to a
   * period (ISO dates) for the "paid in the period" figures; absent dates = all-time.
   */
  indicators(from: string | null = null, to: string | null = null): Observable<CommissionIndicators> {
    let params = new HttpParams();
    params = setIf(params, 'from', from);
    params = setIf(params, 'to', to);
    return this.http.get<CommissionIndicators>('/api/commissions/indicators', { params });
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

/** Builds the shared filter query params (status set + the optional filters), without pagination. */
function filterParams(filters: CommissionFilters): HttpParams {
  let params = new HttpParams();
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
  return params;
}
