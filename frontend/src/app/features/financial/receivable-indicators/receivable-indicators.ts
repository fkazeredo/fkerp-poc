import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { ReceivableIndicators, ReceivableService } from '../../../core/api/receivable.service';

interface Kpi {
  key: string;
  label: string;
  value?: number;
  money?: number;
  accent: string;
}

interface MethodBar {
  label: string;
  count: number;
  amount: number;
  ratio: number;
}

/**
 * Operational Financial Operations indicators — a received-payments & collection view. A current snapshot
 * (Receivables open / partially paid / overdue and the outstanding total) plus the volume in the selected period
 * by payment date (received amount, payments registered, receivables settled) with a "received by method"
 * breakdown as CSS proportion bars (no chart library). The period defaults to month-to-date and re-fetches on
 * change; clearing the dates widens to all-time. Numbers are scoped to the Receivables the caller can see. This
 * is operational, not executive reporting — never Commission, Accounts Payable or bank-reconciliation data.
 */
@Component({
  selector: 'app-receivable-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './receivable-indicators.html',
  styleUrl: './receivable-indicators.css',
})
export class ReceivableIndicatorsPage implements OnInit {
  private readonly receivables = inject(ReceivableService);

  protected paidFrom: Date | null = firstOfMonth();
  protected paidTo: Date | null = new Date();

  protected readonly data = signal<ReceivableIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'open', label: 'Em aberto', value: d.openCount, accent: 'open' },
      { key: 'partial', label: 'Parcialmente pagas', value: d.partiallyPaidCount, accent: 'partial' },
      { key: 'overdue', label: 'Vencidas', value: d.overdueCount, accent: 'overdue' },
      { key: 'outstanding', label: 'Em aberto (R$)', money: d.outstandingAmount, accent: 'outstanding' },
    ];
  });

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'received', label: 'Recebido no período', money: d.receivedAmount, accent: 'received' },
      { key: 'payments', label: 'Pagamentos registrados', value: d.paymentsRegistered, accent: 'payments' },
      { key: 'paid', label: 'Contas quitadas no período', value: d.paidReceivablesInPeriod, accent: 'paid' },
    ];
  });

  protected readonly byMethod = computed<MethodBar[]>(() => {
    const rows = this.data()?.paymentsByMethod ?? [];
    const max = rows.reduce((m, r) => Math.max(m, r.amount), 0);
    return rows.map((r) => ({
      label: r.methodLabel,
      count: r.count,
      amount: r.amount,
      ratio: max > 0 ? r.amount / max : 0,
    }));
  });

  ngOnInit(): void {
    this.reload();
  }

  protected applyPeriod(): void {
    this.reload();
  }

  protected clearPeriod(): void {
    this.paidFrom = null;
    this.paidTo = null;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.receivables.indicators(toIsoDate(this.paidFrom), toIsoDate(this.paidTo)).subscribe({
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
