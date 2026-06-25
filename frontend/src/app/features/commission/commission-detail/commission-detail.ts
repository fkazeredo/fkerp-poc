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
import { SelectModule } from 'primeng/select';
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
import { ReferenceItem, ReferenceService } from '../../../core/api/reference.service';
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
 * understand a commission's origin, calculation, eligibility, approval and payment history. An authorized approver
 * can approve an Eligible commission (it becomes ready for payment) — but never their own (segregation of duties),
 * reject an Eligible one, or cancel an unpaid Expected/Approved one (each with a required reason). Rejecting/
 * cancelling voids the commission only: it touches no Order/Receivable and creates no financial data. Shows
 * commission + commercial-origin data only — never payroll, tax or accounting data; the backend is the authority.
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
    SelectModule,
    TextareaModule,
  ],
  templateUrl: './commission-detail.html',
  styleUrl: './commission-detail.css',
})
export class CommissionDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly commissions = inject(CommissionService);
  private readonly references = inject(ReferenceService);
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

  // Reject / cancel share the resolution-reason cadastro options (one shared list).
  protected readonly reasonOptions = signal<ReferenceItem[]>([]);

  // Reject dialog state (a required reason + optional note).
  protected readonly rejectDialogOpen = signal(false);
  protected readonly rejecting = signal(false);
  protected readonly rejectError = signal<string | null>(null);
  protected readonly rejectForm = this.fb.nonNullable.group({
    reasonId: ['', Validators.required],
    note: ['', Validators.maxLength(2000)],
  });

  // Cancel dialog state (a required reason + optional note).
  protected readonly cancelDialogOpen = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly cancelForm = this.fb.nonNullable.group({
    reasonId: ['', Validators.required],
    note: ['', Validators.maxLength(2000)],
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

  /** Whether the current user may reject this commission (only an Eligible commission can be rejected). */
  protected readonly canReject = computed<boolean>(() => {
    const c = this.commission();
    return !!c && c.status === 'ELIGIBLE' && this.auth.canRejectCommission();
  });

  /** Whether the current user may cancel this commission (only an unpaid Expected/Approved one). */
  protected readonly canCancel = computed<boolean>(() => {
    const c = this.commission();
    return (
      !!c && (c.status === 'EXPECTED' || c.status === 'APPROVED') && this.auth.canCancelCommission()
    );
  });

  /** The lifecycle timeline assembled from the stamps present (generated / eligible / approved / paid / voided). */
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
    if (c.resolvedAt) {
      entries.push({
        label: c.status === 'CANCELLED' ? 'Cancelada' : 'Rejeitada',
        at: c.resolvedAt,
        by: c.resolvedByName,
      });
    }
    return entries;
  });

  private commissionId = '';

  constructor() {
    // Keep the global unsaved flag (tab-close warning) in sync with any open dialog.
    effect(() =>
      this.unsaved.set(
        this.approveDialogOpen() || this.rejectDialogOpen() || this.cancelDialogOpen(),
      ),
    );
  }

  ngOnInit(): void {
    this.commissionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
    // Load the shared reject/cancel reasons only when the user can void commissions.
    if (this.auth.canRejectCommission() || this.auth.canCancelCommission()) {
      this.references
        .list('resolution-reasons', false, 'commission')
        .subscribe((items) => this.reasonOptions.set(items));
    }
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether an open dialog has modified fields (route guard + tab-close warning). */
  hasUnsavedChanges(): boolean {
    return (
      (this.approveDialogOpen() && this.approveForm.dirty) ||
      (this.rejectDialogOpen() && this.rejectForm.dirty) ||
      (this.cancelDialogOpen() && this.cancelForm.dirty)
    );
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
      next: (detail) => this.onResolved(detail, this.approveDialogOpen, this.approving, 'Comissão aprovada'),
      error: (err: HttpErrorResponse) => {
        this.approving.set(false);
        this.approveError.set(this.actionErrorMessage(err, 'aprovar'));
      },
    });
  }

  /** Opens the reject dialog (a required reason + optional note). */
  protected openReject(): void {
    if (!this.canReject()) {
      return;
    }
    this.rejectError.set(null);
    this.rejectForm.reset({ reasonId: '', note: '' });
    this.rejectDialogOpen.set(true);
  }

  /** Closes the reject dialog: if the form was changed, confirms before discarding. */
  protected async closeReject(): Promise<void> {
    if (this.rejectForm.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.rejectDialogOpen.set(false);
  }

  /** Rejects the eligible commission (terminal), then refreshes the detail. */
  protected submitReject(): void {
    const c = this.commission();
    if (!c || this.rejectForm.invalid || this.rejecting()) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    this.rejecting.set(true);
    this.rejectError.set(null);
    const v = this.rejectForm.getRawValue();
    this.commissions.reject(c.id, v.reasonId, emptyToNull(v.note)).subscribe({
      next: (detail) => this.onResolved(detail, this.rejectDialogOpen, this.rejecting, 'Comissão rejeitada'),
      error: (err: HttpErrorResponse) => {
        this.rejecting.set(false);
        this.rejectError.set(this.actionErrorMessage(err, 'rejeitar'));
      },
    });
  }

  /** Opens the cancel dialog (a required reason + optional note). */
  protected openCancel(): void {
    if (!this.canCancel()) {
      return;
    }
    this.cancelError.set(null);
    this.cancelForm.reset({ reasonId: '', note: '' });
    this.cancelDialogOpen.set(true);
  }

  /** Closes the cancel dialog: if the form was changed, confirms before discarding. */
  protected async closeCancel(): Promise<void> {
    if (this.cancelForm.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.cancelDialogOpen.set(false);
  }

  /** Cancels the unpaid commission (terminal), then refreshes the detail. */
  protected submitCancel(): void {
    const c = this.commission();
    if (!c || this.cancelForm.invalid || this.cancelling()) {
      this.cancelForm.markAllAsTouched();
      return;
    }
    this.cancelling.set(true);
    this.cancelError.set(null);
    const v = this.cancelForm.getRawValue();
    this.commissions.cancel(c.id, v.reasonId, emptyToNull(v.note)).subscribe({
      next: (detail) => this.onResolved(detail, this.cancelDialogOpen, this.cancelling, 'Comissão cancelada'),
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(false);
        this.cancelError.set(this.actionErrorMessage(err, 'cancelar'));
      },
    });
  }

  // Shared success handler: refresh the detail, mark the forms pristine, close the dialog, toast.
  private onResolved(
    detail: CommissionDetail,
    open: { set(v: boolean): void },
    busy: { set(v: boolean): void },
    summary: string,
  ): void {
    busy.set(false);
    this.commission.set(detail);
    this.approveForm.markAsPristine();
    this.rejectForm.markAsPristine();
    this.cancelForm.markAsPristine();
    open.set(false);
    this.messages.add({ severity: 'success', summary, detail: 'Operação concluída com sucesso.' });
  }

  protected back(): void {
    this.router.navigateByUrl('/comissoes');
  }

  /**
   * Keyboard: <kbd>a</kbd> approve, <kbd>r</kbd> reject, <kbd>c</kbd> cancel (each when allowed); <kbd>Esc</kbd>
   * closes the open dialog (guarded) or, when none is open, returns to the commission list.
   */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape' && !event.metaKey && !event.ctrlKey && !event.altKey) {
      if (document.querySelector('.p-select-overlay')) {
        return;
      }
      if (this.rejectDialogOpen()) {
        void this.closeReject();
      } else if (this.cancelDialogOpen()) {
        void this.closeCancel();
      } else if (this.approveDialogOpen()) {
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
    if (
      typing ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      this.approveDialogOpen() ||
      this.rejectDialogOpen() ||
      this.cancelDialogOpen()
    ) {
      return;
    }
    if (event.key === 'a' && this.canApprove()) {
      event.preventDefault();
      this.openApprove();
    } else if (event.key === 'r' && this.canReject()) {
      event.preventDefault();
      this.openReject();
    } else if (event.key === 'c' && this.canCancel()) {
      event.preventDefault();
      this.openCancel();
    }
  }

  private actionErrorMessage(err: HttpErrorResponse, action: string): string {
    const body = err.error as { message?: string } | null;
    if (err.status === 403) {
      return `Você não tem permissão para ${action} esta comissão.`;
    }
    if (err.status === 404) {
      return 'Comissão não encontrada.';
    }
    if (err.status === 422) {
      return body?.message ?? `Não foi possível ${action} a comissão neste estado.`;
    }
    if (err.status === 400) {
      return 'Informe o motivo.';
    }
    return body?.message ?? `Não foi possível ${action} a comissão.`;
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
