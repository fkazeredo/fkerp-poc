import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';
import { ProposalItemType } from './proposal.service';

/** The Booking Request lifecycle status (Booking Operations, Sprint 4). */
export type BookingRequestStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'PARTIALLY_CONFIRMED'
  | 'CONFIRMED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * Booking Request list item (the operational reservation worklist). Carries operational reservation data
 * only — never Financial, Payment or Commission data. The human identifier is the source Order number
 * ({@code commercialOrderNumber}, rendered PC-000n); {@code lastBookingAttemptAt} stays null until the
 * attempt slice ships.
 */
export interface BookingRequestListItem {
  id: string;
  commercialOrderId: string;
  commercialOrderNumber: number;
  proposalId: string;
  proposalTitle: string | null;
  status: BookingRequestStatus;
  bookingOperatorId: string | null;
  bookingOperatorName: string | null;
  operatorUnassigned: boolean;
  responsiblePersonId: string | null;
  responsibleName: string | null;
  itemsRequiringBooking: number;
  confirmedItems: number;
  createdAt: string;
  updatedAt: string;
  lastBookingAttemptAt: string | null;
}

/**
 * Operational Booking Request-list filters. Empty {@code status} excludes the terminal CONFIRMED and
 * CANCELLED requests and keeps FAILED; include them explicitly to see them. {@code operator} is a user id or
 * the literal `unassigned`; {@code responsible} is a commercial-responsible user id; the date bounds are ISO
 * `yyyy-MM-dd`; {@code order} restricts to a single source Commercial Order; {@code itemType} narrows by item
 * type; {@code hasFailedItems} keeps only requests with a failed item.
 */
export interface BookingFilters {
  status?: BookingRequestStatus[];
  operator?: string | null;
  responsible?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  order?: string | null;
  itemType?: ProposalItemType | null;
  hasFailedItems?: boolean | null;
}

/** API client for the Booking Request endpoints (Booking Operations). */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  /** The selectable responsible people (shared with the CRM module). */
  responsibles(): Observable<Responsible[]> {
    return this.http.get<Responsible[]>('/api/crm/responsibles');
  }

  /** Operational, paginated Booking Request list filtered by the given criteria and the caller's visibility. */
  list(filters: BookingFilters, page = 0, size = 20): Observable<PageResponse<BookingRequestListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const status of filters.status ?? []) {
      params = params.append('status', status);
    }
    if (filters.operator) {
      params = params.set('operator', filters.operator);
    }
    if (filters.responsible) {
      params = params.set('responsible', filters.responsible);
    }
    if (filters.createdFrom) {
      params = params.set('createdFrom', filters.createdFrom);
    }
    if (filters.createdTo) {
      params = params.set('createdTo', filters.createdTo);
    }
    if (filters.order) {
      params = params.set('order', filters.order);
    }
    if (filters.itemType) {
      params = params.set('itemType', filters.itemType);
    }
    if (filters.hasFailedItems) {
      params = params.set('hasFailedItems', true);
    }
    return this.http.get<PageResponse<BookingRequestListItem>>('/api/bookings', { params });
  }
}
