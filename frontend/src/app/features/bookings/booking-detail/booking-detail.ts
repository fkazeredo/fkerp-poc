import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import {
  BookingItemStatus,
  BookingRequestDetail,
  BookingRequestStatus,
  BookingService,
} from '../../../core/api/booking.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { CommercialOrderStatus } from '../../../core/api/order.service';
import { ProposalItemType, ProposalStatus } from '../../../core/api/proposal.service';

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

const ITEM_STATUS_LABELS: Record<BookingItemStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  CONFIRMED: 'Confirmado',
  FAILED: 'Falhou',
  NOT_REQUIRED: 'Não requer reserva',
  CANCELLED: 'Cancelado',
};

const ITEM_STATUS_SEVERITY: Record<BookingItemStatus, TagSeverity> = {
  PENDING: 'warn',
  IN_PROGRESS: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  NOT_REQUIRED: 'secondary',
  CANCELLED: 'secondary',
};

const ITEM_TYPE_LABELS: Record<ProposalItemType, string> = {
  TRAVEL_PACKAGE: 'Pacote de viagem',
  CAR_RENTAL: 'Locação de veículo',
  SERVICE_FEE: 'Taxa de serviço',
  OTHER: 'Outro',
};

const ORDER_STATUS_LABELS: Record<CommercialOrderStatus, string> = {
  PENDING_BOOKING: 'Pendente de reserva',
  BOOKING_NOT_REQUIRED: 'Reserva não necessária',
  CANCELLED: 'Cancelado',
};

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  WON: 'Ganha',
  LOST: 'Perdida',
};

const PROPOSAL_STATUS_LABELS: Record<ProposalStatus, string> = {
  DRAFT: 'Rascunho',
  READY_FOR_REVIEW: 'Pronta para revisão',
  APPROVED: 'Aprovada',
  SENT: 'Enviada',
  ACCEPTED: 'Aceita',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

/**
 * Booking Request detail page (Booking Operations): the read-only operational record of a reservation — its
 * status, the booking items with their statuses (what must be reserved, what is confirmed, what failed), and
 * the source Commercial Order / Proposal / Opportunity / Lead kept traceable. Shows operational reservation
 * data only — never financial, payment or commission data.
 */
@Component({
  selector: 'app-booking-detail',
  imports: [DatePipe, RouterLink, ButtonModule, CardModule, TableModule, TagModule, MessageModule],
  templateUrl: './booking-detail.html',
  styleUrl: './booking-detail.css',
})
export class BookingDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookings = inject(BookingService);

  protected readonly booking = signal<BookingRequestDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private bookingId = '';

  ngOnInit(): void {
    this.bookingId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: BookingRequestStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: BookingRequestStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected itemStatusLabel(status: BookingItemStatus): string {
    return ITEM_STATUS_LABELS[status];
  }

  protected itemStatusSeverity(status: BookingItemStatus): TagSeverity {
    return ITEM_STATUS_SEVERITY[status];
  }

  protected itemTypeLabel(type: ProposalItemType): string {
    return ITEM_TYPE_LABELS[type];
  }

  protected orderStatusLabel(status: CommercialOrderStatus): string {
    return ORDER_STATUS_LABELS[status];
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected proposalStatusLabel(status: ProposalStatus): string {
    return PROPOSAL_STATUS_LABELS[status];
  }

  /** The human-friendly reservation code — the source Order number rendered PC-000n. */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  protected back(): void {
    this.router.navigateByUrl('/reservas');
  }

  /** Esc returns to the reservation list. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.bookings.detail(this.bookingId).subscribe({
      next: (detail) => {
        this.booking.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta reserva.'
            : err.status === 404
              ? 'Reserva não encontrada.'
              : 'Não foi possível carregar a reserva.',
        );
      },
    });
  }
}
