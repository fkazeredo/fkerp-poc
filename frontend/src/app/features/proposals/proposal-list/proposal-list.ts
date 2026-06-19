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
  ProposalFilters,
  ProposalListItem,
  ProposalService,
  ProposalStatus,
} from '../../../core/api/proposal.service';

const STATUS_LABELS: Record<ProposalStatus, string> = {
  DRAFT: 'Rascunho',
  READY_FOR_REVIEW: 'Pronta para revisão',
  APPROVED: 'Aprovada',
  SENT: 'Enviada',
  ACCEPTED: 'Aceita',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<ProposalStatus, TagSeverity> = {
  DRAFT: 'secondary',
  READY_FOR_REVIEW: 'info',
  APPROVED: 'info',
  SENT: 'warn',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'danger',
  CANCELLED: 'danger',
};

const UNASSIGNED = 'unassigned';

/**
 * Operational Proposal list — the Sales &amp; Proposals module landing. A paginated table of the Proposals
 * the user may work, with status, responsible, creation/validity period and amount filters plus a search
 * over the Proposal title and the source Opportunity name. Terminal-negative Proposals (rejected/expired/
 * cancelled) are hidden unless their status is explicitly chosen, and no filter can surface a Proposal the
 * caller may not see (the backend applies visibility at the query level). Exposes commercial-offer data
 * only — never Booking, Payment or Commission data.
 */
@Component({
  selector: 'app-proposal-list',
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
  templateUrl: './proposal-list.html',
  styleUrl: './proposal-list.css',
})
export class ProposalList implements OnInit {
  private readonly proposals = inject(ProposalService);

  protected readonly statusOptions = (
    [
      'DRAFT',
      'READY_FOR_REVIEW',
      'APPROVED',
      'SENT',
      'ACCEPTED',
      'REJECTED',
      'EXPIRED',
      'CANCELLED',
    ] as ProposalStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected status: ProposalStatus[] = [];
  protected responsible: string | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected validFrom: Date | null = null;
  protected validTo: Date | null = null;
  protected totalMin: number | null = null;
  protected totalMax: number | null = null;
  protected search = '';

  protected readonly items = signal<ProposalListItem[]>([]);
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
    this.proposals.responsibles().subscribe({
      next: (list) =>
        this.responsibleOptions.set([{ id: UNASSIGNED, name: 'Sem responsável' }, ...list]),
    });
  }

  protected statusLabel(status: ProposalStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ProposalStatus): TagSeverity {
    return STATUS_SEVERITY[status];
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
    this.validFrom = null;
    this.validTo = null;
    this.totalMin = null;
    this.totalMax = null;
    this.search = '';
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: ProposalFilters = {
      status: this.status,
      responsible: this.responsible,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      validFrom: toIsoDate(this.validFrom),
      validTo: toIsoDate(this.validTo),
      totalMin: this.totalMin,
      totalMax: this.totalMax,
      q: this.search,
    };
    this.proposals.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as propostas.');
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
