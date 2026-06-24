import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';

/** The Receivable lifecycle status (Financial Operations, Sprint 5). */
export type ReceivableStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

/** The lifecycle status of a single Receivable installment (mirrors the Receivable status). */
export type InstallmentStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

/** One installment of a Receivable's schedule, with its payment progress and overdue flag. Installment data only. */
export interface ReceivableInstallment {
  id: string;
  number: number;
  amount: number;
  amountPaid: number;
  outstanding: number;
  dueDate: string;
  status: InstallmentStatus;
  /** Past due with a balance (OPEN/PARTIALLY_PAID and past its due date) — computed server-side. */
  overdue: boolean;
  paymentNotes: string | null;
}

/** A payment registered against a Receivable installment. Payment data only — never Commission/Invoice data. */
export interface Payment {
  id: string;
  installmentId: string;
  installmentNumber: number;
  amount: number;
  paymentDate: string;
  paymentMethodId: string;
  paymentMethodCode: string;
  paymentMethodLabel: string;
  note: string | null;
  registeredById: string | null;
  registeredByName: string | null;
  registeredAt: string;
  /** Reversal state: when true the payment was reversed (kept in history, no longer counted as paid). */
  reversed: boolean;
  reversalReason: string | null;
  reversedById: string | null;
  reversedByName: string | null;
  reversedAt: string | null;
}

/** A selectable payment method (the active values of the cadastro). */
export interface PaymentMethodOption {
  id: string;
  code: string;
  label: string;
  active: boolean;
  sortOrder: number;
}

/** Payload to register a full payment for a Receivable installment (the amount must equal the installment). */
export interface RegisterPayment {
  paymentMethodId: string;
  amount: number;
  paymentDate: string;
  note?: string | null;
}

/** Payload to reverse a registered payment (a payment-entry correction). The reason is required. */
export interface ReversePayment {
  reason: string;
}

/**
 * Receivable list item (the operational Financial worklist). Carries receivable + commercial-origin data only
 * — never Payment, Commission or Invoice data. The human identifier is the source Order number
 * ({@code orderNumber}, rendered PC-000n).
 */
export interface ReceivableListItem {
  id: string;
  commercialOrderId: string;
  orderNumber: number;
  customerName: string | null;
  totalAmount: number;
  amountPaid: number;
  outstandingAmount: number;
  status: ReceivableStatus;
  dueDate: string;
  overdue: boolean;
  commercialResponsibleId: string | null;
  commercialResponsibleName: string | null;
  financialResponsibleId: string | null;
  financialResponsibleName: string | null;
  createdAt: string;
  lastPaymentDate: string | null;
}

/**
 * Full Receivable detail — keeps the commercial origin (Order / Proposal / Opportunity / Lead / Customer)
 * traceable and exposes the payment standing (paid / outstanding / overdue) and the installment schedule.
 * Carries receivable data only — never Commission, bank-reconciliation or tax-invoice data. {@code amountPaid}
 * is the sum of the registered payments; {@code payments} is the payment history.
 */
export interface ReceivableDetail {
  id: string;
  commercialOrderId: string;
  orderNumber: number;
  proposalId: string;
  proposalReference: string | null;
  opportunityId: string;
  opportunityReference: string | null;
  leadId: string;
  customerId: string;
  customerName: string | null;
  commercialResponsibleId: string | null;
  commercialResponsibleName: string | null;
  financialResponsibleId: string | null;
  financialResponsibleName: string | null;
  totalAmount: number;
  amountPaid: number;
  outstandingAmount: number;
  dueDate: string;
  overdue: boolean;
  paymentNotes: string | null;
  status: ReceivableStatus;
  installments: ReceivableInstallment[];
  payments: Payment[];
  createdAt: string;
  createdByName: string | null;
}

/** A Commercial Order eligible to originate a Receivable (booking CONFIRMED, without an active Receivable). */
export interface EligibleOrder {
  orderId: string;
  number: number;
  customerName: string | null;
  total: number;
}

/**
 * Operational Receivable-list filters. Empty {@code status} shows the operational receivables (excludes the
 * settled PAID and CANCELLED); include them explicitly to see them. Dates are ISO {@code yyyy-MM-dd}.
 */
export interface ReceivableFilters {
  status?: ReceivableStatus[];
  order?: string | null;
  orderNumber?: number | null;
  payer?: string | null;
  dueFrom?: string | null;
  dueTo?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  commercialResponsible?: string | null;
  financialResponsible?: string | null;
  amountMin?: number | null;
  amountMax?: number | null;
  overdueOnly?: boolean | null;
}

/** One installment supplied when creating a Receivable (amount + due date + optional notes). */
export interface CreateInstallment {
  amount: number;
  dueDate: string;
  paymentNotes?: string | null;
}

/**
 * Payload to create a Receivable from a Commercial Order with a confirmed booking. {@code installments} is
 * optional: absent/empty ⇒ one full-amount installment; when present, the installments must sum to the order
 * total.
 */
export interface CreateReceivable {
  commercialOrderId: string;
  dueDate: string;
  paymentNotes?: string | null;
  financialResponsiblePersonId?: string | null;
  installments?: CreateInstallment[];
}

export interface ReceivableCreated {
  id: string;
  status: ReceivableStatus;
}

