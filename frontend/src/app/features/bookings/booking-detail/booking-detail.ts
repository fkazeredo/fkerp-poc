import { Component, HostListener, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Observable } from 'rxjs';
import {
  BookingAttemptResult,
  BookingAttemptType,
  BookingItemStatus,
  BookingRequestDetail,
  BookingRequestItem,
  BookingRequestStatus,
  BookingService,
} from '../../../core/api/booking.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { CommercialOrderStatus } from '../../../core/api/order.service';
import { ProposalItemType, ProposalStatus } from '../../../core/api/proposal.service';
import { AuthService } from '../../../core/auth/auth.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

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

const ATTEMPT_TYPE_LABELS: Record<BookingAttemptType, string> = {
  EXTERNAL_SYSTEM_ACCESS: 'Acesso a sistema externo',
  SUPPLIER_PHONE_CONTACT: 'Contato telefônico com fornecedor',
  SUPPLIER_EMAIL_CONTACT: 'Contato por e-mail com fornecedor',
  INTERNAL_VERIFICATION: 'Verificação interna',
  MANUAL_AVAILABILITY_CHECK: 'Checagem manual de disponibilidade',
  OTHER: 'Outro',
};

const ATTEMPT_RESULT_LABELS: Record<BookingAttemptResult, string> = {
  STARTED: 'Iniciada',
  WAITING_FOR_SUPPLIER: 'Aguardando fornecedor',
  WAITING_FOR_INTERNAL_INFO: 'Aguardando informação interna',
  AVAILABILITY_FOUND: 'Disponibilidade encontrada',
  AVAILABILITY_NOT_FOUND: 'Sem disponibilidade',
  NEEDS_RETRY: 'Precisa nova tentativa',
  FAILED: 'Falhou',
  OTHER: 'Outro',
};

const ATTEMPT_TYPE_OPTIONS = (Object.keys(ATTEMPT_TYPE_LABELS) as BookingAttemptType[]).map((value) => ({
  value,
  label: ATTEMPT_TYPE_LABELS[value],
}));
const ATTEMPT_RESULT_OPTIONS = (Object.keys(ATTEMPT_RESULT_LABELS) as BookingAttemptResult[]).map(
  (value) => ({ value, label: ATTEMPT_RESULT_LABELS[value] }),
);

// Sentinel option value for "the whole request" (no item link) in the item select.
const WHOLE_REQUEST = '';

/**
 * Booking Request detail page (Booking Operations): the operational reservation record — its status, the
 * booking items with their statuses (what must be reserved, what is confirmed, what failed), the manual
 * booking-attempt history, and the source Commercial Order / Proposal / Opportunity / Lead kept traceable.
 * Registering a manual attempt requires booking:request:update; it is append-only history that may move the
 * request PENDING → IN_PROGRESS but never confirms the booking nor creates financial/commission data.
 */
