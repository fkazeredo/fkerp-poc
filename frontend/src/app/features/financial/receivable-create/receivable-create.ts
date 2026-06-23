import {
  Component,
  HostListener,
  inject,
  signal,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Responsible } from '../../../core/api/lead.service';
import {
  CreateReceivable,
  EligibleOrder,
  ReceivableService,
} from '../../../core/api/receivable.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

/**
 * Reactive form to create a Receivable (conta a receber) from a Commercial Order with a confirmed booking. The
 * order selector is fed by the eligible-orders endpoint (booking CONFIRMED, without an active Receivable); the
 * due date is required. Mirrors the backend validation for fast feedback — the backend stays the only authority.
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

  protected readonly form = this.fb.nonNullable.group({
    commercialOrderId: ['', Validators.required],
    dueDate: [null as Date | null, Validators.required],
    financialResponsibleId: [''],
    paymentNotes: [''],
  });

  constructor() {
    // Keep the global unsaved flag (used by the tab-close warning) in sync with the form's dirty state.
    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.unsaved.set(this.hasUnsavedChanges()));
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

  /** The order option label (PC-000n · cliente · valor) used in the selector and the summary. */
  protected orderLabel(o: EligibleOrder): string {
    return 'PC-' + String(o.number).padStart(4, '0') + ' · ' + (o.customerName ?? 'Cliente') ;
  }

  /** The currently selected eligible order (for the summary panel), or null. */
  protected selectedOrder(): EligibleOrder | null {
    const id = this.form.controls.commercialOrderId.value;
    return this.eligibleOrders().find((o) => o.orderId === id) ?? null;
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
    this.loading.set(true);
    this.formError.set(null);

    const v = this.form.getRawValue();
    const payload: CreateReceivable = {
      commercialOrderId: v.commercialOrderId,
      dueDate: toIsoDate(v.dueDate)!,
      financialResponsiblePersonId: emptyToNull(v.financialResponsibleId),
      paymentNotes: emptyToNull(v.paymentNotes),
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

  private handleError(err: HttpErrorResponse): void {
    const body = err.error as { message?: string } | null;
    if (err.status === 409) {
      this.formError.set(body?.message ?? 'Este pedido já possui uma conta a receber ativa.');
    } else if (err.status === 422) {
      this.formError.set(body?.message ?? 'Apenas pedidos com reserva confirmada podem gerar uma conta a receber.');
    } else {
      this.formError.set(body?.message ?? 'Não foi possível criar a conta a receber.');
    }
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
