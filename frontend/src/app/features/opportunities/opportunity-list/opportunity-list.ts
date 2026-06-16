import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { Subject, debounceTime } from 'rxjs';
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
  LOST: 'Perdida',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/**
 * Operational Opportunity list: paginated table of the Opportunities the user may work, with a stage
 * filter and search. Lost Opportunities are hidden unless LOST is explicitly chosen. The title links
 * to the source Lead (there is no Opportunity detail screen yet).
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
    MultiSelectModule,
    TagModule,
    MessageModule,
  ],
  templateUrl: './opportunity-list.html',
  styleUrl: './opportunity-list.css',
})
export class OpportunityList {
  private readonly opportunities = inject(OpportunityService);

  protected readonly stageOptions = (
    ['NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'LOST'] as OpportunityStage[]
  ).map((value) => ({ value, label: STAGE_LABELS[value] }));

  protected stage: OpportunityStage[] = [];
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
    this.search = '';
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: OpportunityFilters = { stage: this.stage, q: this.search };
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
