import { Component, HostListener, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import {
  CommercialOrderDetail,
  CommercialOrderStatus,
  OrderCommissionStatus,
  OrderService,
} from '../../../core/api/order.service';
import {
  CommissionBasis,
  CommissionListItem,
  CommissionService,
  CommissionStatus,
} from '../../../core/api/commission.service';
import { BookingRequestStatus } from '../../../core/api/booking.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { ProposalItemType, ProposalStatus } from '../../../core/api/proposal.service';
import { ReceivableStatus } from '../../../core/api/receivable.service';
import { ContactMethod, CustomerDetail, CustomerService } from '../../../core/api/customer.service';
import { AuthService } from '../../../core/auth/auth.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

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

// The commission-status summary reflected onto the Order (Slice 10) — distinct from the live commission panel: a
// read-only mirror visible to any order reader (even without the commission scope). ISSUE = a voided commission.
const ORDER_COMMISSION_STATUS_LABELS: Record<OrderCommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  PAID: 'Paga',
  ISSUE: 'Problema na comissão',
};

const ORDER_COMMISSION_STATUS_SEVERITY: Record<OrderCommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  PAID: 'success',
  ISSUE: 'danger',
};

const ORDER_COMMISSION_STATUS_HINTS: Record<OrderCommissionStatus, string> = {
  EXPECTED: 'Comissão prevista para a venda — ainda uma previsão.',
  ELIGIBLE: 'Comissão pendente de aprovação (conta a receber paga).',
  APPROVED: 'Comissão aprovada — pronta para pagamento.',
  PAID: 'Comissão paga — ciclo da comissão encerrado.',
  ISSUE: 'Comissão cancelada ou rejeitada — requer atenção.',
};

const COMMISSION_BASIS_LABELS: Record<CommissionBasis, string> = {
  COMMERCIAL_AMOUNT: 'Valor comercial (previsão)',
  RECEIVED_AMOUNT: 'Valor recebido',
};

const COMMISSION_STATUS_LABELS: Record<CommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  PAID: 'Paga',
  CANCELLED: 'Cancelada',
};

const COMMISSION_STATUS_SEVERITY: Record<CommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
  PAID: 'success',
  CANCELLED: 'secondary',
};

/**
 * Commercial Order detail page (Sales & Proposals): the formal, read-only record of the closed deal — its
 * status, the snapshot of the sold items and total, and the source Proposal / Opportunity / Lead kept
 * traceable. Shows commercial-order data only — never booking, receivable, payment or commission data.
 */
