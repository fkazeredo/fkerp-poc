import { Component, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { Subject, debounceTime } from 'rxjs';
import { Responsible } from '../../../core/api/lead.service';
import {
  BookingNeed,
  CommercialOrderListItem,
  CommercialOrderStatus,
  OrderCommissionStatus,
  OrderFilters,
  OrderService,
} from '../../../core/api/order.service';
import { BookingRequestStatus } from '../../../core/api/booking.service';
import { ReceivableStatus } from '../../../core/api/receivable.service';

const STATUS_LABELS: Record<CommercialOrderStatus, string> = {
  PENDING_BOOKING: 'Pendente de reserva',
  BOOKING_NOT_REQUIRED: 'Reserva não necessária',
  CANCELLED: 'Cancelado',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<CommercialOrderStatus, TagSeverity> = {
  PENDING_BOOKING: 'warn',
  BOOKING_NOT_REQUIRED: 'success',
  CANCELLED: 'danger',
};

const BOOKING_STATUS_LABELS: Record<BookingRequestStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  CONFIRMED: 'Confirmada',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelada',
};

const BOOKING_STATUS_SEVERITY: Record<BookingRequestStatus, TagSeverity> = {
  PENDING: 'warn',
  IN_PROGRESS: 'info',
  PARTIALLY_CONFIRMED: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELLED: 'secondary',
};

const FINANCIAL_STATUS_LABELS: Record<ReceivableStatus, string> = {
  OPEN: 'Em aberto',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
  CANCELLED: 'Cancelada',
};

const FINANCIAL_STATUS_SEVERITY: Record<ReceivableStatus, TagSeverity> = {
  OPEN: 'info',
  PARTIALLY_PAID: 'warn',
  PAID: 'success',
  OVERDUE: 'danger',
  CANCELLED: 'secondary',
};

const COMMISSION_STATUS_LABELS: Record<OrderCommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  PAID: 'Paga',
  ISSUE: 'Problema na comissão',
};

const COMMISSION_STATUS_SEVERITY: Record<OrderCommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  PAID: 'success',
  ISSUE: 'danger',
};

const UNASSIGNED = 'unassigned';

/** Formats the sequential order number as the human-friendly code PC-000n. */
export function orderCode(n: number): string {
  return 'PC-' + String(n).padStart(4, '0');
}

/**
 * Operational Commercial Order list — the Vendas module's order landing. A paginated table of the Orders the
 * user may see, with status, responsible, creation period, amount and booking-need filters plus a search over
 * the source Proposal title. Cancelled Orders are hidden unless their status is explicitly chosen, and no
 * filter can surface an Order the caller may not see (visibility is applied at the query level). Exposes
 * commercial-order data only — never Booking, Receivable, Payment or Commission data.
 */
@Component({
  selector: 'app-order-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    MultiSelectModule,
    DatePickerModule,
    TagModule,
    MessageModule,
  ],
  templateUrl: './order-list.html',
  styleUrl: './order-list.css',
})
export class OrderList implements OnInit {
  private readonly orders = inject(OrderService);

  protected readonly statusOptions = (
    ['PENDING_BOOKING', 'BOOKING_NOT_REQUIRED', 'CANCELLED'] as CommercialOrderStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly bookingNeedOptions: { value: BookingNeed; label: string }[] = [
    { value: 'REQUIRED', label: 'Exige reserva' },
    { value: 'NOT_REQUIRED', label: 'Não exige' },
  ];
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected status: CommercialOrderStatus[] = [];
  protected responsible: string | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected totalMin: number | null = null;
  protected totalMax: number | null = null;
  protected bookingNeed: BookingNeed | null = null;
  protected search = '';

  protected readonly items = signal<CommercialOrderListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  private readonly searchInput = new Subject<void>();

  constructor() {
    this.searchInput.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.applyFilters());
  }

  ngOnInit(): void {
    this.orders.responsibles().subscribe({
      next: (list) => this.responsibleOptions.set([{ id: UNASSIGNED, name: 'Sem responsável' }, ...list]),
    });
  }

  protected orderCode(n: number): string {
    return orderCode(n);
  }

  protected statusLabel(status: CommercialOrderStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: CommercialOrderStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected bookingStatusLabel(status: BookingRequestStatus): string {
    return BOOKING_STATUS_LABELS[status];
  }

  protected bookingStatusSeverity(status: BookingRequestStatus): TagSeverity {
    return BOOKING_STATUS_SEVERITY[status];
  }

  protected financialStatusLabel(status: ReceivableStatus): string {
    return FINANCIAL_STATUS_LABELS[status];
  }

  protected financialStatusSeverity(status: ReceivableStatus): TagSeverity {
    return FINANCIAL_STATUS_SEVERITY[status];
  }

  protected commissionStatusLabel(status: OrderCommissionStatus): string {
    return COMMISSION_STATUS_LABELS[status];
  }

  protected commissionStatusSeverity(status: OrderCommissionStatus): TagSeverity {
    return COMMISSION_STATUS_SEVERITY[status];
  }

  /** Single loader: fires on first render and on pagination (lazy table). */
  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.firstRow = event.first ?? 0;
    this.reload();
  }

  protected onSearchInput(): void {
    this.searchInput.next();
  }

  protected applyFilters(): void {
    this.firstRow = 0;
    this.reload();
  }

  protected clearFilters(): void {
    this.status = [];
    this.responsible = null;
    this.createdFrom = null;
    this.createdTo = null;
    this.totalMin = null;
    this.totalMax = null;
    this.bookingNeed = null;
    this.search = '';
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: OrderFilters = {
      status: this.status,
      responsible: this.responsible,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      totalMin: this.totalMin,
      totalMax: this.totalMax,
      bookingNeed: this.bookingNeed,
      q: this.search,
    };
    this.orders.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar os pedidos.');
      },
    });
  }
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
