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
import { Origin, Responsible } from '../../../core/api/lead.service';
import {
  OpportunityFilters,
  OpportunityListItem,
  OpportunityService,
  OpportunityStage,
} from '../../../core/api/opportunity.service';

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  WON: 'Ganha',
  LOST: 'Perdida',
};

const UNASSIGNED = 'unassigned';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/**
 * Operational Opportunity list: paginated table of the Opportunities the user may work, with stage,
 * responsible, origin, creation/expected-close period and estimated-value filters plus search (over the
 * Opportunity and the source Lead's name and contacts). Lost Opportunities are hidden unless LOST is
 * explicitly chosen, and no filter can surface an Opportunity the caller may not see (the backend applies
 * visibility at the query level). The title links to the source Lead (there is no Opportunity detail
 * screen yet).
 */
@Component({
  selector: 'app-opportunity-list',
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
  templateUrl: './opportunity-list.html',
  styleUrl: './opportunity-list.css',
})
export class OpportunityList implements OnInit {
  private readonly opportunities = inject(OpportunityService);

  protected readonly stageOptions = (
    ['NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'LOST'] as OpportunityStage[]
  ).map((value) => ({ value, label: STAGE_LABELS[value] }));
  protected readonly origins = signal<Origin[]>([]);
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected stage: OpportunityStage[] = [];
  protected responsible: string | null = null;
  protected originId: string | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected closeFrom: Date | null = null;
  protected closeTo: Date | null = null;
  protected valueMin: number | null = null;
  protected valueMax: number | null = null;
  protected search = '';

  protected readonly items = signal<OpportunityListItem[]>([]);
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
    this.opportunities.origins().subscribe({ next: (list) => this.origins.set(list) });
    this.opportunities.responsibles().subscribe({
      next: (list) =>
        this.responsibleOptions.set([{ id: UNASSIGNED, name: 'Sem responsável' }, ...list]),
    });
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected stageSeverity(stage: OpportunityStage): TagSeverity {
    switch (stage) {
      case 'NEW_OPPORTUNITY':
        return 'info';
      case 'DISCOVERY':
        return 'secondary';
      case 'PRODUCT_FIT':
        return 'warn';
      case 'READY_FOR_PROPOSAL':
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
    this.stage = [];
    this.responsible = null;
    this.originId = null;
    this.createdFrom = null;
    this.createdTo = null;
    this.closeFrom = null;
    this.closeTo = null;
    this.valueMin = null;
    this.valueMax = null;
    this.search = '';
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: OpportunityFilters = {
      stage: this.stage,
      responsible: this.responsible,
      originId: this.originId,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      closeFrom: toIsoDate(this.closeFrom),
      closeTo: toIsoDate(this.closeTo),
      valueMin: this.valueMin,
      valueMax: this.valueMax,
      q: this.search,
    };
    this.opportunities.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as oportunidades.');
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
