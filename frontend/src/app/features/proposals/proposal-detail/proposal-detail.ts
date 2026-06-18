import { Component, HostListener, effect, inject, signal, OnDestroy, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Observable } from 'rxjs';
import {
  DiscountType,
  ProposalDetail,
  ProposalItem,
  ProposalItemType,
  ProposalService,
  ProposalStatus,
  UpdateProposal,
} from '../../../core/api/proposal.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { AuthService } from '../../../core/auth/auth.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

const STATUS_LABELS: Record<ProposalStatus, string> = {
  DRAFT: 'Rascunho',
  READY_FOR_REVIEW: 'Pronta para revisão',
  APPROVED: 'Aprovada',
  SENT: 'Enviada',
  ACCEPTED: 'Aceita',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  LOST: 'Perdida',
};

const ITEM_TYPE_LABELS: Record<ProposalItemType, string> = {
  TRAVEL_PACKAGE: 'Pacote de viagem',
  CAR_RENTAL: 'Locação de veículo',
  SERVICE_FEE: 'Taxa de serviço',
  OTHER: 'Outro',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<ProposalStatus, TagSeverity> = {
  DRAFT: 'secondary',
  READY_FOR_REVIEW: 'info',
  APPROVED: 'info',
  SENT: 'warn',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'danger',
  CANCELLED: 'danger',
};

type DiscountMode = 'NONE' | DiscountType;

/**
 * Proposal detail page (Sales & Proposals): the commercial offer's summary, status, source Opportunity,
 * and its **items** (what the company intends to sell). While the Proposal is a Draft and the user may
 * operate it, items can be added, edited and removed; each contributes to the Proposal total. Shows
 * commercial-offer data only — never sale/order/booking/financial data.
 */
@Component({
  selector: 'app-proposal-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    TableModule,
    TagModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    DatePickerModule,
    MessageModule,
  ],
  templateUrl: './proposal-detail.html',
  styleUrl: './proposal-detail.css',
})
export class ProposalDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly proposals = inject(ProposalService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly unsaved = inject(UnsavedChangesService);

  constructor() {
    // An open edit dialog means an in-progress edit; keep the global flag in sync for the tab-close warning.
    effect(() => this.unsaved.set(this.hasUnsavedChanges()));
  }

  protected readonly proposal = signal<ProposalDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly itemOpen = signal(false);
  protected readonly detailsOpen = signal(false);
  protected readonly acting = signal(false);
  protected readonly itemTypeOptions = (Object.keys(ITEM_TYPE_LABELS) as ProposalItemType[]).map(
    (value) => ({ value, label: ITEM_TYPE_LABELS[value] }),
  );
  protected readonly discountModeOptions: { value: DiscountMode; label: string }[] = [
    { value: 'NONE', label: 'Sem desconto' },
    { value: 'AMOUNT', label: 'Valor (R$)' },
    { value: 'PERCENT', label: 'Percentual (%)' },
  ];

  protected editingItemId: string | null = null;
  protected itemType: ProposalItemType = 'TRAVEL_PACKAGE';
  protected itemDescription = '';
  protected itemQuantity = 1;
  protected itemUnitValue: number | null = null;
  protected itemDiscountMode: DiscountMode = 'NONE';
  protected itemDiscountValue: number | null = null;

  // Commercial-details dialog (validity, terms, payment notes, Proposal-level discount).
  protected detailsValidUntil: Date | null = null;
  protected detailsCommercialTerms = '';
  protected detailsPaymentNotes = '';
  protected detailsDiscountMode: DiscountMode = 'NONE';
  protected detailsDiscountValue: number | null = null;

  private proposalId = '';

  ngOnInit(): void {
    this.proposalId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  // Snapshot of the open dialog's fields, captured when it opens, to detect real edits.
  private editSnapshot = '';

  private liveSnapshot(): string {
    if (this.itemOpen()) {
      return JSON.stringify([
        this.itemType,
        this.itemDescription,
        this.itemQuantity,
        this.itemUnitValue,
        this.itemDiscountMode,
        this.itemDiscountValue,
      ]);
    }
    if (this.detailsOpen()) {
      return JSON.stringify([
        this.detailsValidUntil,
        this.detailsCommercialTerms,
        this.detailsPaymentNotes,
        this.detailsDiscountMode,
        this.detailsDiscountValue,
      ]);
    }
    return '';
  }

  /** Whether an edit dialog is open AND its fields were changed since it opened. */
  private dialogDirty(): boolean {
    return (this.itemOpen() || this.detailsOpen()) && this.editSnapshot !== this.liveSnapshot();
  }

  /** Used by the route guard and the tab-close warning: warns only when there are real edits. */
  hasUnsavedChanges(): boolean {
    return this.dialogDirty();
  }

  /** Closes the item dialog, confirming first if it has unsaved edits. */
  protected async requestCloseItem(): Promise<void> {
    if (this.dialogDirty() && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.itemOpen.set(false);
  }

  /** Closes the commercial-details dialog, confirming first if it has unsaved edits. */
  protected async requestCloseDetails(): Promise<void> {
    if (this.dialogDirty() && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.detailsOpen.set(false);
  }

  protected statusLabel(status: ProposalStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ProposalStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected itemTypeLabel(type: ProposalItemType): string {
    return ITEM_TYPE_LABELS[type];
  }

  /** Whether the user may add/edit/remove items (has the operate scope and the Proposal is a Draft). */
  protected canManageItems(): boolean {
    return this.auth.canOperateProposal() && this.proposal()?.status === 'DRAFT';
  }

  protected back(): void {
    this.router.navigateByUrl('/propostas');
  }

  protected openAddItem(): void {
    this.editingItemId = null;
    this.itemType = 'TRAVEL_PACKAGE';
    this.itemDescription = '';
    this.itemQuantity = 1;
    this.itemUnitValue = null;
    this.itemDiscountMode = 'NONE';
    this.itemDiscountValue = null;
    this.itemOpen.set(true);
    this.editSnapshot = this.liveSnapshot();
  }

  protected openEditItem(item: ProposalItem): void {
    this.editingItemId = item.id;
    this.itemType = item.type;
    this.itemDescription = item.description;
    this.itemQuantity = item.quantity;
    this.itemUnitValue = item.unitValue;
    this.itemDiscountMode = item.discountType ?? 'NONE';
    this.itemDiscountValue = item.discountValue;
    this.itemOpen.set(true);
    this.editSnapshot = this.liveSnapshot();
  }

  protected canSaveItem(): boolean {
    return (
      this.itemDescription.trim().length > 0 &&
      this.itemQuantity >= 1 &&
      this.itemUnitValue != null &&
      (this.itemDiscountMode === 'NONE' || this.itemDiscountValue != null)
    );
  }

  protected confirmItem(): void {
    if (!this.canSaveItem()) {
      return;
    }
    const payload = {
      type: this.itemType,
      description: this.itemDescription.trim(),
      quantity: this.itemQuantity,
      unitValue: this.itemUnitValue!,
      discountType: this.itemDiscountMode === 'NONE' ? null : this.itemDiscountMode,
      discountValue: this.itemDiscountMode === 'NONE' ? null : this.itemDiscountValue,
    };
    const action = this.editingItemId
      ? this.proposals.updateItem(this.proposalId, this.editingItemId, payload)
      : this.proposals.addItem(this.proposalId, payload);
    this.act(action, this.editingItemId ? 'Item atualizado' : 'Item adicionado', this.itemOpen);
  }

  protected removeItem(item: ProposalItem): void {
    this.act(this.proposals.removeItem(this.proposalId, item.id), 'Item removido');
  }

  /** Opens the commercial-details dialog, pre-filled from the current Proposal. */
  protected openEditDetails(): void {
    const p = this.proposal();
    if (!p) {
      return;
    }
    this.detailsValidUntil = p.validUntil ? new Date(p.validUntil) : null;
    this.detailsCommercialTerms = p.commercialTerms ?? '';
    this.detailsPaymentNotes = p.paymentNotes ?? '';
    this.detailsDiscountMode = p.discountType ?? 'NONE';
    this.detailsDiscountValue = p.discountValue;
    this.detailsOpen.set(true);
    this.editSnapshot = this.liveSnapshot();
  }

  protected canSaveDetails(): boolean {
    return this.detailsDiscountMode === 'NONE' || this.detailsDiscountValue != null;
  }

  protected confirmEditDetails(): void {
    if (!this.canSaveDetails()) {
      return;
    }
    const payload: UpdateProposal = {
      validUntil: this.detailsValidUntil ? toIsoDate(this.detailsValidUntil) : null,
      commercialTerms: this.detailsCommercialTerms.trim() || null,
      paymentNotes: this.detailsPaymentNotes.trim() || null,
      discountType: this.detailsDiscountMode === 'NONE' ? null : this.detailsDiscountMode,
      discountValue: this.detailsDiscountMode === 'NONE' ? null : this.detailsDiscountValue,
    };
    this.act(this.proposals.updateDetails(this.proposalId, payload), 'Dados atualizados', this.detailsOpen);
  }

  /** Whether the Proposal can be submitted for review (operable Draft with items and a positive total). */
  protected canSubmit(): boolean {
    const p = this.proposal();
    return this.canManageItems() && !!p && p.items.length > 0 && p.total > 0;
  }

  protected submitForReview(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.act(this.proposals.submitForReview(this.proposalId), 'Proposta enviada para revisão');
  }

  /** Shortcuts on the proposal detail: i add item, e edit commercial details, s submit for review, Esc back. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    // Esc always closes the open dialog through the unsaved-changes guard — even from a focused field
    // (the dialogs disable PrimeNG's own Esc close so it can't bypass the guard).
    if (event.key === 'Escape' && (this.itemOpen() || this.detailsOpen())) {
      if (this.itemOpen()) {
        void this.requestCloseItem();
      } else {
        void this.requestCloseDetails();
      }
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.itemOpen() || this.detailsOpen()) {
      return;
    }
    if (event.key === 'i' && this.canManageItems()) {
      this.openAddItem();
    } else if (event.key === 'e' && this.canManageItems()) {
      this.openEditDetails();
    } else if (event.key === 's' && this.canSubmit()) {
      this.submitForReview();
    } else if (event.key === 'Escape') {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.proposals.detail(this.proposalId).subscribe({
      next: (detail) => {
        this.proposal.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta proposta.'
            : err.status === 404
              ? 'Proposta não encontrada.'
              : 'Não foi possível carregar a proposta.',
        );
      },
    });
  }

  private act(action: Observable<ProposalDetail>, successSummary: string, dialog?: { set: (v: boolean) => void }): void {
    this.acting.set(true);
    action.subscribe({
      next: (detail) => {
        this.proposal.set(detail);
        this.acting.set(false);
        dialog?.set(false);
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

/** Formats a local Date as an ISO calendar date (yyyy-MM-dd), as the validity contract expects. */
function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = `${date.getMonth() + 1}`.padStart(2, '0');
  const d = `${date.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${d}`;
}
