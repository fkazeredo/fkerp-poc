import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, Responsible } from './lead.service';
import { OpportunityStage } from './opportunity.service';
import { CommercialOrderStatus } from './order.service';
import { ProposalItemType, ProposalStatus } from './proposal.service';

/** The Booking Request lifecycle status (Booking Operations, Sprint 4). */
export type BookingRequestStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'PARTIALLY_CONFIRMED'
  | 'CONFIRMED'
  | 'FAILED'
  | 'CANCELLED';

/** The status of a single booking item (the per-item confirmation/failure signal). */
export type BookingItemStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'CONFIRMED'
  | 'FAILED'
  | 'NOT_REQUIRED'
  | 'CANCELLED';

/** The kind of manual booking attempt (Booking Operations, Sprint 4). */
export type BookingAttemptType =
  | 'EXTERNAL_SYSTEM_ACCESS'
  | 'SUPPLIER_PHONE_CONTACT'
  | 'SUPPLIER_EMAIL_CONTACT'
  | 'INTERNAL_VERIFICATION'
  | 'MANUAL_AVAILABILITY_CHECK'
  | 'OTHER';

/** The outcome of a manual booking attempt (history only — never confirms/fails the reservation). */
export type BookingAttemptResult =
  | 'STARTED'
  | 'WAITING_FOR_SUPPLIER'
  | 'WAITING_FOR_INTERNAL_INFO'
  | 'AVAILABILITY_FOUND'
  | 'AVAILABILITY_NOT_FOUND'
  | 'NEEDS_RETRY'
  | 'FAILED'
  | 'OTHER';

/** A single manual booking attempt in the operational history. */
export interface BookingAttempt {
  id: string;
  bookingItemId: string | null;
  type: BookingAttemptType;
  result: BookingAttemptResult;
  description: string;
  occurredAt: string;
  nextActionDate: string | null;
  registeredByName: string | null;
}

/** Payload to register a manual booking attempt. */
export interface RegisterBookingAttempt {
  bookingItemId?: string | null;
  type: BookingAttemptType;
  result: BookingAttemptResult;
  description: string;
  occurredAt: string;
  nextActionDate?: string | null;
}

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

/** The external reservation result recorded when a Travel Package item is manually confirmed. No monetary data. */
export interface BookingItemConfirmation {
  externalSystem: string;
  externalLocator: string;
  confirmedAt: string;
  confirmedByName: string | null;
  packageDescription: string | null;
  travelStartDate: string | null;
  travelEndDate: string | null;
  travelerNotes: string | null;
  operationalNotes: string | null;
}

/** A single booking line, traceable to its source Commercial Order item, carrying its booking status. */
export interface BookingRequestItem {
  id: string;
  orderItemId: string;
  type: ProposalItemType;
  description: string;
  quantity: number;
  requiresBooking: boolean;
  status: BookingItemStatus;
  confirmation: BookingItemConfirmation | null;
}

/** Payload to manually confirm a Travel Package booking item. */
export interface ConfirmTravelPackage {
  externalSystem: string;
  externalLocator: string;
  confirmedAt: string;
  packageDescription?: string | null;
  travelStartDate?: string | null;
  travelEndDate?: string | null;
  travelerNotes?: string | null;
  operationalNotes?: string | null;
}

/** The source Commercial Order, kept traceable from the reservation (its number is the human identifier). */
export interface BookingSourceOrder {
  id: string;
  number: number;
  status: CommercialOrderStatus;
}

/** The source Proposal (commercial reference), kept traceable from the reservation. */
export interface BookingSourceProposal {
  id: string;
  title: string;
  status: ProposalStatus;
}

/** The source Opportunity (commercial reference), kept traceable from the reservation. */
export interface BookingSourceOpportunity {
  id: string;
  name: string;
  stage: OpportunityStage;
}

/** The source Lead, kept traceable from the reservation. */
export interface BookingSourceLead {
  id: string;
  name: string;
}

/**
 * Full Booking Request detail — the operational reservation record: its summary, the source Commercial Order /
 * Proposal / Opportunity / Lead (kept traceable) and the booking items with their statuses (the per-item
 * confirmation/failure signal). Carries operational reservation data only — never Financial, Payment or
 * Commission data.
 */
export interface BookingRequestDetail {
  id: string;
  commercialOrderId: string;
  commercialOrderNumber: number;
  status: BookingRequestStatus;
  bookingOperatorId: string | null;
  bookingOperatorName: string | null;
  operatorUnassigned: boolean;
  responsiblePersonId: string | null;
  responsibleName: string | null;
  notes: string | null;
  itemsRequiringBooking: number;
  itemsConfirmed: number;
  itemsFailed: number;
  createdAt: string;
  updatedAt: string;
  createdByName: string | null;
  items: BookingRequestItem[];
  attempts: BookingAttempt[];
  sourceOrder: BookingSourceOrder;
  sourceProposal: BookingSourceProposal;
  sourceOpportunity: BookingSourceOpportunity;
  sourceLead: BookingSourceLead;
}

/** API client for the Booking Request endpoints (Booking Operations). */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  /** Full detail of a Booking Request the caller may see. */
  detail(id: string): Observable<BookingRequestDetail> {
    return this.http.get<BookingRequestDetail>(`/api/bookings/${id}`);
  }

  /** Registers a manual booking attempt; returns the refreshed detail. */
  registerAttempt(id: string, payload: RegisterBookingAttempt): Observable<BookingRequestDetail> {
    return this.http.post<BookingRequestDetail>(`/api/bookings/${id}/attempts`, payload);
  }

  /** Manually confirms a Travel Package booking item; returns the refreshed detail. */
  confirmTravelPackage(
    id: string,
    itemId: string,
    payload: ConfirmTravelPackage,
  ): Observable<BookingRequestDetail> {
    return this.http.post<BookingRequestDetail>(`/api/bookings/${id}/items/${itemId}/confirm`, payload);
  }

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
