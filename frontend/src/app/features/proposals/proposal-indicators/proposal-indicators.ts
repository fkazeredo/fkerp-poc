import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import {
  ProposalIndicators,
  ProposalService,
  ProposalStatus,
} from '../../../core/api/proposal.service';

interface Kpi {
  key: string;
  label: string;
  value: number;
  accent: string;
  currency?: boolean;
}

interface Bar {
  label: string;
  count: number;
  ratio: number;
}

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

/**
 * Minimum Proposal-flow indicators: volume KPIs over the selected period (total, proposed amount, accepted
 * amount, rejected count) plus a current operational snapshot (waiting for review, waiting for customer
 * decision), and two breakdowns (by status, by responsible) with CSS proportion bars — no chart library.
 * The period defaults to month-to-date and re-fetches on change; clearing the dates widens to all-time.
 * Numbers are scoped to the Proposals the caller can see (managers global, representatives own). Exposes
 * commercial-offer figures only — never Sale, Order, Booking, Financial or Commission data.
 */
@Component({
  selector: 'app-proposal-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './proposal-indicators.html',
  styleUrl: './proposal-indicators.css',
})
export class ProposalIndicatorsPage implements OnInit {
  private readonly proposals = inject(ProposalService);

  protected createdFrom: Date | null = firstOfMonth();
  protected createdTo: Date | null = new Date();

  protected readonly data = signal<ProposalIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Total no período', value: d.total, accent: 'total' },
      { key: 'proposed', label: 'Valor proposto', value: d.proposedAmount, accent: 'proposed', currency: true },
      { key: 'accepted', label: 'Valor aceito', value: d.acceptedAmount, accent: 'accepted', currency: true },
      { key: 'rejected', label: 'Recusadas', value: d.rejectedCount, accent: 'rejected' },
    ];
  });

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'review', label: 'Aguardando revisão', value: d.waitingForReview, accent: 'review' },
      { key: 'decision', label: 'Aguardando cliente', value: d.waitingForCustomerDecision, accent: 'decision' },
    ];
  });

  protected readonly byStatus = computed<Bar[]>(() =>
    toBars((this.data()?.byStatus ?? []).map((s) => ({ label: STATUS_LABELS[s.status], count: s.count }))),
  );

  protected readonly byResponsible = computed<Bar[]>(() =>
    toBars(
      (this.data()?.byResponsible ?? []).map((r) => ({
        label: r.responsibleName ?? 'Sem responsável',
        count: r.count,
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
    this.proposals.indicators(toIsoDate(this.createdFrom), toIsoDate(this.createdTo)).subscribe({
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
