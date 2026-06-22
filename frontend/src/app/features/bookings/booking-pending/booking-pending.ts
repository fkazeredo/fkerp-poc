import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import {
  BookingPendingReason,
  BookingRequestStatus,
  BookingService,
  PendingBookingRequest,
} from '../../../core/api/booking.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_LABELS: Record<BookingRequestStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  CONFIRMED: 'Confirmada',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelada',
};

const STATUS_SEVERITY: Record<BookingRequestStatus, TagSeverity> = {
  PENDING: 'warn',
  IN_PROGRESS: 'info',
  PARTIALLY_CONFIRMED: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELLED: 'secondary',
};

const REASON_LABELS: Record<BookingPendingReason, string> = {
  UNASSIGNED_OPERATOR: 'Sem operador',
  PENDING_WITHOUT_ATTEMPT: 'Pendente sem tentativa',
  IN_PROGRESS_WITHOUT_RECENT_ATTEMPT: 'Sem tentativa recente',
  HAS_FAILED_ITEM: 'Item com falha',
  HAS_PENDING_REQUIRED_ITEM: 'Item a reservar pendente',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  OVERDUE_NEXT_ACTION: 'Próxima ação atrasada',
};

const REASON_SEVERITY: Record<BookingPendingReason, TagSeverity> = {
  UNASSIGNED_OPERATOR: 'warn',
  PENDING_WITHOUT_ATTEMPT: 'warn',
  IN_PROGRESS_WITHOUT_RECENT_ATTEMPT: 'warn',
  HAS_FAILED_ITEM: 'danger',
  HAS_PENDING_REQUIRED_ITEM: 'info',
  PARTIALLY_CONFIRMED: 'info',
  OVERDUE_NEXT_ACTION: 'danger',
};

/**
 * Operational pending-items worklist for Booking Operations: the Booking Requests that need action, tagged with
 * the reasons why, so reservations do not stall silently. Read-only; visibility is enforced by the backend. It is
 * operational, not an executive dashboard — no notification/SLA engine, no external retry.
 */
@Component({
  selector: 'app-booking-pending',
  imports: [DatePipe, RouterLink, TableModule, TagModule, MessageModule],
  templateUrl: './booking-pending.html',
  styleUrl: './booking-pending.css',
})
export class BookingPending implements OnInit {
  private readonly bookings = inject(BookingService);

  protected readonly items = signal<PendingBookingRequest[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  ngOnInit(): void {
    // The lazy table fires the first load.
  }

  /** The human-friendly reservation code — the source Order number rendered PC-000n. */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  protected statusLabel(status: BookingRequestStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: BookingRequestStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected reasonLabel(reason: BookingPendingReason): string {
    return REASON_LABELS[reason];
  }

  protected reasonSeverity(reason: BookingPendingReason): TagSeverity {
    return REASON_SEVERITY[reason];
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.firstRow = event.first ?? 0;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.bookings.pending(this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as pendências de reserva.');
      },
    });
  }
}
