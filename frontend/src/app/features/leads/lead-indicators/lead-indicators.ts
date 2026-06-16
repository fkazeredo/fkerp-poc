import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { LeadIndicators, LeadService } from '../../../core/api/lead.service';

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

/**
 * Minimum top-of-funnel Lead indicators: KPI cards (total + by status, Lost included, plus waiting
 * for first contact) and two breakdowns (by origin, by responsible) with CSS proportion bars — no
 * chart library. The period defaults to month-to-date and re-fetches on change; clearing the dates
 * widens to all-time. Numbers are scoped to the Leads the caller can see (managers global, reps own).
 */
@Component({
  selector: 'app-lead-indicators',
  imports: [FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './lead-indicators.html',
  styleUrl: './lead-indicators.css',
})
export class LeadIndicatorsPage implements OnInit {
  private readonly leads = inject(LeadService);

  protected createdFrom: Date | null = firstOfMonth();
  protected createdTo: Date | null = new Date();

  protected readonly data = signal<LeadIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly kpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Total', value: d.total, accent: 'total' },
      { key: 'new', label: 'Novos', value: d.newLeads, accent: 'new' },
      { key: 'contacted', label: 'Em contato', value: d.contacted, accent: 'contacted' },
      { key: 'qualified', label: 'Qualificados', value: d.qualified, accent: 'qualified' },
      { key: 'lost', label: 'Perdidos', value: d.lost, accent: 'lost' },
      {
        key: 'waiting',
        label: 'Aguardando 1º contato',
        value: d.waitingFirstContact,
        accent: 'waiting',
      },
    ];
  });

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

  protected readonly isEmpty = computed(() => {
    const d = this.data();
    return !!d && d.total === 0;
  });

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
    this.leads.indicators(toIsoDate(this.createdFrom), toIsoDate(this.createdTo)).subscribe({
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
