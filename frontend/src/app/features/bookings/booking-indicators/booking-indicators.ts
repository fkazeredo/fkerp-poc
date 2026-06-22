import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import {
  BookingIndicators,
  BookingRequestStatus,
  BookingService,
} from '../../../core/api/booking.service';
import { ProposalItemType } from '../../../core/api/proposal.service';

interface Kpi {
  key: string;
  label: string;
  value?: number;
  text?: string;
  accent: string;
}

interface Bar {
  label: string;
  count: number;
  ratio: number;
}

const STATUS_LABELS: Record<BookingRequestStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  CONFIRMED: 'Confirmada',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelada',
};

const ITEM_TYPE_LABELS: Record<ProposalItemType, string> = {
  TRAVEL_PACKAGE: 'Pacote de viagem',
  CAR_RENTAL: 'Locação de veículo',
  SERVICE_FEE: 'Taxa de serviço',
  OTHER: 'Outro',
};

/**
 * Minimum Booking Operations indicators: volume KPIs over the selected period (total requests, failed items)
 * plus a current operational snapshot (requests ready for Financial Operations, and the average creation→
 * confirmation time), with by-status and by-item-type breakdowns as CSS proportion bars — no chart library. The
 * period defaults to month-to-date and re-fetches on change; clearing the dates widens to all-time. Numbers are
 * scoped to the requests the caller can see. Exposes operational reservation figures only — never Financial,
 * Payment or Commission data.
 */
@Component({
  selector: 'app-booking-indicators',
  imports: [FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './booking-indicators.html',
  styleUrl: './booking-indicators.css',
})
export class BookingIndicatorsPage implements OnInit {
  private readonly bookings = inject(BookingService);

  protected createdFrom: Date | null = firstOfMonth();
  protected createdTo: Date | null = new Date();

  protected readonly data = signal<BookingIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Total no período', value: d.total, accent: 'total' },
      { key: 'failed', label: 'Itens com falha', value: d.failedItems, accent: 'failed' },
    ];
  });

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'ready', label: 'Prontas p/ Financeiro', value: d.readyForFinance, accent: 'ready' },
      {
        key: 'avg',
        label: 'Tempo médio até confirmação',
        text: formatDuration(d.avgConfirmationSeconds),
        accent: 'avg',
      },
    ];
  });

  protected readonly byStatus = computed<Bar[]>(() =>
    toBars((this.data()?.byStatus ?? []).map((s) => ({ label: STATUS_LABELS[s.status], count: s.count }))),
  );

  protected readonly byItemType = computed<Bar[]>(() =>
    toBars((this.data()?.itemsByType ?? []).map((t) => ({ label: ITEM_TYPE_LABELS[t.type], count: t.count }))),
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
    this.bookings.indicators(toIsoDate(this.createdFrom), toIsoDate(this.createdTo)).subscribe({
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

/** Formats an average duration in seconds into a short pt-BR label, or "—" when there is no data. */
export function formatDuration(seconds: number | null): string {
  if (seconds == null) {
    return '—';
  }
  const hours = seconds / 3600;
  if (hours >= 24) {
    return `${(hours / 24).toFixed(1)} dias`;
  }
  if (hours >= 1) {
    return `${hours.toFixed(1)} h`;
  }
  return `${Math.round(seconds / 60)} min`;
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
