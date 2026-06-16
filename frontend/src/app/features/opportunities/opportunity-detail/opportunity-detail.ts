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
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import {
  OpportunityDetail,
  OpportunityService,
  OpportunityStage,
} from '../../../core/api/opportunity.service';
import { ReferenceItem, ReferenceService } from '../../../core/api/reference.service';
import { AuthService } from '../../../core/auth/auth.service';

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  LOST: 'Perdida',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/**
 * Opportunity detail page (consultation): commercial summary, the traceable source Lead, and the loss
 * outcome when LOST. The only action is "mark as lost" (scope crm:opportunity:update). Activity and
 * stage-movement history are reserved for future slices (empty for now). The detail never shows
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
    MessageModule,
  ],
  templateUrl: './opportunity-detail.html',
  styleUrl: './opportunity-detail.css',
})
export class OpportunityDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly opportunities = inject(OpportunityService);
  private readonly references = inject(ReferenceService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);

  protected readonly opportunity = signal<OpportunityDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly loseOpen = signal(false);
  protected readonly acting = signal(false);
  protected readonly lossReasons = signal<ReferenceItem[]>([]);
  protected lossReasonId: string | null = null;
  protected lossNote = '';

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

  protected back(): void {
    this.router.navigateByUrl('/oportunidades');
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

  protected confirmLose(): void {
    if (!this.lossReasonId) {
      return;
    }
    this.acting.set(true);
    this.opportunities.lose(this.opportunityId, this.lossReasonId, this.lossNote || null).subscribe({
      next: (detail) => {
        this.opportunity.set(detail);
        this.acting.set(false);
        this.loseOpen.set(false);
        this.messages.add({ severity: 'success', summary: 'Oportunidade marcada como perdida' });
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

  /** Contextual shortcuts on the detail screen: p lose, Esc back. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.loseOpen()) {
      return;
    }
    if (event.key === 'p' && this.canLose()) {
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
}
