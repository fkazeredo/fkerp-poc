import { Component, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../../core/auth/auth.service';
import { Responsible } from '../../../core/api/lead.service';
import {
  ReceivableFilters,
  ReceivableListItem,
  ReceivableService,
  ReceivableStatus,
} from '../../../core/api/receivable.service';

const STATUS_LABELS: Record<ReceivableStatus, string> = {
  OPEN: 'Em aberto',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
  CANCELLED: 'Cancelada',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<ReceivableStatus, TagSeverity> = {
  OPEN: 'info',
  PARTIALLY_PAID: 'warn',
  PAID: 'success',
  OVERDUE: 'danger',
  CANCELLED: 'secondary',
};

/** Formats the source Order's sequential number as the human-friendly code PC-000n. */
export function orderCode(n: number): string {
  return 'PC-' + String(n).padStart(4, '0');
}

/**
 * Operational Receivable list — the Financeiro module's landing (Contas a receber): the receivables that require
 * financial follow-up, so a financial user can prioritize collection. A paginated table with status, payer,
 * source order, due/creation period, responsibles, amount-range and overdue-only filters. The settled PAID and
 * CANCELLED receivables are hidden unless their status is explicitly chosen; overdue ones stay visible as
 * operational problems. No filter can surface a receivable the caller may not see (visibility is applied at the
 * query level). Exposes receivable + commercial-origin data only — never Commission or bank-reconciliation data.
 */
@Component({
  selector: 'app-receivable-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    TableModule,
    ButtonModule,
    MultiSelectModule,
    SelectModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    ToggleSwitchModule,
    TagModule,
    MessageModule,
  ],
  templateUrl: './receivable-list.html',
  styleUrl: './receivable-list.css',
})
export class ReceivableList implements OnInit {
  private readonly receivables = inject(ReceivableService);
  private readonly auth = inject(AuthService);

  protected readonly statusOptions = (
    ['OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'] as ReceivableStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected status: ReceivableStatus[] = [];
  protected payer: string | null = null;
  protected orderNumber: number | null = null;
  protected dueFrom: Date | null = null;
  protected dueTo: Date | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected commercialResponsible: string | null = null;
  protected financialResponsible: string | null = null;
  protected amountMin: number | null = null;
  protected amountMax: number | null = null;
  protected overdueOnly = false;

  protected readonly items = signal<ReceivableListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  protected readonly canCreate = this.auth.canCreateReceivable();

  ngOnInit(): void {
    this.receivables.responsibles().subscribe({
      next: (list) => this.responsibleOptions.set(list),
    });
  }

  protected orderCode(n: number): string {
    return orderCode(n);
  }

  protected statusLabel(status: ReceivableStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ReceivableStatus): TagSeverity {
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
    this.payer = null;
    this.orderNumber = null;
    this.dueFrom = null;
    this.dueTo = null;
    this.createdFrom = null;
    this.createdTo = null;
    this.commercialResponsible = null;
    this.financialResponsible = null;
    this.amountMin = null;
    this.amountMax = null;
    this.overdueOnly = false;
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: ReceivableFilters = {
      status: this.status,
      payer: this.payer,
      orderNumber: this.orderNumber,
      dueFrom: toIsoDate(this.dueFrom),
      dueTo: toIsoDate(this.dueTo),
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      commercialResponsible: this.commercialResponsible,
      financialResponsible: this.financialResponsible,
      amountMin: this.amountMin,
      amountMax: this.amountMax,
      overdueOnly: this.overdueOnly,
    };
    this.receivables.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as contas a receber.');
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
