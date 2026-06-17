import { Component, inject, signal, OnInit } from '@angular/core';
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
} from '../../../core/api/proposal.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';
import { AuthService } from '../../../core/auth/auth.service';

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
    MessageModule,
  ],
  templateUrl: './proposal-detail.html',
  styleUrl: './proposal-detail.css',
})
export class ProposalDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly proposals = inject(ProposalService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);

  protected readonly proposal = signal<ProposalDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly itemOpen = signal(false);
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

  private proposalId = '';

  ngOnInit(): void {
    this.proposalId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
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
