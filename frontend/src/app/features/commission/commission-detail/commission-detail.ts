import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import {
  CommissionBasis,
  CommissionDetail,
  CommissionService,
  CommissionStatus,
  ReceivableStatusCode,
} from '../../../core/api/commission.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_LABELS: Record<CommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  PAID: 'Paga',
  CANCELLED: 'Cancelada',
};

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

/** One entry of the commission's lifecycle timeline (derived from the stamps it carries). */
interface TimelineEntry {
  label: string;
  at: string;
  by: string | null;
}

/**
 * Commission detail page (Commission Management): the read-only record a commercial/financial manager opens to
 * understand a commission's origin, calculation, eligibility, approval and payment history. Keeps the commercial
 * origin (Order / Proposal / Opportunity / Lead) and the related Receivable traceable, and shows the calculation
 * basis and the applied rule (the percentage is the snapshot, so it stays visible even if the rule changes). Shows
 * commission + commercial-origin data only — never payroll, tax or accounting data. The user only opens commissions
 * they are allowed to see (the backend narrows visibility — own vs all).
 */
@Component({
  selector: 'app-commission-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, ButtonModule, CardModule, TagModule, MessageModule],
  templateUrl: './commission-detail.html',
  styleUrl: './commission-detail.css',
})
export class CommissionDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly commissions = inject(CommissionService);

  protected readonly commission = signal<CommissionDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** The lifecycle timeline assembled from the stamps present (generated / eligible / approved / paid). */
  protected readonly timeline = computed<TimelineEntry[]>(() => {
    const c = this.commission();
    if (!c) {
      return [];
    }
    const entries: TimelineEntry[] = [{ label: 'Gerada', at: c.createdAt, by: c.createdByName }];
    if (c.eligibleAt) {
      entries.push({ label: 'Elegível (conta a receber paga)', at: c.eligibleAt, by: null });
    }
    if (c.approvedAt) {
      entries.push({ label: 'Aprovada', at: c.approvedAt, by: null });
    }
    if (c.paidAt) {
      entries.push({ label: 'Paga', at: c.paidAt, by: null });
    }
    return entries;
  });

  private commissionId = '';

  ngOnInit(): void {
    this.commissionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
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

  /** The human-friendly source order code (PC-000n). */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  protected back(): void {
    this.router.navigateByUrl('/comissoes');
  }

  /** Esc returns to the commission list. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !event.metaKey && !event.ctrlKey && !event.altKey) {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.commissions.detail(this.commissionId).subscribe({
      next: (detail) => {
        this.commission.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta comissão.'
            : err.status === 404
              ? 'Comissão não encontrada.'
              : 'Não foi possível carregar a comissão.',
        );
      },
    });
  }
}
