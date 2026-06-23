import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { MultiSelectModule } from 'primeng/multiselect';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../../core/auth/auth.service';
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
 * Operational Receivable list — the Financeiro module's landing (Contas a receber). A paginated table of the
 * receivables the user may see, filterable by status. The CANCELLED receivables are hidden unless their status
 * is explicitly chosen, and no filter can surface a receivable the caller may not see (visibility is applied at
 * the query level). Exposes receivable + commercial-origin data only — never Payment, Commission or Invoice
 * data. The row links to the receivable detail; the primary action creates one from a confirmed Order.
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
    TagModule,
    MessageModule,
  ],
  templateUrl: './receivable-list.html',
  styleUrl: './receivable-list.css',
})
export class ReceivableList {
  private readonly receivables = inject(ReceivableService);
  private readonly auth = inject(AuthService);

  protected readonly statusOptions = (
    ['OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'] as ReceivableStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));

  protected status: ReceivableStatus[] = [];

  protected readonly items = signal<ReceivableListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  protected readonly canCreate = this.auth.canCreateReceivable();

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
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: ReceivableFilters = { status: this.status };
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
