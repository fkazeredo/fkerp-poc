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
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { Responsible } from '../../../core/api/lead.service';
import {
  CommissionBasis,
  CommissionFilters,
  CommissionListItem,
  CommissionOperationalSummary,
  CommissionService,
  CommissionStatus,
  ReceivableStatusCode,
} from '../../../core/api/commission.service';
import { CommissionRuleListItem, CommissionRuleService } from '../../../core/api/commission-rule.service';

const STATUS_LABELS: Record<CommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  PAID: 'Paga',
  CANCELLED: 'Cancelada',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<CommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
  PAID: 'success',
  CANCELLED: 'secondary',
};

const BASIS_LABELS: Record<CommissionBasis, string> = {
  COMMERCIAL_AMOUNT: 'Valor comercial (previsão)',
  RECEIVED_AMOUNT: 'Valor recebido',
};

const RECEIVABLE_LABELS: Record<ReceivableStatusCode, string> = {
  OPEN: 'Em aberto',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
  CANCELLED: 'Cancelada',
};

/** Formats the source Order's sequential number as the human-friendly code PC-000n. */
export function orderCode(n: number): string {
  return 'PC-' + String(n).padStart(4, '0');
}

/**
 * Operational Commission list (Comercial module): the commissions a commercial/financial manager tracks — expected,
 * eligible (pending approval), approved and paid. A paginated table with status, beneficiary, source order, rule,
 * creation/eligibility/payment periods and amount-range filters. The settled Paid and the terminal Rejected/Cancelled
 * are hidden unless their status is explicitly chosen; eligible commissions stay visible as pending approval. No
 * filter can surface a commission the caller may not see (sellers/representatives see only their own — visibility is
 * applied at the query level). Shows commission + commercial-origin data only — never payroll, tax, accounting or
 * accounts-payable data.
 */
@Component({
  selector: 'app-commission-list',
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
    TagModule,
    MessageModule,
  ],
  templateUrl: './commission-list.html',
  styleUrl: './commission-list.css',
})
export class CommissionList implements OnInit {
  private readonly commissions = inject(CommissionService);
  private readonly rulesApi = inject(CommissionRuleService);

  protected readonly statusOptions = (
    ['EXPECTED', 'ELIGIBLE', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED'] as CommissionStatus[]
  ).map((value) => ({ value, label: STATUS_LABELS[value] }));
  protected readonly beneficiaryOptions = signal<Responsible[]>([]);
  protected readonly ruleOptions = signal<CommissionRuleListItem[]>([]);

  protected status: CommissionStatus[] = [];
  protected beneficiary: string | null = null;
  protected orderNumber: number | null = null;
  protected rule: string | null = null;
  protected createdFrom: Date | null = null;
  protected createdTo: Date | null = null;
  protected eligibleFrom: Date | null = null;
  protected eligibleTo: Date | null = null;
  protected paidFrom: Date | null = null;
  protected paidTo: Date | null = null;
  protected amountMin: number | null = null;
  protected amountMax: number | null = null;

  protected readonly items = signal<CommissionListItem[]>([]);
  protected readonly total = signal(0);
  // The operational grouping (count + total amount by status / by beneficiary) of the same visible+filtered set.
  protected readonly summary = signal<CommissionOperationalSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  ngOnInit(): void {
    this.rulesApi.responsibles().subscribe({ next: (list) => this.beneficiaryOptions.set(list) });
    this.rulesApi.list(true).subscribe({ next: (list) => this.ruleOptions.set(list) });
  }

  protected orderCode(n: number): string {
    return orderCode(n);
  }

  protected statusLabel(status: CommissionStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: CommissionStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected basisLabel(basis: CommissionBasis): string {
    return BASIS_LABELS[basis];
  }

  protected receivableLabel(status: ReceivableStatusCode | null): string {
    return status ? RECEIVABLE_LABELS[status] : '—';
  }

  /** The source reference shown for the commission's origin (Opportunity, falling back to Proposal). */
  protected originReference(item: CommissionListItem): string {
    return item.opportunityReference ?? item.proposalReference ?? '—';
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
    this.beneficiary = null;
    this.orderNumber = null;
    this.rule = null;
    this.createdFrom = null;
    this.createdTo = null;
    this.eligibleFrom = null;
    this.eligibleTo = null;
    this.paidFrom = null;
    this.paidTo = null;
    this.amountMin = null;
    this.amountMax = null;
    this.applyFilters();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: CommissionFilters = {
      status: this.status,
      beneficiary: this.beneficiary,
      orderNumber: this.orderNumber,
      rule: this.rule,
      createdFrom: toIsoDate(this.createdFrom),
      createdTo: toIsoDate(this.createdTo),
      eligibleFrom: toIsoDate(this.eligibleFrom),
      eligibleTo: toIsoDate(this.eligibleTo),
      paidFrom: toIsoDate(this.paidFrom),
      paidTo: toIsoDate(this.paidTo),
      amountMin: this.amountMin,
      amountMax: this.amountMax,
    };
    this.commissions.list(filters, this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as comissões.');
      },
    });
    // The operational summary mirrors the same filters/visibility; failing it must not break the list.
    this.commissions.summary(filters).subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => this.summary.set(null),
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
