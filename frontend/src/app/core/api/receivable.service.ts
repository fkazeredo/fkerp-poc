import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';

/** The Receivable lifecycle status (Financial Operations, Sprint 5). */
export type ReceivableStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

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
  dueDate: string;
  status: ReceivableStatus;
  financialResponsibleId: string | null;
  financialResponsibleName: string | null;
  createdAt: string;
}

/**
 * Full Receivable detail — keeps the commercial origin (Order / Proposal / Opportunity / Lead / Customer)
 * traceable. Carries receivable data only — never Payment, Commission or Invoice data.
 */
export interface ReceivableDetail {
  id: string;
  commercialOrderId: string;
  orderNumber: number;
  proposalId: string;
  opportunityId: string;
  leadId: string;
  customerId: string;
  customerName: string | null;
  commercialResponsibleId: string | null;
  commercialResponsibleName: string | null;
  financialResponsibleId: string | null;
  financialResponsibleName: string | null;
  totalAmount: number;
  dueDate: string;
  paymentNotes: string | null;
  status: ReceivableStatus;
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
 * Operational Receivable-list filters. Empty {@code status} excludes the CANCELLED Receivables; include it to
 * see cancelled ones. {@code order} restricts to a single source Commercial Order.
 */
export interface ReceivableFilters {
  status?: ReceivableStatus[];
  order?: string | null;
}

/** Payload to create a Receivable from a Commercial Order with a confirmed booking. */
export interface CreateReceivable {
  commercialOrderId: string;
  dueDate: string;
  paymentNotes?: string | null;
  financialResponsiblePersonId?: string | null;
}

export interface ReceivableCreated {
  id: string;
  status: ReceivableStatus;
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

  /** The Commercial Orders eligible to originate a Receivable, visible to the caller (for the create selector). */
  eligibleOrders(): Observable<EligibleOrder[]> {
    return this.http.get<EligibleOrder[]>('/api/receivables/eligible-orders');
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
    return this.http.get<PageResponse<ReceivableListItem>>('/api/receivables', { params });
  }
}
