import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { Responsible } from '../../../core/api/lead.service';
import {
  BookingFilters,
  BookingRequestListItem,
  BookingRequestStatus,
  BookingService,
} from '../../../core/api/booking.service';
import { ProposalItemType } from '../../../core/api/proposal.service';

const STATUS_LABELS: Record<BookingRequestStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  PARTIALLY_CONFIRMED: 'Parcialmente confirmada',
  CONFIRMED: 'Confirmada',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelada',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<BookingRequestStatus, TagSeverity> = {
  PENDING: 'warn',
  IN_PROGRESS: 'info',
  PARTIALLY_CONFIRMED: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELLED: 'secondary',
};

const ITEM_TYPE_LABELS: Record<ProposalItemType, string> = {
  TRAVEL_PACKAGE: 'Pacote de viagem',
  CAR_RENTAL: 'Locação de carro',
  SERVICE_FEE: 'Taxa de serviço',
  OTHER: 'Outro',
};

const UNASSIGNED = 'unassigned';

/** Formats the source Order's sequential number as the human-friendly code PC-000n (the reservation id). */
export function orderCode(n: number): string {
  return 'PC-' + String(n).padStart(4, '0');
}

/**
 * Operational Booking Request list — the Reservas module's landing. A paginated table of the reservations the
 * user may see, with status, operator, commercial responsible, item type, has-failed and creation-period
 * filters. The terminal CONFIRMED and CANCELLED requests are hidden unless their status is explicitly chosen
 * (FAILED stays visible), and no filter can surface a reservation the caller may not see (visibility is
 * applied at the query level). Exposes operational reservation data only — never Financial, Payment or
 * Commission data. The row links to its source Commercial Order (the booking detail is a later slice).
 */
@Component({
  selector: 'app-booking-list',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TableModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    DatePickerModule,
    ToggleSwitchModule,
    TagModule,
    MessageModule,
  ],
  templateUrl: './booking-list.html',
  styleUrl: './booking-list.css',
})
export class BookingList implements OnInit {
  private readonly bookings = inject(BookingService);

  protected readonly statusOptions = (
    [
      'PENDING',
      'IN_PROGRESS',
      'PARTIALLY_CONFIRMED',
      'CONFIRMED',
      'FAILED',
      'CANCELLED',
    ] as BookingRequestStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly itemTypeOptions = (
    ['TRAVEL_PACKAGE', 'CAR_RENTAL', 'SERVICE_FEE', 'OTHER'] as ProposalItemType[]
  ).map((value) => ({ value, label: ITEM_TYPE_LABELS[value] }));
  protected readonly operatorOptions = signal<Responsible[]>([]);
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected status: BookingRequestStatus[] = [];
  protected operator: string | null = null;
  protected responsible: string | null = null;
  protected itemType: ProposalItemType | null = null;
  protected hasFailedItems = false;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;

  protected readonly items = signal<BookingRequestListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  ngOnInit(): void {
    this.bookings.responsibles().subscribe({
      next: (list) => {
        this.responsibleOptions.set(list);
        this.operatorOptions.set([{ id: UNASSIGNED, name: 'Sem operador' }, ...list]);
      },
    });
  }

  protected orderCode(n: number): string {
    return orderCode(n);
  }

  protected statusLabel(status: BookingRequestStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: BookingRequestStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  /** Single loader: fires on first render and on pagination (lazy table). */
  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.firstRow = event.first ?? 0;
    this.reload();
  }

  protected applyFilters(): void {
    this.firstRow = 0;
    this.reload();
  }

  protected clearFilters(): void {
    this.status = [];
    this.operator = null;
    this.responsible = null;
    this.itemType = null;
    this.hasFailedItems = false;
    this.createdFrom = null;
    this.createdTo = null;
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: BookingFilters = {
      status: this.status,
      operator: this.operator,
      responsible: this.responsible,
      itemType: this.itemType,
      hasFailedItems: this.hasFailedItems,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
    };
    this.bookings.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as reservas.');
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
