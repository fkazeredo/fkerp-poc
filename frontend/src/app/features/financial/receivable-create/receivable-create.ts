import { Component, HostListener, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Responsible } from '../../../core/api/lead.service';
import {
  CreateInstallment,
  CreateReceivable,
  EligibleOrder,
  ReceivableService,
} from '../../../core/api/receivable.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

type InstallmentForm = FormGroup<{
  amount: FormControl<number | null>;
  dueDate: FormControl<Date | null>;
  paymentNotes: FormControl<string>;
}>;

/**
 * Reactive form to create a Receivable (conta a receber) from a Commercial Order with a confirmed booking. The
 * order selector is fed by the eligible-orders endpoint (booking CONFIRMED, without an active Receivable). The
 * receivable may be split into installments: an optional editor whose rows must sum to the order total. With no
 * installment rows, a single full-amount installment is created at the given due date. Mirrors the backend
 * validation for fast feedback — the backend stays the only authority.
 */
@Component({
  selector: 'app-receivable-create',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    ButtonModule,
    TextareaModule,
    SelectModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
  ],
  templateUrl: './receivable-create.html',
  styleUrl: './receivable-create.css',
})
export class ReceivableCreate implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly receivables = inject(ReceivableService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly unsaved = inject(UnsavedChangesService);

  // Set when the user is intentionally leaving (cancel or successful submit), so the guard does not prompt.
  private leaving = false;

  protected readonly eligibleOrders = signal<EligibleOrder[]>([]);
  protected readonly responsibles = signal<Responsible[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadingOrders = signal(true);
  protected readonly formError = signal<string | null>(null);

  // Live, signal-backed figures (kept in sync on every form change) so the "remaining" reacts under zoneless.
  protected readonly orderTotal = signal(0);
  protected readonly installmentsSum = signal(0);
  protected readonly remaining = computed(() => round2(this.orderTotal() - this.installmentsSum()));

  protected readonly installments = this.fb.array<InstallmentForm>([]);

  protected readonly form = this.fb.nonNullable.group({
    commercialOrderId: ['', Validators.required],
    dueDate: [null as Date | null, Validators.required],
    financialResponsibleId: [''],
    paymentNotes: [''],
    installments: this.installments,
  });

  constructor() {
    // Keep the global unsaved flag and the live figures in sync with the form's state.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.unsaved.set(this.hasUnsavedChanges());
      this.recompute();
    });
  }

  ngOnInit(): void {
    this.receivables.eligibleOrders().subscribe({
      next: (list) => {
        this.eligibleOrders.set(list);
        this.loadingOrders.set(false);
        // Pre-select the order passed from the order detail's "Gerar conta a receber" action, when eligible.
        const preselect = this.route.snapshot.queryParamMap.get('order');
        if (preselect && list.some((o) => o.orderId === preselect)) {
          this.form.controls.commercialOrderId.setValue(preselect);
        }
        this.recompute();
      },
      error: () => {
        this.loadingOrders.set(false);
        this.formError.set('Não foi possível carregar os pedidos elegíveis.');
      },
    });
    this.receivables.responsibles().subscribe({
      next: (list) => this.responsibles.set(list),
    });
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether the form has unsaved edits (used by the route guard and the tab-close warning). */
  hasUnsavedChanges(): boolean {
    return !this.leaving && this.form.dirty;
  }

  /** The order option label (PC-000n · cliente) used in the selector and the summary. */
  protected orderLabel(o: EligibleOrder): string {
    return 'PC-' + String(o.number).padStart(4, '0') + ' · ' + (o.customerName ?? 'Cliente');
  }

  /** The currently selected eligible order (for the summary panel), or null. */
  protected selectedOrder(): EligibleOrder | null {
    const id = this.form.controls.commercialOrderId.value;
    return this.eligibleOrders().find((o) => o.orderId === id) ?? null;
  }

  /** Whether the receivable is being split into installments (≥1 editor row). */
  protected hasInstallments(): boolean {
    return this.installments.length > 0;
  }

  /** The installment rows, for the template. */
  protected installmentRows(): InstallmentForm[] {
    return this.installments.controls;
  }

  /** Adds an empty installment row (switches the form into split mode). */
  protected addInstallment(): void {
    this.installments.push(
      this.fb.group({
        amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
        dueDate: this.fb.control<Date | null>(null, Validators.required),
        paymentNotes: this.fb.nonNullable.control(''),
      }),
    );
    this.syncDueDateValidator();
    this.form.markAsDirty();
    this.recompute();
  }

  /** Removes an installment row (back to single-installment mode when the last is removed). */
  protected removeInstallment(index: number): void {
    this.installments.removeAt(index);
    this.syncDueDateValidator();
    this.form.markAsDirty();
    this.recompute();
  }

  /** Whether the schedule is balanced: no split, or the installments sum to the order total. */
  protected scheduleBalanced(): boolean {
    return !this.hasInstallments() || Math.abs(this.remaining()) < 0.005;
  }

  /**
   * Esc cancels the screen through the same guard as the Cancel button. When a PrimeNG overlay is open, Esc
   * closes that first instead of leaving the screen.
   */
  @HostListener('document:keydown.escape', ['$event'])
  protected onEscape(event: Event): void {
    const overlayOpen = document.querySelector(
      '.p-select-overlay, .p-datepicker-panel, .p-multiselect-overlay',
    );
    if (event.defaultPrevented || overlayOpen) {
      return;
    }
    void this.cancel();
  }

  /** Cancels: if the form has edits, confirms before discarding; otherwise returns to the list. */
  protected async cancel(): Promise<void> {
    if (this.form.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.leaving = true;
    this.unsaved.set(false);
    this.router.navigateByUrl('/financeiro/contas-a-receber');
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.scheduleBalanced()) {
      this.formError.set('A soma das parcelas deve ser igual ao valor total da conta a receber.');
      return;
    }
    this.loading.set(true);
    this.formError.set(null);

    const v = this.form.getRawValue();
    const installments: CreateInstallment[] = this.installments.controls.map((g) => ({
      amount: g.controls.amount.value ?? 0,
      dueDate: toIsoDate(g.controls.dueDate.value)!,
      paymentNotes: emptyToNull(g.controls.paymentNotes.value),
    }));
    // With a schedule, the receivable's reference due date is the first installment's; otherwise the field value.
    const dueDate = installments.length > 0 ? installments[0].dueDate : toIsoDate(v.dueDate)!;
    const payload: CreateReceivable = {
      commercialOrderId: v.commercialOrderId,
      dueDate,
      financialResponsiblePersonId: emptyToNull(v.financialResponsibleId),
      paymentNotes: emptyToNull(v.paymentNotes),
      installments: installments.length > 0 ? installments : undefined,
    };

    this.receivables.create(payload).subscribe({
      next: (created) => {
        this.leaving = true;
        this.unsaved.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Conta a receber criada',
          detail: 'A conta a receber foi gerada com sucesso.',
        });
        this.router.navigateByUrl('/financeiro/contas-a-receber/' + created.id);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.handleError(err);
      },
    });
  }

  // The top-level due date is required only when the receivable is NOT split (a single installment uses it);
  // with a schedule, the per-row dates drive everything and the reference date is derived from the first row.
  private syncDueDateValidator(): void {
    const ctrl = this.form.controls.dueDate;
    if (this.hasInstallments()) {
      ctrl.clearValidators();
    } else {
      ctrl.setValidators(Validators.required);
    }
    ctrl.updateValueAndValidity({ emitEvent: false });
  }

  private recompute(): void {
    const order = this.selectedOrder();
    this.orderTotal.set(order ? order.total : 0);
    const sum = this.installments.controls.reduce((acc, g) => acc + (g.controls.amount.value ?? 0), 0);
    this.installmentsSum.set(round2(sum));
  }

  private handleError(err: HttpErrorResponse): void {
    const body = err.error as { message?: string } | null;
    if (err.status === 409) {
      this.formError.set(body?.message ?? 'Este pedido já possui uma conta a receber ativa.');
    } else if (err.status === 422) {
      this.formError.set(body?.message ?? 'Não foi possível criar a conta a receber com estes dados.');
    } else {
      this.formError.set(body?.message ?? 'Não foi possível criar a conta a receber.');
    }
  }
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
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
