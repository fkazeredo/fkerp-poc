import { Component, HostListener, effect, inject, signal, OnDestroy, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Observable } from 'rxjs';
import { LeadDetail, LeadService, LeadStatus, Responsible } from '../../../core/api/lead.service';
import { ReferenceItem, ReferenceService } from '../../../core/api/reference.service';
import { CreateOpportunity, OpportunityService } from '../../../core/api/opportunity.service';
import { AuthService } from '../../../core/auth/auth.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Novo',
  CONTACTED: 'Em contato',
  QUALIFIED: 'Qualificado',
  LOST: 'Perdido',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/** Lead detail page: core data + commercial history, with qualify / lose / reassign actions. */
@Component({
  selector: 'app-lead-detail',
  imports: [
    DatePipe,
    FormsModule,
    ButtonModule,
    CardModule,
    TagModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    InputTextModule,
    DatePickerModule,
    MessageModule,
  ],
  templateUrl: './lead-detail.html',
  styleUrl: './lead-detail.css',
})
export class LeadDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly leads = inject(LeadService);
  private readonly references = inject(ReferenceService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly opportunities = inject(OpportunityService);
  private readonly unsaved = inject(UnsavedChangesService);

  constructor() {
    // An open edit dialog means an in-progress edit; keep the global flag in sync for the tab-close warning.
    effect(() => this.unsaved.set(this.hasUnsavedChanges()));
  }

  /** Whether an edit dialog is open (used by the route guard and the tab-close warning). */
  hasUnsavedChanges(): boolean {
    return (
      this.qualifyOpen() ||
      this.loseOpen() ||
      this.reassignOpen() ||
      this.interactionOpen() ||
      this.opportunityOpen()
    );
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  protected readonly lead = signal<LeadDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly qualifyOpen = signal(false);
  protected readonly loseOpen = signal(false);
  protected readonly reassignOpen = signal(false);
  protected readonly interactionOpen = signal(false);
  protected readonly opportunityOpen = signal(false);
  protected readonly acting = signal(false);

  protected readonly lossReasons = signal<ReferenceItem[]>([]);
  protected readonly responsibleOptions = signal<Responsible[]>([]);
  protected readonly interactionTypes = signal<ReferenceItem[]>([]);
  protected readonly interactionResults = signal<ReferenceItem[]>([]);

  protected qualifyMainInterest = '';
  protected qualifyNote = '';
  protected lossReasonId: string | null = null;
  protected lossNote = '';
  protected reassignTo: string | null = null;

  protected interactionTypeId: string | null = null;
  protected interactionResultId: string | null = null;
  protected interactionDescription = '';
  protected interactionOccurredAt: Date = new Date();
  protected interactionNextContactAt: Date | null = null;

  protected oppProductType = '';
  protected oppEstimatedValue: number | null = null;
  protected oppExpectedCloseDate: Date | null = null;
  protected oppResponsibleTo: string | null = null;
  protected oppNote = '';

  /** Upper bound for the interaction date picker — an interaction cannot have happened in the future. */
  protected readonly now = new Date();

  private leadId = '';

  ngOnInit(): void {
    this.leadId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: LeadStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: LeadStatus): TagSeverity {
    switch (status) {
      case 'NEW':
        return 'info';
      case 'CONTACTED':
        return 'warn';
      case 'QUALIFIED':
        return 'success';
      case 'LOST':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  /** Whether the user may perform operational actions (consultation-only users cannot). */
  protected canOperate(): boolean {
    return this.auth.canOperateLead();
  }

  protected canQualify(): boolean {
    const lead = this.lead();
    return this.canOperate() && !!lead && lead.status === 'CONTACTED' && !lead.unassigned;
  }

  protected canLose(): boolean {
    const status = this.lead()?.status;
    return this.canOperate() && !!status && status !== 'LOST';
  }

  protected canReassign(): boolean {
    const status = this.lead()?.status;
    return this.canOperate() && !!status && status !== 'LOST';
  }

  /** Full assignment authority (managers/admins): may reassign to anyone. */
  protected canAssign(): boolean {
    return this.auth.hasScope('crm:lead:assign');
  }

  /** A non-manager may claim (self-assign) an unassigned, non-lost lead they are viewing. */
  protected canClaim(): boolean {
    const lead = this.lead();
    return (
      this.canOperate() && !this.canAssign() && !!lead && lead.unassigned && lead.status !== 'LOST'
    );
  }

  /** A qualified lead may originate one commercial Opportunity (scope crm:opportunity:create). */
  protected canCreateOpportunity(): boolean {
    return this.auth.canCreateOpportunity() && this.lead()?.status === 'QUALIFIED';
  }

  protected back(): void {
    this.router.navigateByUrl('/leads');
  }

  private anyDialogOpen(): boolean {
    return (
      this.qualifyOpen() ||
      this.loseOpen() ||
      this.reassignOpen() ||
      this.interactionOpen() ||
      this.opportunityOpen()
    );
  }

  /** Contextual shortcuts on the detail screen: i interaction, q qualify, p lose, r reassign/claim, Esc back. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    if (this.anyDialogOpen()) {
      return; // let PrimeNG handle Esc/typing while a dialog is open
    }
    switch (event.key) {
      case 'i':
        if (this.canOperate()) {
          this.openInteraction();
        }
        break;
      case 'q':
        if (this.canQualify()) {
          this.openQualify();
        }
        break;
      case 'p':
        if (this.canLose()) {
          this.openLose();
        }
        break;
      case 'r':
        if (this.canAssign() && this.canReassign()) {
          this.openReassign();
        } else if (this.canClaim()) {
          this.claim();
        }
        break;
      case 'o':
        if (this.canCreateOpportunity()) {
          this.openOpportunity();
        }
        break;
      case 'Escape':
        this.back();
        break;
      default:
        break;
    }
  }

  protected openQualify(): void {
    this.qualifyMainInterest = '';
    this.qualifyNote = '';
    this.qualifyOpen.set(true);
  }

  protected openLose(): void {
    this.lossReasonId = null;
    this.lossNote = '';
    if (this.lossReasons().length === 0) {
      this.references
        .list('loss-reasons')
        .subscribe({ next: (list) => this.lossReasons.set(list) });
    }
    this.loseOpen.set(true);
  }

  protected openReassign(): void {
    this.reassignTo = this.lead()?.responsibleId ?? null;
    if (this.responsibleOptions().length === 0) {
      this.leads.responsibles().subscribe({ next: (list) => this.responsibleOptions.set(list) });
    }
    this.reassignOpen.set(true);
  }

  protected confirmQualify(): void {
    if (this.qualifyMainInterest.trim().length === 0) {
      return;
    }
    this.act(
      this.leads.qualify(this.leadId, this.qualifyMainInterest.trim(), this.qualifyNote || null),
      'Lead qualificado',
      this.qualifyOpen,
    );
  }

  protected confirmLose(): void {
    if (!this.lossReasonId) {
      return;
    }
    this.act(
      this.leads.lose(this.leadId, this.lossReasonId, this.lossNote || null),
      'Lead marcado como perdido',
      this.loseOpen,
    );
  }

  protected confirmReassign(): void {
    this.act(
      this.leads.reassign(this.leadId, this.reassignTo),
      'Responsável atualizado',
      this.reassignOpen,
    );
  }

  /** Self-claim: a non-manager assigns the lead to themselves (no dialog). */
  protected claim(): void {
    const me = this.auth.userId();
    if (!me) {
      return;
    }
    this.act(this.leads.reassign(this.leadId, me), 'Lead atribuído a você');
  }

  protected openInteraction(): void {
    this.interactionTypeId = null;
    this.interactionResultId = null;
    this.interactionDescription = '';
    this.interactionOccurredAt = new Date();
    this.interactionNextContactAt = null;
    if (this.interactionTypes().length === 0) {
      this.references
        .list('interaction-types')
        .subscribe({ next: (list) => this.interactionTypes.set(list) });
    }
    if (this.interactionResults().length === 0) {
      this.references
        .list('interaction-results')
        .subscribe({ next: (list) => this.interactionResults.set(list) });
    }
    this.interactionOpen.set(true);
  }

  protected canSaveInteraction(): boolean {
    return (
      !!this.interactionTypeId &&
      !!this.interactionResultId &&
      this.interactionDescription.trim().length > 0 &&
      !!this.interactionOccurredAt
    );
  }

  protected confirmInteraction(): void {
    if (!this.canSaveInteraction()) {
      return;
    }
    this.act(
      this.leads.recordInteraction(this.leadId, {
        typeId: this.interactionTypeId!,
        resultId: this.interactionResultId!,
        description: this.interactionDescription.trim(),
        occurredAt: this.interactionOccurredAt.toISOString(),
        nextContactAt: this.interactionNextContactAt
          ? this.interactionNextContactAt.toISOString()
          : null,
      }),
      'Interação registrada',
      this.interactionOpen,
    );
  }

  protected openOpportunity(): void {
    this.oppProductType = '';
    this.oppEstimatedValue = null;
    this.oppExpectedCloseDate = null;
    this.oppResponsibleTo = this.lead()?.responsibleId ?? null;
    this.oppNote = '';
    if (this.responsibleOptions().length === 0) {
      this.leads.responsibles().subscribe({ next: (list) => this.responsibleOptions.set(list) });
    }
    this.opportunityOpen.set(true);
  }

  /** Creates the Opportunity from this qualified lead. Does not change the lead (kept separate). */
  protected confirmOpportunity(): void {
    this.acting.set(true);
    const payload: CreateOpportunity = {
      leadId: this.leadId,
      responsiblePersonId: this.oppResponsibleTo,
      productType: this.oppProductType.trim() || null,
      estimatedValue: this.oppEstimatedValue,
      expectedCloseDate: toIsoDate(this.oppExpectedCloseDate),
      initialNote: this.oppNote.trim() || null,
    };
    this.opportunities.create(payload).subscribe({
      next: () => {
        this.acting.set(false);
        this.opportunityOpen.set(false);
        this.messages.add({ severity: 'success', summary: 'Oportunidade criada' });
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        const body = err.error as { message?: string } | null;
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: body?.message ?? 'Não foi possível criar a oportunidade.',
        });
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.leads.detail(this.leadId).subscribe({
      next: (detail) => {
        this.lead.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver este lead.'
            : err.status === 404
              ? 'Lead não encontrado.'
              : 'Não foi possível carregar o lead.',
        );
      },
    });
  }

  private act(
    action: Observable<LeadDetail>,
    successSummary: string,
    dialog?: { set: (v: boolean) => void },
  ): void {
    this.acting.set(true);
    action.subscribe({
      next: (detail) => {
        this.lead.set(detail);
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

function toIsoDate(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