@Component({
  selector: 'app-booking-detail',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    TableModule,
    TagModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    InputTextModule,
    DatePickerModule,
    MessageModule,
  ],
  templateUrl: './booking-detail.html',
  styleUrl: './booking-detail.css',
})
export class BookingDetail implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookings = inject(BookingService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly unsaved = inject(UnsavedChangesService);

  protected readonly booking = signal<BookingRequestDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly attemptOpen = signal(false);
  protected readonly acting = signal(false);
  protected readonly attemptTypeOptions = ATTEMPT_TYPE_OPTIONS;
  protected readonly attemptResultOptions = ATTEMPT_RESULT_OPTIONS;
  protected attemptType: BookingAttemptType | null = null;
  protected attemptResult: BookingAttemptResult | null = null;
  protected attemptDescription = '';
  protected attemptOccurredAt: Date = new Date();
  protected attemptItemId: string = WHOLE_REQUEST;
  protected attemptNextActionDate: Date | null = null;

  // Confirm-travel-package dialog state.
  protected readonly confirmOpen = signal(false);
  protected confirmItemId = '';
  protected confirmItemLabel = '';
  protected confirmExternalSystem = '';
  protected confirmExternalLocator = '';
  protected confirmDate: Date = new Date();
  protected confirmPackageDescription = '';
  protected confirmTravelStart: Date | null = null;
  protected confirmTravelEnd: Date | null = null;
  protected confirmTravelerNotes = '';
  protected confirmOperationalNotes = '';

  /** Upper bound for the date pickers — an attempt/confirmation cannot have happened in the future. */
  protected readonly now = new Date();

  private bookingId = '';
  private editSnapshot = '';

  constructor() {
    effect(() => this.unsaved.set(this.anyDialogOpen()));
  }

  private anyDialogOpen(): boolean {
    return this.attemptOpen() || this.confirmOpen();
  }

  ngOnInit(): void {
    this.bookingId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  private liveSnapshot(): string {
    return JSON.stringify([
      this.attemptType,
      this.attemptResult,
      this.attemptDescription,
      this.attemptOccurredAt,
      this.attemptItemId,
      this.attemptNextActionDate,
      this.confirmExternalSystem,
      this.confirmExternalLocator,
      this.confirmDate,
      this.confirmPackageDescription,
      this.confirmTravelStart,
      this.confirmTravelEnd,
      this.confirmTravelerNotes,
      this.confirmOperationalNotes,
    ]);
  }

  /** Whether a dialog is open AND its fields changed since it opened. */
  hasUnsavedChanges(): boolean {
    return this.anyDialogOpen() && this.editSnapshot !== this.liveSnapshot();
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

  protected attemptTypeLabel(type: BookingAttemptType): string {
    return ATTEMPT_TYPE_LABELS[type];
  }

  protected attemptResultLabel(result: BookingAttemptResult): string {
    return ATTEMPT_RESULT_LABELS[result];
  }

  /** The human-friendly reservation code — the source Order number rendered PC-000n. */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  /** Whether the user may register a manual booking attempt (has the update scope). */
  protected canRegisterAttempt(): boolean {
    return this.auth.canOperateBookings() && this.booking() !== null;
  }

  /** The item-link options for the attempt dialog: the whole request plus each booking item. */
  protected itemOptions(): { value: string; label: string }[] {
    const items = this.booking()?.items ?? [];
    return [
      { value: WHOLE_REQUEST, label: 'Reserva toda' },
      ...items.map((i) => ({ value: i.id, label: `${ITEM_TYPE_LABELS[i.type]} — ${i.description}` })),
    ];
  }

  /** The label for an attempt's linked item (or "Reserva toda" when it concerns the whole request). */
  protected attemptItemLabel(bookingItemId: string | null): string {
    if (!bookingItemId) {
      return 'Reserva toda';
    }
    const item = this.booking()?.items.find((i) => i.id === bookingItemId);
    return item ? `${ITEM_TYPE_LABELS[item.type]} — ${item.description}` : 'Item da reserva';
  }

  protected openAttempt(): void {
    this.attemptType = null;
    this.attemptResult = null;
    this.attemptDescription = '';
    this.attemptOccurredAt = new Date();
    this.attemptItemId = WHOLE_REQUEST;
    this.attemptNextActionDate = null;
    this.attemptOpen.set(true);
    this.editSnapshot = this.liveSnapshot();
  }

  protected canSaveAttempt(): boolean {
    return (
      !!this.attemptType &&
      !!this.attemptResult &&
      this.attemptDescription.trim().length > 0 &&
      !!this.attemptOccurredAt
    );
  }

  protected confirmAttempt(): void {
    if (!this.canSaveAttempt()) {
      return;
    }
    this.act(
      this.bookings.registerAttempt(this.bookingId, {
        bookingItemId: this.attemptItemId || null,
        type: this.attemptType!,
        result: this.attemptResult!,
        description: this.attemptDescription.trim(),
        occurredAt: this.attemptOccurredAt.toISOString(),
        nextActionDate: toIsoDate(this.attemptNextActionDate),
      }),
      'Tentativa registrada',
      this.attemptOpen,
    );
  }

  /** The confirmed booking items (those carrying a confirmation block), for the confirmations card. */
  protected confirmations(): BookingRequestItem[] {
    return this.booking()?.items.filter((i) => i.confirmation) ?? [];
  }

  /** Whether this booking item can be confirmed through the Travel Package flow by the current user. */
  protected canConfirmItem(item: BookingRequestItem): boolean {
    return (
      this.auth.canOperateBookings() &&
      item.type === 'TRAVEL_PACKAGE' &&
      item.requiresBooking &&
      item.status !== 'CONFIRMED' &&
      item.status !== 'CANCELLED'
    );
  }

  protected openConfirm(item: BookingRequestItem): void {
    this.confirmItemId = item.id;
    this.confirmItemLabel = `${ITEM_TYPE_LABELS[item.type]} — ${item.description}`;
    this.confirmExternalSystem = '';
    this.confirmExternalLocator = '';
    this.confirmDate = new Date();
    this.confirmPackageDescription = '';
    this.confirmTravelStart = null;
    this.confirmTravelEnd = null;
    this.confirmTravelerNotes = '';
    this.confirmOperationalNotes = '';
    this.confirmOpen.set(true);
    this.editSnapshot = this.liveSnapshot();
  }

  protected canSaveConfirm(): boolean {
    return (
      this.confirmExternalSystem.trim().length > 0 &&
      this.confirmExternalLocator.trim().length > 0 &&
      !!this.confirmDate
    );
  }

  protected confirmItem(): void {
    if (!this.canSaveConfirm()) {
      return;
    }
    this.act(
      this.bookings.confirmTravelPackage(this.bookingId, this.confirmItemId, {
        externalSystem: this.confirmExternalSystem.trim(),
        externalLocator: this.confirmExternalLocator.trim(),
        confirmedAt: this.confirmDate.toISOString(),
        packageDescription: this.confirmPackageDescription.trim() || null,
        travelStartDate: toIsoDate(this.confirmTravelStart),
        travelEndDate: toIsoDate(this.confirmTravelEnd),
        travelerNotes: this.confirmTravelerNotes.trim() || null,
        operationalNotes: this.confirmOperationalNotes.trim() || null,
      }),
      'Reserva confirmada',
      this.confirmOpen,
    );
  }

  /** Closes a dialog, confirming first if it has unsaved edits. */
  protected async requestClose(open: { set: (v: boolean) => void }): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    open.set(false);
  }

  private closeOpenDialog(): void {
    for (const open of [this.attemptOpen, this.confirmOpen]) {
      if (open()) {
        void this.requestClose(open);
        return;
      }
    }
  }

  protected back(): void {
    this.router.navigateByUrl('/reservas');
  }

  /** Shortcuts: a register attempt, Esc closes the open dialog (guarded) or returns to the list. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.anyDialogOpen()) {
      this.closeOpenDialog();
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.anyDialogOpen()) {
      return;
    }
    if (event.key === 'a' && this.canRegisterAttempt()) {
      this.openAttempt();
    } else if (event.key === 'Escape') {
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

  private act(
    action: Observable<BookingRequestDetail>,
    successSummary: string,
    dialog: { set: (v: boolean) => void },
  ): void {
    this.acting.set(true);
    action.subscribe({
      next: (detail) => {
        this.booking.set(detail);
        this.acting.set(false);
        dialog.set(false);
        this.messages.add({ severity: 'success', summary: successSummary });
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        const body = err.error as { message?: string } | null;
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: body?.message ?? 'Não foi possível concluir a ação.',
        });
      },
    });
  }
}

function toIsoDate(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
