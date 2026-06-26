import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import {
  CommissionIndicators,
  CommissionService,
  CommissionStatus,
} from '../../../core/api/commission.service';

interface Kpi {
  key: string;
  label: string;
  value: number;
  accent: string;
  currency?: boolean;
}

interface StatusBar {
  status: CommissionStatus;
  label: string;
  count: number;
  amount: number;
  ratio: number;
}

interface BeneficiaryBar {
  label: string;
  count: number;
  amount: number;
  ratio: number;
}

const STATUS_LABELS: Record<CommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  PAID: 'Paga',
  CANCELLED: 'Cancelada',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<CommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
  PAID: 'success',
  CANCELLED: 'secondary',
};

/**
 * Minimum Commission Management indicators: a current snapshot (amount + count pending approval and pending payment,
 * the by-status and by-beneficiary breakdowns) plus the volume in the selected period (commissions paid in the
 * period) and two snapshot health averages (eligibility→approval and approval→payment latency). The period defaults
 * to month-to-date and re-fetches on change; clearing widens to all-time. Numbers are scoped to the commissions the
 * caller can see (managers/finance global, beneficiaries own). Commission figures only — never payroll, tax,
 * accounting or bank data.
 */
@Component({
  selector: 'app-commission-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule, TagModule],
  templateUrl: './commission-indicators.html',
  styleUrl: './commission-indicators.css',
})
export class CommissionIndicatorsPage implements OnInit {
  private readonly commissions = inject(CommissionService);

  protected from: Date | null = firstOfMonth();
  protected to: Date | null = new Date();

  protected readonly data = signal<CommissionIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'paidCount', label: 'Pagas no período', value: d.paidInPeriodCount, accent: 'paid' },
      { key: 'paidAmount', label: 'Valor pago no período', value: d.paidInPeriodAmount, accent: 'paid', currency: true },
    ];
  });

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      {
        key: 'pendingApprovalAmount',
        label: 'Pendente de aprovação',
        value: d.pendingApprovalAmount,
        accent: 'eligible',
        currency: true,
      },
      {
        key: 'pendingPaymentAmount',
        label: 'Pendente de pagamento',
        value: d.pendingPaymentAmount,
        accent: 'approved',
        currency: true,
      },
    ];
  });

  protected readonly byStatus = computed<StatusBar[]>(() => {
    const rows = this.data()?.byStatus ?? [];
    const max = rows.reduce((m, r) => Math.max(m, r.count), 0);
    return rows.map((r) => ({
      status: r.status,
      label: STATUS_LABELS[r.status],
      count: r.count,
      amount: r.totalAmount,
      ratio: max > 0 ? r.count / max : 0,
    }));
  });

  protected readonly byBeneficiary = computed<BeneficiaryBar[]>(() => {
    const rows = this.data()?.byBeneficiary ?? [];
    const max = rows.reduce((m, r) => Math.max(m, r.count), 0);
    return rows.map((r) => ({
      label: r.beneficiaryName ?? '—',
      count: r.count,
      amount: r.totalAmount,
      ratio: max > 0 ? r.count / max : 0,
    }));
  });

  /** Human label for the eligibility→approval average latency (or "—" when no commission was approved yet). */
  protected readonly avgEligibilityToApproval = computed(() =>
    humanDuration(this.data()?.avgEligibilityToApprovalSeconds ?? null),
  );

  /** Human label for the approval→payment average latency (or "—" when no commission was paid yet). */
  protected readonly avgApprovalToPayment = computed(() =>
    humanDuration(this.data()?.avgApprovalToPaymentSeconds ?? null),
  );

  protected statusSeverity(status: CommissionStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

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
    this.commissions.indicators(toIsoDate(this.from), toIsoDate(this.to)).subscribe({
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

/** Formats a duration in seconds as a short human label (e.g. "2 d 3 h", "5 min", "imediato"); null → "—". */
function humanDuration(seconds: number | null): string {
  if (seconds === null || seconds === undefined) {
    return '—';
  }
  if (seconds < 60) {
    return seconds <= 0 ? 'imediato' : `${seconds} s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    const remMin = minutes % 60;
    return remMin > 0 ? `${hours} h ${remMin} min` : `${hours} h`;
  }
  const days = Math.floor(hours / 24);
  const remHours = hours % 24;
  return remHours > 0 ? `${days} d ${remHours} h` : `${days} d`;
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
