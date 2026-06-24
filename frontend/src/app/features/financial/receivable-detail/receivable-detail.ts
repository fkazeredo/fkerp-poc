import { Component, HostListener, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
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
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../core/auth/auth.service';
import {
  InstallmentStatus,
  PaymentMethodOption,
  ReceivableDetail,
  ReceivableInstallment,
  ReceivableService,
  ReceivableStatus,
} from '../../../core/api/receivable.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

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
 * Receivable detail page (Financial Operations): the record of a conta a receber — its value, due date and
 * status, the payer (Customer), the commercial origin (Order / Proposal / Opportunity / Lead) kept traceable,
 * the installment schedule and the payment history. An authorized financial user can register a full payment for
 * an open installment (the installment becomes Paid; when all are paid the receivable becomes Paid). Shows
 * receivable + payment data only — never Commission, Invoice or bank-reconciliation data.
 */
@Component({
  selector: 'app-receivable-detail',
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
    DatePickerModule,
    InputNumberModule,
    TextareaModule,
  ],
  templateUrl: './receivable-detail.html',
  styleUrl: './receivable-detail.css',
})
export class ReceivableDetailPage implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly receivables = inject(ReceivableService);
  private readonly fb = inject(FormBuilder);
  private readonly messages = inject(MessageService);
  private readonly unsaved = inject(UnsavedChangesService);
  protected readonly auth = inject(AuthService);

  protected readonly receivable = signal<ReceivableDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // Payment dialog state.
  protected readonly paymentDialogOpen = signal(false);
  protected readonly paymentMethods = signal<PaymentMethodOption[]>([]);
  protected readonly targetInstallment = signal<ReceivableInstallment | null>(null);
  protected readonly saving = signal(false);
  protected readonly paymentError = signal<string | null>(null);
  // The payment date cannot be in the future (mirrors the backend @PastOrPresent).
  protected readonly today = new Date();

  protected readonly paymentForm = this.fb.nonNullable.group({
    paymentMethodId: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    paymentDate: [null as Date | null, Validators.required],
    note: [''],
  });

  private receivableId = '';

  constructor() {
    // Keep the global unsaved flag (tab-close warning) in sync with the payment dialog state.
    effect(() => this.unsaved.set(this.paymentDialogOpen()));
  }

  ngOnInit(): void {
    this.receivableId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
    if (this.auth.canRegisterPayment()) {
      this.receivables.paymentMethods().subscribe({
        next: (methods) => this.paymentMethods.set(methods),
      });
    }
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether the payment dialog is open with modified fields (route guard + tab-close warning). */
  hasUnsavedChanges(): boolean {
    return this.paymentDialogOpen() && this.paymentForm.dirty;
  }

  protected statusLabel(status: ReceivableStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ReceivableStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  /** Installment statuses mirror the Receivable statuses (same values), so the same labels/severities apply. */
  protected installmentStatusLabel(status: InstallmentStatus): string {
    return STATUS_LABELS[status];
  }

  protected installmentStatusSeverity(status: InstallmentStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  /** Whether the installment can receive a payment (still has a balance) and the user is authorized. */
  protected canPay(installment: ReceivableInstallment): boolean {
    return (
      this.auth.canRegisterPayment() &&
      (installment.status === 'OPEN' || installment.status === 'PARTIALLY_PAID')
    );
  }

  /** Whole days the receivable is past due (for the overdue note); 0 when not overdue. */
  protected daysOverdue(dueDate: string): number {
    const due = new Date(dueDate + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const diff = today.getTime() - due.getTime();
    return diff > 0 ? Math.floor(diff / 86400000) : 0;
  }

  /** The human-friendly source order code (PC-000n). */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  /** Opens the payment dialog for the given installment, defaulting the amount to its outstanding balance. */
  protected openPayment(installment: ReceivableInstallment): void {
    this.targetInstallment.set(installment);
    this.paymentError.set(null);
    this.paymentForm.reset({
      paymentMethodId: '',
      amount: installment.outstanding,
      paymentDate: new Date(),
      note: '',
    });
    this.paymentDialogOpen.set(true);
  }

  /** Closes the payment dialog: if the form was changed, confirms before discarding. */
  protected async closePayment(): Promise<void> {
    if (this.paymentForm.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.paymentDialogOpen.set(false);
  }

  /** Registers the payment (full or partial) for the targeted installment, then refreshes the detail. */
  protected submitPayment(): void {
    const installment = this.targetInstallment();
    if (!installment || this.paymentForm.invalid || this.saving()) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.paymentError.set(null);
    const v = this.paymentForm.getRawValue();
    this.receivables
      .registerPayment(this.receivableId, installment.id, {
        paymentMethodId: v.paymentMethodId,
        amount: v.amount!,
        paymentDate: toIsoDate(v.paymentDate)!,
        note: emptyToNull(v.note),
      })
      .subscribe({
        next: (detail) => {
          this.saving.set(false);
          this.receivable.set(detail);
          this.paymentForm.markAsPristine();
          this.paymentDialogOpen.set(false);
          this.messages.add({
            severity: 'success',
            summary: 'Pagamento registrado',
            detail: 'O pagamento foi registrado com sucesso.',
          });
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          this.paymentError.set(this.paymentErrorMessage(err));
        },
      });
  }

  protected back(): void {
    this.router.navigateByUrl('/financeiro/contas-a-receber');
  }

  /**
   * Keyboard: <kbd>p</kbd> opens the payment dialog for the first payable installment (when authorized);
   * <kbd>Esc</kbd> closes the dialog (guarded) or, when none is open, returns to the list. While a PrimeNG
   * overlay (select/datepicker) is open, Esc closes that first.
   */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      if (document.querySelector('.p-select-overlay, .p-datepicker-panel')) {
        return;
      }
      if (this.paymentDialogOpen()) {
        void this.closePayment();
      } else {
        this.back();
      }
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey || this.paymentDialogOpen()) {
      return;
    }
    if (event.key === 'p' && this.auth.canRegisterPayment()) {
      const payable = this.receivable()?.installments.find(
        (i) => i.status === 'OPEN' || i.status === 'PARTIALLY_PAID',
      );
      if (payable) {
        event.preventDefault();
        this.openPayment(payable);
      }
    }
  }

  private paymentErrorMessage(err: HttpErrorResponse): string {
    const body = err.error as { message?: string } | null;
    if (err.status === 403) {
      return 'Você não tem permissão para registrar pagamentos.';
    }
    if (err.status === 404) {
      return 'Parcela ou conta a receber não encontrada.';
    }
    if (err.status === 422 || err.status === 400) {
      return body?.message ?? 'Não foi possível registrar o pagamento com estes dados.';
    }
    return body?.message ?? 'Não foi possível registrar o pagamento.';
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

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
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