/** Received total + count for one payment method over the period (non-reversed payments). */
export interface MethodTotal {
  method: string;
  methodLabel: string;
  count: number;
  amount: number;
}

/** Current receivable count for a lifecycle status code. */
export interface StatusCount {
  status: ReceivableStatus;
  count: number;
}

/**
 * Minimum functional Financial Operations indicators — a manager's minimal view of receivables and received
 * payments. Volume in the period (receivables created, amount to receive, amount received, payments by method,
 * receivables settled, average days to payment) plus a current snapshot (receivables by status, outstanding and
 * overdue amounts, receivables ready for Commission Management). Operational, not executive — never Commission
 * calculation, Accounts Payable or bank-reconciliation data.
 */
export interface ReceivableIndicators {
  // Volume in the selected period.
  totalReceivablesInPeriod: number;
  totalToReceive: number;
  receivedAmount: number;
  paymentsRegistered: number;
  paymentsByMethod: MethodTotal[];
  paidReceivablesInPeriod: number;
  /** Average days from creation to settlement over receivables settled in the period; null when none. */
  avgDaysToPayment: number | null;
  // Current snapshot.
  byStatus: StatusCount[];
  outstandingAmount: number;
  overdueAmount: number;
  /** Current count of PAID receivables — identifiable as ready for Commission Management (Sprint 6). */
  readyForCommission: number;
}

/** API client for the Receivable endpoints (Financial Operations). */
@Injectable({ providedIn: 'root' })
export class ReceivableService {
  private readonly http = inject(HttpClient);

  /** The selectable responsible people (shared with the CRM module). */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  /** Creates a Receivable from a Commercial Order with a confirmed booking; returns the new id and status. */
  create(payload: CreateReceivable): Observable<ReceivableCreated> {
    return this.http.post<ReceivableCreated>('/api/receivables', payload);
  }

  detail(id: string): Observable<ReceivableDetail> {
    return this.http.get<ReceivableDetail>(`/api/receivables/${id}`);
  }

  /** The active payment methods (the cadastro values) for the payment dialog's method selector. */
  paymentMethods(): Observable<PaymentMethodOption[]> {
    return this.http.get<PaymentMethodOption[]>('/api/financial/payment-methods');
  }

  /**
   * Registers a full payment for one installment of a Receivable; returns the refreshed detail (installment +
   * receivable status consolidated, the new payment in the history).
   */
  registerPayment(
    receivableId: string,
    installmentId: string,
    payload: RegisterPayment,
  ): Observable<ReceivableDetail> {
    return this.http.post<ReceivableDetail>(
      `/api/receivables/${receivableId}/installments/${installmentId}/payments`,
      payload,
    );
  }

  /**
   * Reverses a registered payment (a payment-entry correction); returns the refreshed detail (the payment stays
   * in history marked reversed, the installment + receivable status and paid amount re-consolidated).
   */
  reversePayment(
    receivableId: string,
    paymentId: string,
    payload: ReversePayment,
  ): Observable<ReceivableDetail> {
    return this.http.post<ReceivableDetail>(
      `/api/receivables/${receivableId}/payments/${paymentId}/reversals`,
      payload,
    );
  }

  /** The Commercial Orders eligible to originate a Receivable, visible to the caller (for the create selector). */
  eligibleOrders(): Observable<EligibleOrder[]> {
    return this.http.get<EligibleOrder[]>('/api/receivables/eligible-orders');
  }

  /**
   * The minimum Financial Operations indicators visible to the caller: the period volume plus a current snapshot.
   * Empty period bounds widen to all-time. Operational, never Commission/Payables data.
   */
  indicators(from?: string | null, to?: string | null): Observable<ReceivableIndicators> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<ReceivableIndicators>('/api/receivables/indicators', { params });
  }

  /** Operational, paginated Receivable list filtered by the given criteria and the caller's visibility. */
  list(filters: ReceivableFilters, page = 0, size = 20): Observable<PageResponse<ReceivableListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    if (filters.order) {
      params = params.set('order', filters.order);
    }
    if (filters.orderNumber != null) {
      params = params.set('orderNumber', filters.orderNumber);
    }
    if (filters.payer && filters.payer.trim().length > 0) {
      params = params.set('payer', filters.payer.trim());
    }
    if (filters.dueFrom) {
      params = params.set('dueFrom', filters.dueFrom);
    }
    if (filters.dueTo) {
      params = params.set('dueTo', filters.dueTo);
    }
    if (filters.createdFrom) {
      params = params.set('createdFrom', filters.createdFrom);
    }
    if (filters.createdTo) {
      params = params.set('createdTo', filters.createdTo);
    }
    if (filters.commercialResponsible) {
      params = params.set('commercialResponsible', filters.commercialResponsible);
    }
    if (filters.financialResponsible) {
      params = params.set('financialResponsible', filters.financialResponsible);
    }
    if (filters.amountMin != null) {
      params = params.set('amountMin', filters.amountMin);
    }
    if (filters.amountMax != null) {
      params = params.set('amountMax', filters.amountMax);
    }
    if (filters.overdueOnly) {
      params = params.set('overdueOnly', true);
    }
    return this.http.get<PageResponse<ReceivableListItem>>('/api/receivables', { params });
  }
}
