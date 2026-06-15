import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { Subject, debounceTime } from 'rxjs';
import {
  LeadFilters,
  LeadListItem,
  LeadService,
  LeadStatus,
  Origin,
  Responsible,
} from '../../../core/api/lead.service';

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Novo',
  CONTACTED: 'Em contato',
  QUALIFIED: 'Qualificado',
  LOST: 'Perdido',
};

const UNASSIGNED = 'unassigned';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/** Operational Lead list: paginated table with status/origin/responsible/period filters + search. */
@Component({
  selector: 'app-lead-list',
  imports: [
    DatePipe,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MultiSelectModule,
    DatePickerModule,
    TagModule,
    MessageModule,
  ],
  templateUrl: './lead-list.html',
  styleUrl: './lead-list.css',
})
export class LeadList implements OnInit {
  private readonly leads = inject(LeadService);
  private readonly router = inject(Router);

  protected readonly statusOptions = (
    ['NEW', 'CONTACTED', 'QUALIFIED', 'LOST'] as LeadStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly origins = signal<Origin[]>([]);
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected status: LeadStatus[] = [];
  protected originId: string | null = null;
  protected responsible: string | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected search = '';

  protected readonly items = signal<LeadListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  private readonly searchInput = new Subject<void>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe(() => this.applyFilters());
  }

  ngOnInit(): void {
    this.leads.origins().subscribe({ next: (list) => this.origins.set(list) });
    this.leads.responsibles().subscribe({
      next: (list) =>
        this.responsibleOptions.set([{ id: UNASSIGNED, name: 'Sem responsável' }, ...list]),
    });
  }

  protected statusLabel(status: LeadStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: LeadStatus): TagSeverity {
    switch (status) {
      case 'NEW':
        return 'info';
      case 'CONTACTED':
        return 'warn';
      case 'QUALIFIED':
        return 'success';
      case 'LOST':
        return 'danger';
      default:
        return 'secondary';
    }
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
    this.originId = null;
    this.responsible = null;
    this.createdFrom = null;
    this.createdTo = null;
    this.search = '';
    this.applyFilters();
  }

  protected goNew(): void {
    this.router.navigateByUrl('/leads/new');
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: LeadFilters = {
      status: this.status,
      originId: this.originId,
      responsible: this.responsible,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      q: this.search,
    };
    this.leads.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar os leads.');
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
