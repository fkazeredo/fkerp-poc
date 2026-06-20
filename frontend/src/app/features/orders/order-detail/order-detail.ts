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
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { ProposalItemType, ProposalStatus } from '../../../core/api/proposal.service';

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
