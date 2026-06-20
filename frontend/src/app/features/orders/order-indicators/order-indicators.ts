import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { OrderIndicators, OrderService } from '../../../core/api/order.service';

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

/**
 * Minimum Commercial Order indicators: volume KPIs over the selected period (total, total amount) plus a
 * current operational snapshot (orders pending booking), and a by-responsible breakdown with CSS proportion
 * bars — no chart library. The period defaults to month-to-date and re-fetches on change; clearing the
 * dates widens to all-time. Numbers are scoped to the Orders the caller can see (managers global,
 * representatives own). Exposes commercial-order figures only — never Booking, Receivable, Payment or
 * Commission data.
 */
@Component({
  selector: 'app-order-indicators',
  imports: [CurrencyPipe, FormsModule, ButtonModule, DatePickerModule, MessageModule],
  templateUrl: './order-indicators.html',
  styleUrl: './order-indicators.css',
})
export class OrderIndicatorsPage implements OnInit {
  private readonly orders = inject(OrderService);

  protected createdFrom: Date | null = firstOfMonth();
  protected createdTo: Date | null = new Date();

  protected readonly data = signal<OrderIndicators | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly periodKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [
      { key: 'total', label: 'Total no período', value: d.total, accent: 'total' },
      { key: 'amount', label: 'Valor total', value: d.totalAmount, accent: 'amount', currency: true },
    ];
  });

  protected readonly snapshotKpis = computed<Kpi[]>(() => {
    const d = this.data();
    if (!d) {
      return [];
    }
    return [{ key: 'pending', label: 'Pendentes de reserva', value: d.pendingBooking, accent: 'pending' }];
  });

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
    this.orders.indicators(toIsoDate(this.createdFrom), toIsoDate(this.createdTo)).subscribe({
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
