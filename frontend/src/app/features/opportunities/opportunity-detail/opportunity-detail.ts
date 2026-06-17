import { Component, HostListener, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Observable } from 'rxjs';
import {
  OpportunityActivityResult,
  OpportunityActivityType,
  OpportunityDetail,
  OpportunityLossReason,
  OpportunityService,
  OpportunityStage,
} from '../../../core/api/opportunity.service';
import { AuthService } from '../../../core/auth/auth.service';

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  LOST: 'Perdida',
};

// The single forward step allowed from each stage (the pipeline is a strict funnel; LOST is via the
// lose action). READY_FOR_PROPOSAL and LOST have no further advance.
const NEXT_STAGE: Partial<Record<OpportunityStage, OpportunityStage>> = {
  NEW_OPPORTUNITY: 'DISCOVERY',
  DISCOVERY: 'PRODUCT_FIT',
  PRODUCT_FIT: 'READY_FOR_PROPOSAL',
};

const ACTIVITY_TYPE_LABELS: Record<OpportunityActivityType, string> = {
  PHONE_CALL: 'Ligação',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'E-mail',
  MEETING: 'Reunião',
  INTERNAL_NOTE: 'Nota interna',
  DOCUMENT_REQUEST: 'Solicitação de documento',
  PRICE_DISCUSSION: 'Discussão de preço',
  TRAVEL_REQUIREMENT_CLARIFICATION: 'Esclarecimento de requisito de viagem',
  OTHER: 'Outro',
};

const ACTIVITY_RESULT_LABELS: Record<OpportunityActivityResult, string> = {
  CLIENT_ENGAGED: 'Cliente engajado',
  NEEDS_FOLLOW_UP: 'Precisa follow-up',
  WAITING_FOR_CLIENT: 'Aguardando cliente',
  WAITING_FOR_INTERNAL_INFO: 'Aguardando informação interna',
  PRODUCT_FIT_IDENTIFIED: 'Aderência identificada',
  READY_FOR_PROPOSAL: 'Pronta para proposta',
  NOT_INTERESTED: 'Sem interesse',
  OTHER: 'Outro',
};

const ACTIVITY_TYPE_OPTIONS = (Object.keys(ACTIVITY_TYPE_LABELS) as OpportunityActivityType[]).map(
  (value) => ({ value, label: ACTIVITY_TYPE_LABELS[value] }),
);
const ACTIVITY_RESULT_OPTIONS = (Object.keys(ACTIVITY_RESULT_LABELS) as OpportunityActivityResult[]).map(
  (value) => ({ value, label: ACTIVITY_RESULT_LABELS[value] }),
);

const LOSS_REASON_LABELS: Record<OpportunityLossReason, string> = {
  NO_BUDGET: 'Sem orçamento',
  NO_DECISION: 'Sem decisão',
  NO_RESPONSE: 'Sem resposta',
  COMPETITOR_CHOSEN: 'Concorrente escolhido',
  PRODUCT_MISMATCH: 'Incompatibilidade de produto',
  PRICE_TOO_HIGH: 'Preço muito alto',
  TRAVEL_CANCELLED: 'Viagem cancelada',
  DUPLICATED_OPPORTUNITY: 'Oportunidade duplicada',
  OUT_OF_PROFILE: 'Fora do perfil',
  OTHER: 'Outro',
};

const LOSS_REASON_OPTIONS = (Object.keys(LOSS_REASON_LABELS) as OpportunityLossReason[]).map((value) => ({
  value,
  label: LOSS_REASON_LABELS[value],
}));

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/**
 * Opportunity detail page: commercial summary, the traceable source Lead, the loss outcome when LOST,
 * the pipeline stage-movement history and the commercial activity history. Operations (register
 * activity, advance stage, mark as lost) require crm:opportunity:update. The detail never shows
 * Proposal, Sale, Booking, Financial or Commission data.
 */
