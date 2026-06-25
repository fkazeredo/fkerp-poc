import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CommissionBasis,
  CommissionDetail,
  CommissionService,
  CommissionStatus,
  ReceivableStatusCode,
} from '../../../core/api/commission.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

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
 * basis and the applied rule (the percentage is the snapshot, so it stays visible even if the rule changes). An
 * authorized approver can approve an Eligible commission (it becomes ready for payment) — but never their own
 * (segregation of duties; the backend is the authority). Shows commission + commercial-origin data only — never
 * payroll, tax or accounting data. The user only opens commissions they are allowed to see (own vs all).
 */
@Component({
  selector: 'app-commission-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    TagModule,
    MessageModule,
    DialogModule,
    TextareaModule,
  ],
  templateUrl: './commission-detail.html',
  styleUrl: './commission-detail.css',
})
export class CommissionDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly commissions = inject(CommissionService);
  private readonly fb = inject(FormBuilder);
  private readonly messages = inject(MessageService);
  private readonly unsaved = inject(UnsavedChangesService);
  protected readonly auth = inject(AuthService);

  protected readonly commission = signal<CommissionDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // Approve dialog state (the notes are optional).
  protected readonly approveDialogOpen = signal(false);
  protected readonly approving = signal(false);
  protected readonly approveError = signal<string | null>(null);
  protected readonly approveForm = this.fb.nonNullable.group({
    notes: ['', Validators.maxLength(2000)],
  });

  /** Whether the current user may approve this commission: it is Eligible, they hold the scope, and it is not theirs. */
  protected readonly canApprove = computed<boolean>(() => {
    const c = this.commission();
    return (
      !!c &&
      c.status === 'ELIGIBLE' &&
      this.auth.canApproveCommission() &&
      this.auth.userId() !== c.beneficiaryUserId
    );
  });

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
      entries.push({ label: 'Aprovada', at: c.approvedAt, by: c.approvedByName });
    }
    if (c.paidAt) {
      entries.push({ label: 'Paga', at: c.paidAt, by: null });
    }
    return entries;
  });

  private commissionId = '';

  constructor() {
    // Keep the global unsaved flag (tab-close warning) in sync with the open dialog.
    effect(() => this.unsaved.set(this.approveDialogOpen()));
  }

  ngOnInit(): void {
    this.commissionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether the approve dialog is open with modified fields (route guard + tab-close warning). */
  hasUnsavedChanges(): boolean {
    return this.approveDialogOpen() && this.approveForm.dirty;
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

  /** Opens the approve dialog (clears the optional notes). */
  protected openApprove(): void {
    if (!this.canApprove()) {
      return;
    }
    this.approveError.set(null);
    this.approveForm.reset({ notes: '' });
    this.approveDialogOpen.set(true);
  }

  /** Closes the approve dialog: if the form was changed, confirms before discarding. */
  protected async closeApprove(): Promise<void> {
    if (this.approveForm.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.approveDialogOpen.set(false);
  }

  /** Approves the eligible commission, then refreshes the detail (it becomes Approved / ready for payment). */
  protected submitApprove(): void {
    const c = this.commission();
    if (!c || this.approveForm.invalid || this.approving()) {
      this.approveForm.markAllAsTouched();
      return;
    }
    this.approving.set(true);
    this.approveError.set(null);
    const notes = emptyToNull(this.approveForm.getRawValue().notes);
    this.commissions.approve(c.id, notes).subscribe({
      next: (detail) => {
        this.approving.set(false);
        this.commission.set(detail);
        this.approveForm.markAsPristine();
        this.approveDialogOpen.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Comissão aprovada',
          detail: 'A comissão foi aprovada e está pronta para pagamento.',
        });
      },
      error: (err: HttpErrorResponse) => {
        this.approving.set(false);
        this.approveError.set(this.approveErrorMessage(err));
      },
    });
  }

  protected back(): void {
    this.router.navigateByUrl('/comissoes');
  }

  /**
   * Keyboard: <kbd>a</kbd> opens the approve dialog (when allowed); <kbd>Esc</kbd> closes the dialog (guarded) or,
   * when none is open, returns to the commission list.
   */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !event.metaKey && !event.ctrlKey && !event.altKey) {
      if (this.approveDialogOpen()) {
        void this.closeApprove();
      } else {
        this.back();
      }
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.approveDialogOpen()) {
      return;
    }
    if (event.key === 'a' && this.canApprove()) {
      event.preventDefault();
      this.openApprove();
    }
  }

  private approveErrorMessage(err: HttpErrorResponse): string {
    const body = err.error as { message?: string } | null;
    if (err.status === 403) {
      return 'Você não pode aprovar esta comissão (sem permissão ou é a sua própria comissão).';
    }
    if (err.status === 404) {
      return 'Comissão não encontrada.';
    }
    if (err.status === 422) {
      return body?.message ?? 'Apenas comissões elegíveis podem ser aprovadas.';
    }
    return body?.message ?? 'Não foi possível aprovar a comissão.';
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

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
