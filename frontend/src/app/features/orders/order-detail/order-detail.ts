import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import {
  CommercialOrderDetail,
  CommercialOrderStatus,
  OrderService,
} from '../../../core/api/order.service';
import { BookingRequestStatus } from '../../../core/api/booking.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { ProposalItemType, ProposalStatus } from '../../../core/api/proposal.service';
import { ReceivableStatus } from '../../../core/api/receivable.service';
import { AuthService } from '../../../core/auth/auth.service';

const STATUS_LABELS: Record<CommercialOrderStatus, string> = {
  PENDING_BOOKING: 'Pendente de reserva',
  BOOKING_NOT_REQUIRED: 'Reserva não necessária',
  CANCELLED: 'Cancelado',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<CommercialOrderStatus, TagSeverity> = {
  PENDING_BOOKING: 'warn',
  BOOKING_NOT_REQUIRED: 'success',
  CANCELLED: 'danger',
};

const ITEM_TYPE_LABELS: Record<ProposalItemType, string> = {
  TRAVEL_PACKAGE: 'Pacote de viagem',
  CAR_RENTAL: 'Locação de veículo',
  SERVICE_FEE: 'Taxa de serviço',
  OTHER: 'Outro',
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

const BOOKING_STATUS_LABELS: Record<BookingRequestStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  CONFIRMED: 'Confirmada',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelada',
};

const BOOKING_STATUS_SEVERITY: Record<BookingRequestStatus, TagSeverity> = {
  PENDING: 'warn',
  IN_PROGRESS: 'info',
  PARTIALLY_CONFIRMED: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELLED: 'secondary',
};

const BOOKING_STATUS_HINTS: Record<BookingRequestStatus, string> = {
  PENDING: 'Reserva pendente de início.',
  IN_PROGRESS: 'Reserva em andamento.',
  PARTIALLY_CONFIRMED: 'Reserva parcialmente confirmada.',
  CONFIRMED: 'Reserva confirmada — o pedido pode seguir para o Financeiro.',
  FAILED: 'Problema na reserva — requer atenção das operações.',
  CANCELLED: 'Reserva cancelada.',
};

const FINANCIAL_STATUS_LABELS: Record<ReceivableStatus, string> = {
  OPEN: 'Em aberto',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
  CANCELLED: 'Cancelada',
};

const FINANCIAL_STATUS_SEVERITY: Record<ReceivableStatus, TagSeverity> = {
  OPEN: 'info',
  PARTIALLY_PAID: 'warn',
  PAID: 'success',
  OVERDUE: 'danger',
  CANCELLED: 'secondary',
};

const FINANCIAL_STATUS_HINTS: Record<ReceivableStatus, string> = {
  OPEN: 'Conta a receber em aberto — aguardando pagamento.',
  PARTIALLY_PAID: 'Parcialmente paga — ainda há saldo a receber.',
  PAID: 'Paga — o pedido está pronto para o Comissionamento (Sprint 6).',
  OVERDUE: 'Vencida — problema financeiro a tratar.',
  CANCELLED: 'Conta a receber cancelada.',
};

/**
 * Commercial Order detail page (Sales & Proposals): the formal, read-only record of the closed deal — its
 * status, the snapshot of the sold items and total, and the source Proposal / Opportunity / Lead kept
 * traceable. Shows commercial-order data only — never booking, receivable, payment or commission data.
 */
@Component({
  selector: 'app-order-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, ButtonModule, CardModule, TableModule, TagModule, MessageModule],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.css',
})
export class OrderDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orders = inject(OrderService);
  private readonly auth = inject(AuthService);

  protected readonly order = signal<CommercialOrderDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private orderId = '';

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: CommercialOrderStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: CommercialOrderStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected itemTypeLabel(type: ProposalItemType): string {
    return ITEM_TYPE_LABELS[type];
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected proposalStatusLabel(status: ProposalStatus): string {
    return PROPOSAL_STATUS_LABELS[status];
  }

  protected bookingStatusLabel(status: BookingRequestStatus): string {
    return BOOKING_STATUS_LABELS[status];
  }

  protected bookingStatusSeverity(status: BookingRequestStatus): TagSeverity {
    return BOOKING_STATUS_SEVERITY[status];
  }

  /** A human hint about the booking reflection (CONFIRMED → ready for finance, FAILED → problem, etc.). */
  protected bookingHint(status: BookingRequestStatus | null): string {
    return status ? BOOKING_STATUS_HINTS[status] : 'Reserva ainda não iniciada.';
  }

  protected financialStatusLabel(status: ReceivableStatus): string {
    return FINANCIAL_STATUS_LABELS[status];
  }

  protected financialStatusSeverity(status: ReceivableStatus): TagSeverity {
    return FINANCIAL_STATUS_SEVERITY[status];
  }

  /** A human hint about the financial reflection (PAID → ready for commission, OVERDUE → problem, etc.). */
  protected financialHint(status: ReceivableStatus | null): string {
    return status ? FINANCIAL_STATUS_HINTS[status] : 'Sem conta a receber ainda.';
  }

  /** The human-friendly order code (PC-000n). */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  /** A note about the next operational step, derived from the order status. */
  protected nextStep(status: CommercialOrderStatus): string {
    if (status === 'PENDING_BOOKING') {
      return 'Pendente de reserva — a próxima etapa pode iniciar as operações de reserva.';
    }
    if (status === 'BOOKING_NOT_REQUIRED') {
      return 'Reserva não necessária para este pedido.';
    }
    return 'Pedido cancelado.';
  }

  /**
   * Whether to offer generating a Receivable from this Order: its booking is CONFIRMED (ready for Financial
   * Operations) and the user may create Receivables. The button only navigates to the Financeiro create form
   * pre-selecting this Order — the financial authority and the "no active receivable yet" rule stay on the
   * backend (the eligible-orders list there excludes Orders that already have one).
   */
  protected canGenerateReceivable(): boolean {
    return this.order()?.bookingStatus === 'CONFIRMED' && this.auth.canCreateReceivable();
  }

  /** Goes to the Receivable create form with this Order pre-selected. */
  protected generateReceivable(): void {
    const o = this.order();
    if (o) {
      this.router.navigate(['/financeiro/contas-a-receber/nova'], { queryParams: { order: o.id } });
    }
  }

  protected back(): void {
    const o = this.order();
    this.router.navigateByUrl(o ? '/propostas/' + o.proposalId : '/vendas');
  }

  /** Esc returns to the source Proposal. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orders.detail(this.orderId).subscribe({
      next: (detail) => {
        this.order.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver este pedido.'
            : err.status === 404
              ? 'Pedido não encontrado.'
              : 'Não foi possível carregar o pedido.',
        );
      },
    });
  }
}