@Component({
  selector: 'app-order-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    TableModule,
    TagModule,
    MessageModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    InputTextModule,
  ],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.css',
})
export class OrderDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orders = inject(OrderService);
  private readonly commissions = inject(CommissionService);
  private readonly customers = inject(CustomerService);
  private readonly auth = inject(AuthService);
  private readonly messages = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  private readonly unsaved = inject(UnsavedChangesService);

  protected readonly order = signal<CommercialOrderDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // This Order's active commission (fetched on load when the user may read commissions, and refreshed after
  // generating). Shows whether it is still a forecast (Prevista) or eligible for approval (Pendente de aprovação).
  protected readonly commission = signal<CommissionListItem | null>(null);
  protected readonly generating = signal(false);

  // Consolidate-customer dialog state (Customer Management — Sprint 7 Slice 1). Only the order id is required;
  // the fields prefill from the source Lead and are optional.
  protected readonly customerDialogOpen = signal(false);
  protected readonly consolidating = signal(false);
  protected readonly customerError = signal<string | null>(null);
  protected readonly consolidatedCustomer = signal<CustomerDetail | null>(null);
  protected readonly contactMethodOptions: { label: string; value: ContactMethod }[] = [
    { label: 'E-mail', value: 'EMAIL' },
    { label: 'Telefone', value: 'PHONE' },
    { label: 'WhatsApp', value: 'WHATSAPP' },
  ];
  protected readonly customerForm = this.fb.nonNullable.group({
    name: ['', Validators.maxLength(200)],
    document: ['', Validators.maxLength(30)],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.pattern(/^\d*$/), Validators.maxLength(30)]],
    whatsapp: ['', [Validators.pattern(/^\d*$/), Validators.maxLength(30)]],
    preferredContactMethod: [null as ContactMethod | null],
    notes: ['', Validators.maxLength(2000)],
  });

  private orderId = '';

  constructor() {
    // Keep the app-wide unsaved flag (tab-close warning) in sync with the open consolidate dialog.
    effect(() => this.unsaved.set(this.customerDialogOpen()));
  }

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
    if (this.auth.canSeeCommissions()) {
      this.commissions.byOrder(this.orderId).subscribe({ next: (c) => this.commission.set(c) });
    }
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether the open consolidate dialog has modified fields (route guard + tab-close warning). */
  hasUnsavedChanges(): boolean {
    return this.customerDialogOpen() && this.customerForm.dirty;
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

  /** Label of the commission-status summary reflected onto the Order (Slice 10). */
  protected orderCommissionLabel(status: OrderCommissionStatus): string {
    return ORDER_COMMISSION_STATUS_LABELS[status];
  }

  protected orderCommissionSeverity(status: OrderCommissionStatus): TagSeverity {
    return ORDER_COMMISSION_STATUS_SEVERITY[status];
  }

  /** A human hint about the commission reflection (PAID → cycle closed, ISSUE → voided, etc.). */
  protected orderCommissionHint(status: OrderCommissionStatus | null): string {
    return status ? ORDER_COMMISSION_STATUS_HINTS[status] : 'Sem comissão ainda.';
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

  /**
   * Whether to offer generating an Expected Commission from this Order: the Order is commercially closed (not
   * cancelled), the user may generate commissions, and no commission exists yet for this Order. The "has a
   * responsible / a positive total / an applicable active rule" rules stay on the backend (surfaced as a friendly
   * message on 422); the backend is the authority.
   */
  protected canGenerateCommission(): boolean {
    return (
      this.auth.canCreateCommission() && this.order()?.status !== 'CANCELLED' && this.commission() === null
    );
  }

  /** Human label for the commission's calculation basis (commercial forecast vs received amount). */
  protected basisLabel(basis: CommissionBasis): string {
    return COMMISSION_BASIS_LABELS[basis];
  }

  /** Human label for the commission status (ELIGIBLE is shown as "Pendente de aprovação"). */
  protected commissionStatusLabel(status: CommissionStatus): string {
    return COMMISSION_STATUS_LABELS[status];
  }

  protected commissionStatusSeverity(status: CommissionStatus): TagSeverity {
    return COMMISSION_STATUS_SEVERITY[status];
  }

  /**
   * Generates the Expected Commission for this Order and shows its summary inline. After generating, the order's
   * commission is re-fetched (so the panel reflects its real status — already Eligible if the receivable was paid).
   * Friendly messages surface the "already generated" (409) and the business-rule (422) cases.
   */
  protected generateCommission(): void {
    const o = this.order();
    if (!o || this.generating() || !this.canGenerateCommission()) {
      return;
    }
    this.generating.set(true);
    this.commissions.generate(o.id).subscribe({
      next: () => {
        this.commissions.byOrder(o.id).subscribe({
          next: (c) => {
            this.generating.set(false);
            this.commission.set(c);
            this.messages.add({ severity: 'success', summary: 'Comissão prevista gerada' });
          },
          error: () => {
            this.generating.set(false);
            this.messages.add({ severity: 'success', summary: 'Comissão prevista gerada' });
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.generating.set(false);
        this.messages.add({ severity: 'error', summary: this.commissionError(err) });
      },
    });
  }

  private commissionError(err: HttpErrorResponse): string {
    if (err.status === 409) {
      return 'Comissão já gerada para este pedido.';
    }
    const body = err.error as { message?: string } | null;
    if (err.status === 422) {
      return body?.message ?? 'Não foi possível gerar a comissão para este pedido.';
    }
    return body?.message ?? 'Não foi possível gerar a comissão.';
  }

  /**
   * Whether to offer creating/consolidating a Customer from this Order: the user holds the Customer Management
   * create scope and the Order is loaded. The "from a Commercial Order" rule and idempotency stay on the backend.
   */
  protected canConsolidateCustomer(): boolean {
    return this.auth.canCreateCustomer() && this.order() !== null;
  }

  /** Opens the consolidate dialog, prefilling the editable fields from the source Lead. */
  protected openConsolidate(): void {
    const o = this.order();
    if (!o || !this.canConsolidateCustomer()) {
      return;
    }
    this.customerError.set(null);
    this.customerForm.reset({
      name: o.sourceLead.name ?? '',
      document: '',
      email: o.sourceLead.email ?? '',
      phone: o.sourceLead.phone ?? '',
      whatsapp: o.sourceLead.whatsapp ?? '',
      preferredContactMethod: null,
      notes: '',
    });
    this.customerDialogOpen.set(true);
  }

  /** Closes the consolidate dialog: if the form was changed, confirms before discarding. */
  protected async closeConsolidate(): Promise<void> {
    if (this.customerForm.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.customerDialogOpen.set(false);
  }

  /** Creates/consolidates the Customer Profile from this Order and shows it inline. */
  protected submitConsolidate(): void {
    const o = this.order();
    if (!o || this.customerForm.invalid || this.consolidating()) {
      this.customerForm.markAllAsTouched();
      return;
    }
    this.consolidating.set(true);
    this.customerError.set(null);
    const v = this.customerForm.getRawValue();
    this.customers
      .createFromOrder({
        commercialOrderId: o.id,
        name: nullable(v.name),
        document: nullable(v.document),
        email: nullable(v.email),
        phone: nullable(v.phone),
        whatsapp: nullable(v.whatsapp),
        preferredContactMethod: v.preferredContactMethod,
        notes: nullable(v.notes),
      })
      .subscribe({
        next: (customer) => {
          this.consolidating.set(false);
          this.consolidatedCustomer.set(customer);
          this.customerForm.markAsPristine();
          this.customerDialogOpen.set(false);
          this.messages.add({ severity: 'success', summary: 'Cliente consolidado' });
        },
        error: (err: HttpErrorResponse) => {
          this.consolidating.set(false);
          this.customerError.set(this.customerErrorMessage(err));
        },
      });
  }

  private customerErrorMessage(err: HttpErrorResponse): string {
    const body = err.error as { message?: string } | null;
    if (err.status === 403) {
      return 'Você não tem permissão para consolidar clientes.';
    }
    if (err.status === 404) {
      return 'Pedido não encontrado.';
    }
    if (err.status === 400) {
      return 'Verifique os campos informados.';
    }
    return body?.message ?? 'Não foi possível consolidar o cliente.';
  }

  protected back(): void {
    const o = this.order();
    this.router.navigateByUrl(o ? '/propostas/' + o.proposalId : '/vendas');
  }

  /**
   * Keyboard: <kbd>c</kbd> generates the Expected Commission, <kbd>k</kbd> consolidates the Customer (each when
   * allowed); <kbd>Esc</kbd> closes the open dialog (guarded) or returns to the source Proposal.
   */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !event.metaKey && !event.ctrlKey && !event.altKey) {
      if (document.querySelector('.p-select-overlay')) {
        return;
      }
      if (this.customerDialogOpen()) {
        void this.closeConsolidate();
      } else {
        this.back();
      }
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target && (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.customerDialogOpen()) {
      return;
    }
    if (event.key === 'c' && this.canGenerateCommission()) {
      event.preventDefault();
      this.generateCommission();
    } else if (event.key === 'k' && this.canConsolidateCustomer()) {
      event.preventDefault();
      this.openConsolidate();
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

/** Trims a form string and maps an empty value to null (so an unfilled field keeps the Lead snapshot). */
function nullable(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
