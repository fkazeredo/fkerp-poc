import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ReceivableDetail, ReceivableService, ReceivableStatus } from '../../../core/api/receivable.service';

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

/**
 * Receivable detail page (Financial Operations): the read-only record of a conta a receber — its value, due
 * date and status, the payer (Customer) and the commercial origin (Order / Proposal / Opportunity / Lead) kept
 * traceable. Shows receivable data only — never Payment, Commission or Invoice data.
 */
@Component({
  selector: 'app-receivable-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, ButtonModule, CardModule, TagModule, MessageModule],
  templateUrl: './receivable-detail.html',
  styleUrl: './receivable-detail.css',
})
export class ReceivableDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly receivables = inject(ReceivableService);

  protected readonly receivable = signal<ReceivableDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private receivableId = '';

  ngOnInit(): void {
    this.receivableId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: ReceivableStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ReceivableStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  /** The human-friendly source order code (PC-000n). */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  protected back(): void {
    this.router.navigateByUrl('/financeiro/contas-a-receber');
  }

  /** Esc returns to the Receivable list. */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.back();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.receivables.detail(this.receivableId).subscribe({
      next: (detail) => {
        this.receivable.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta conta a receber.'
            : err.status === 404
              ? 'Conta a receber não encontrada.'
              : 'Não foi possível carregar a conta a receber.',
        );
      },
    });
  }
}