@Component({
  selector: 'app-opportunity-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    TagModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    MessageModule,
  ],
  templateUrl: './opportunity-detail.html',
  styleUrl: './opportunity-detail.css',
})
export class OpportunityDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly opportunities = inject(OpportunityService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);

  protected readonly opportunity = signal<OpportunityDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly loseOpen = signal(false);
  protected readonly acting = signal(false);
  protected readonly lossReasonOptions = LOSS_REASON_OPTIONS;
  protected lossReason: OpportunityLossReason | null = null;
  protected lossNote = '';

  protected readonly stageOpen = signal(false);
  protected targetStage: OpportunityStage | null = null;

  protected readonly activityOpen = signal(false);
  protected readonly activityTypeOptions = ACTIVITY_TYPE_OPTIONS;
  protected readonly activityResultOptions = ACTIVITY_RESULT_OPTIONS;
  protected activityType: OpportunityActivityType | null = null;
  protected activityResult: OpportunityActivityResult | null = null;
  protected activityDescription = '';
  protected activityOccurredAt: Date = new Date();
  protected activityNextActionDate: Date | null = null;

  /** Upper bound for the activity date picker — an activity cannot have happened in the future. */
  protected readonly now = new Date();

  protected readonly editOpen = signal(false);
  protected editEstimatedValue: number | null = null;
  protected editExpectedCloseDate: Date | null = null;
  protected editProductType = '';
  protected editNotes = '';

  private opportunityId = '';

  ngOnInit(): void {
    this.opportunityId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected stageSeverity(stage: OpportunityStage): TagSeverity {
    switch (stage) {
      case 'NEW_OPPORTUNITY':
        return 'info';
      case 'DISCOVERY':
        return 'secondary';
      case 'PRODUCT_FIT':
        return 'warn';
      case 'READY_FOR_PROPOSAL':
        return 'success';
      case 'LOST':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  /** Whether the user may mark this Opportunity as lost (has the update scope and it is not already lost). */
  protected canLose(): boolean {
    const stage = this.opportunity()?.stage;
    return this.auth.canOperateOpportunity() && !!stage && stage !== 'LOST';
  }

  /** Whether the user may advance this Opportunity (has the update scope and a forward stage exists). */
  protected canChangeStage(): boolean {
    return this.auth.canOperateOpportunity() && this.nextStage() !== null;
  }

  /** The single forward step this Opportunity can advance to (strict funnel), or null. */
  protected nextStage(): OpportunityStage | null {
    const stage = this.opportunity()?.stage;
    return (stage && NEXT_STAGE[stage]) || null;
  }

  /** The advance target offered in the dialog — only the immediate next stage. */
  protected stageOptions(): { value: OpportunityStage; label: string }[] {
    const next = this.nextStage();
    return next ? [{ value: next, label: STAGE_LABELS[next] }] : [];
  }

  /** Whether the user may register a commercial activity (has the update scope; any stage). */
  protected canRegisterActivity(): boolean {
    return this.auth.canOperateOpportunity() && this.opportunity() !== null;
  }

  /** Whether the user may edit the commercial details (has the update scope; any stage). */
  protected canEditDetails(): boolean {
    return this.auth.canOperateOpportunity() && this.opportunity() !== null;
  }

  protected activityTypeLabel(type: OpportunityActivityType): string {
    return ACTIVITY_TYPE_LABELS[type];
  }

  protected activityResultLabel(result: OpportunityActivityResult): string {
    return ACTIVITY_RESULT_LABELS[result];
  }

  protected back(): void {
    this.router.navigateByUrl('/oportunidades');
  }

  protected lossReasonLabel(reason: OpportunityLossReason): string {
    return LOSS_REASON_LABELS[reason];
  }

  protected openLose(): void {
    this.lossReason = null;
    this.lossNote = '';
    this.loseOpen.set(true);
  }

  protected confirmLose(): void {
    if (!this.lossReason) {
      return;
    }
    this.act(
      this.opportunities.lose(this.opportunityId, this.lossReason, this.lossNote || null),
      'Oportunidade marcada como perdida',
      this.loseOpen,
    );
  }

  protected openStage(): void {
    this.targetStage = this.nextStage();
    this.stageOpen.set(true);
  }

  protected confirmStage(): void {
    if (!this.targetStage) {
      return;
    }
    this.act(
      this.opportunities.changeStage(this.opportunityId, this.targetStage),
      'Estágio atualizado',
      this.stageOpen,
    );
  }

  protected openActivity(): void {
    this.activityType = null;
    this.activityResult = null;
    this.activityDescription = '';
    this.activityOccurredAt = new Date();
    this.activityNextActionDate = null;
    this.activityOpen.set(true);
  }

  protected canSaveActivity(): boolean {
    return (
      !!this.activityType &&
      !!this.activityResult &&
      this.activityDescription.trim().length > 0 &&
      !!this.activityOccurredAt
    );
  }

  protected confirmActivity(): void {
    if (!this.canSaveActivity()) {
      return;
    }
    this.act(
      this.opportunities.registerActivity(this.opportunityId, {
        type: this.activityType!,
        result: this.activityResult!,
        description: this.activityDescription.trim(),
        occurredAt: this.activityOccurredAt.toISOString(),
        nextActionDate: toIsoDate(this.activityNextActionDate),
      }),
      'Atividade registrada',
      this.activityOpen,
    );
  }

  protected openEdit(): void {
    const o = this.opportunity();
    this.editEstimatedValue = o?.estimatedValue ?? null;
    this.editExpectedCloseDate = parseLocalDate(o?.expectedCloseDate ?? null);
    this.editProductType = o?.productType ?? '';
    this.editNotes = o?.notes ?? '';
    this.editOpen.set(true);
  }

  protected confirmEdit(): void {
    this.act(
      this.opportunities.updateDetails(this.opportunityId, {
        estimatedValue: this.editEstimatedValue,
        expectedCloseDate: toIsoDate(this.editExpectedCloseDate),
        productType: this.editProductType.trim() || null,
        notes: this.editNotes.trim() || null,
      }),
      'Dados comerciais atualizados',
      this.editOpen,
    );
  }

  /** Shortcuts: a register activity, e edit details, s change stage, p lose, Esc back. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (
      typing ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      this.loseOpen() ||
      this.stageOpen() ||
      this.activityOpen() ||
      this.editOpen()
    ) {
      return;
    }
    if (event.key === 'a' && this.canRegisterActivity()) {
      this.openActivity();
    } else if (event.key === 'e' && this.canEditDetails()) {
      this.openEdit();
    } else if (event.key === 's' && this.canChangeStage()) {
      this.openStage();
    } else if (event.key === 'p' && this.canLose()) {
      this.openLose();
    } else if (event.key === 'Escape') {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.opportunities.detail(this.opportunityId).subscribe({
      next: (detail) => {
        this.opportunity.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta oportunidade.'
            : err.status === 404
              ? 'Oportunidade não encontrada.'
              : 'Não foi possível carregar a oportunidade.',
        );
      },
    });
  }

  private act(
    action: Observable<OpportunityDetail>,
    successSummary: string,
    dialog: { set: (v: boolean) => void },
  ): void {
    this.acting.set(true);
    action.subscribe({
      next: (detail) => {
        this.opportunity.set(detail);
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

/** Parses an ISO `yyyy-MM-dd` into a local-midnight Date (avoids the UTC-parse day shift). */
function parseLocalDate(value: string | null): Date | null {
  if (!value) {
    return null;
  }
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}
