import { Component, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import {
  OpportunityPendingReason,
  OpportunityService,
  OpportunityStage,
  PendingOpportunity,
} from '../../../core/api/opportunity.service';

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  WON: 'Ganha',
  LOST: 'Perdida',
};

const REASON_LABELS: Record<OpportunityPendingReason, string> = {
  WITHOUT_RECENT_ACTIVITY: 'Sem atividade recente',
  OVERDUE_NEXT_ACTION: 'Próxima ação atrasada',
  STUCK_IN_NEW: 'Parada em Nova',
  STUCK_IN_DISCOVERY: 'Parada em Descoberta',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  EXPECTED_CLOSE_OVERDUE: 'Fechamento vencido',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const REASON_SEVERITY: Record<OpportunityPendingReason, TagSeverity> = {
  WITHOUT_RECENT_ACTIVITY: 'warn',
  OVERDUE_NEXT_ACTION: 'danger',
  STUCK_IN_NEW: 'info',
  STUCK_IN_DISCOVERY: 'secondary',
  READY_FOR_PROPOSAL: 'success',
  EXPECTED_CLOSE_OVERDUE: 'danger',
};

/**
 * Operational pending-items worklist: Opportunities that need action, tagged with the reasons why, so a
 * negotiation does not stall silently. Read-only; visibility is enforced by the backend.
 */
@Component({
  selector: 'app-opportunity-pending',
  imports: [CurrencyPipe, DatePipe, RouterLink, TableModule, TagModule, MessageModule],
  templateUrl: './opportunity-pending.html',
  styleUrl: './opportunity-pending.css',
})
export class OpportunityPending implements OnInit {
  private readonly opportunities = inject(OpportunityService);

  protected readonly items = signal<PendingOpportunity[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  ngOnInit(): void {
    // The lazy table fires the first load.
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

  protected reasonLabel(reason: OpportunityPendingReason): string {
    return REASON_LABELS[reason];
  }

  protected reasonSeverity(reason: OpportunityPendingReason): TagSeverity {
    return REASON_SEVERITY[reason];
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.firstRow = event.first ?? 0;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.opportunities.pending(this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as pendências.');
      },
    });
  }
}
