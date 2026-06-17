import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import {
  OpportunityIndicators,
  OpportunityService,
  OpportunityStage,
} from '../../../core/api/opportunity.service';

interface Kpi {
  key: string;
  label: string;
  value: number;
  accent: string;
}

interface Bar {
  label: string;
  count: number;
  ratio: number;
}

interface ValueBar {
  label: string;
  value: number;
  ratio: number;
}

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  LOST: 'Perdida',
};

/**
 * Minimum commercial-pipeline indicators: volume KPIs over the selected period (total, lost) plus a
 * current pipeline snapshot (active, ready for proposal, overdue close, active pipeline value), and four
 * breakdowns (by stage, by origin, by responsible, and active value by responsible) with CSS proportion
 * bars — no chart library. The period defaults to month-to-date and re-fetches on change; clearing the
 * dates widens to all-time. Numbers are scoped to the Opportunities the caller can see (managers global,
 * representatives own).
 */
@Component({
  selector: 'app-opportunity-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './opportunity-indicators.html',
  styleUrl: './opportunity-indicators.css',
})
export class OpportunityIndicatorsPage implements OnInit {
  private readonly opportunities = inject(OpportunityService);

  protected createdFrom: Date | null = firstOfMonth();
  protected createdTo: Date | null = new Date();

  protected readonly data = signal<OpportunityIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Total no período', value: d.total, accent: 'total' },
      { key: 'lost', label: 'Perdidas no período', value: d.lost, accent: 'lost' },
    ];
  });

  protected readonly pipelineKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'active', label: 'Ativas', value: d.active, accent: 'active' },
      { key: 'ready', label: 'Prontas p/ proposta', value: d.readyForProposal, accent: 'ready' },
      { key: 'overdue', label: 'Fechamento vencido', value: d.overdueClose, accent: 'overdue' },
    ];
  });

  protected readonly byStage = computed<Bar[]>(() =>
    toBars((this.data()?.byStage ?? []).map((s) => ({ label: STAGE_LABELS[s.stage], count: s.count }))),
  );

  protected readonly byOrigin = computed<Bar[]>(() =>
    toBars((this.data()?.byOrigin ?? []).map((o) => ({ label: o.origin, count: o.count }))),
  );

  protected readonly byResponsible = computed<Bar[]>(() =>
    toBars(
      (this.data()?.byResponsible ?? []).map((r) => ({
        label: r.responsibleName ?? 'Sem responsável',
        count: r.count,
      })),
    ),
  );

  protected readonly valueByResponsible = computed<ValueBar[]>(() =>
    toValueBars(
      (this.data()?.valueByResponsible ?? []).map((r) => ({
        label: r.responsibleName ?? 'Sem responsável',
        value: r.value,
      })),
    ),
  );

  ngOnInit(): void {
    this.reload();
  }

  protected applyPeriod(): void {
    this.reload();
  }

  protected clearPeriod(): void {
    this.createdFrom = null;
    this.createdTo = null;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.opportunities.indicators(toIsoDate(this.createdFrom), toIsoDate(this.createdTo)).subscribe({
      next: (indicators) => {
        this.data.set(indicators);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar os indicadores.');
      },
    });
  }
}

function toBars(rows: { label: string; count: number }[]): Bar[] {
  const max = rows.reduce((m, r) => Math.max(m, r.count), 0);
  return rows.map((r) => ({
    label: r.label,
    count: r.count,
    ratio: max > 0 ? r.count / max : 0,
  }));
}

function toValueBars(rows: { label: string; value: number }[]): ValueBar[] {
  const max = rows.reduce((m, r) => Math.max(m, r.value), 0);
  return rows.map((r) => ({
    label: r.label,
    value: r.value,
    ratio: max > 0 ? r.value / max : 0,
  }));
}

function firstOfMonth(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1);
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
