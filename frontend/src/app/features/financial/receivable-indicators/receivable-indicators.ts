import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import {
  ReceivableIndicators,
  ReceivableService,
  ReceivableStatus,
} from '../../../core/api/receivable.service';

interface Kpi {
  key: string;
  label: string;
  value?: number;
  money?: number;
  text?: string;
  accent: string;
}

interface Bar {
  label: string;
  count: number;
  ratio: number;
}

interface MethodBar {
  label: string;
  count: number;
  amount: number;
  ratio: number;
}

const STATUS_LABELS: Record<ReceivableStatus, string> = {
  OPEN: 'Em aberto',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
  CANCELLED: 'Cancelada',
};

/**
 * Minimum functional Financial Operations indicators — a manager's minimal view of receivables and received
 * payments: the volume in the selected period (receivables created, amount to receive, amount received, payments
 * registered, receivables settled and the average days to payment) plus a current snapshot (receivables by status,
 * outstanding and overdue amounts, and the receivables ready for Commission Management), with by-status and
 * by-method breakdowns as CSS proportion bars (no chart library). The period defaults to month-to-date and
 * re-fetches on change; clearing the dates widens to all-time. Numbers are scoped to the Receivables the caller
 * can see. Operational, not executive reporting — never Commission calculation, Accounts Payable or
 * bank-reconciliation data.
 */
@Component({
  selector: 'app-receivable-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './receivable-indicators.html',
  styleUrl: './receivable-indicators.css',
})
export class ReceivableIndicatorsPage implements OnInit {
  private readonly receivables = inject(ReceivableService);

  protected from: Date | null = firstOfMonth();
  protected to: Date | null = new Date();

  protected readonly data = signal<ReceivableIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Contas no período', value: d.totalReceivablesInPeriod, accent: 'total' },
      { key: 'toReceive', label: 'A receber (período)', money: d.totalToReceive, accent: 'toreceive' },
      { key: 'received', label: 'Recebido no período', money: d.receivedAmount, accent: 'received' },
      { key: 'payments', label: 'Pagamentos registrados', value: d.paymentsRegistered, accent: 'payments' },
      { key: 'settled', label: 'Contas quitadas', value: d.paidReceivablesInPeriod, accent: 'settled' },
      { key: 'avg', label: 'Tempo médio até pagamento', text: formatDays(d.avgDaysToPayment), accent: 'avg' },
    ];
  });

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'outstanding', label: 'Em aberto (R$)', money: d.outstandingAmount, accent: 'outstanding' },
      { key: 'overdue', label: 'Vencido (R$)', money: d.overdueAmount, accent: 'overdue' },
      { key: 'commission', label: 'Prontas p/ comissão', value: d.readyForCommission, accent: 'commission' },
    ];
  });

  protected readonly byStatus = computed<Bar[]>(() =>
    toBars((this.data()?.byStatus ?? []).map((s) => ({ label: STATUS_LABELS[s.status], count: s.count }))),
  );

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
    this.from = null;
    this.to = null;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.receivables.indicators(toIsoDate(this.from), toIsoDate(this.to)).subscribe({
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

/** Formats the average days-to-payment, or "—" when there is no settled data in the period. */
export function formatDays(days: number | null): string {
  if (days == null) {
    return '—';
  }
  return `${days} ${Math.abs(days) === 1 ? 'dia' : 'dias'}`;
}

function toBars(rows: { label: string; count: number }[]): Bar[] {
  const max = rows.reduce((m, r) => Math.max(m, r.count), 0);
  return rows.map((r) => ({ label: r.label, count: r.count, ratio: max > 0 ? r.count / max : 0 }));
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
