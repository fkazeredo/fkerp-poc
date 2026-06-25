import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import {
  CommissionRuleListItem,
  CommissionRuleService,
  CommissionTargetType,
} from '../../../core/api/commission-rule.service';
import { Responsible } from '../../../core/api/lead.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

const TARGET_LABELS: Record<CommissionTargetType, string> = {
  SELLER: 'Vendedor',
  SALES_REPRESENTATIVE: 'Representante',
  COMMERCIAL_RESPONSIBLE: 'Responsável comercial',
};

/**
 * Commission Rules management (Commission Management, Sprint 6 Slice 1): a commercial/financial manager defines the
 * basic rules — a percentage of the received amount — that the system will later use to calculate commissions. It
 * is configuration only: creating/editing a rule creates no Commission, Payment, payroll, payable, tax or
 * accounting data. Lives under Cadastros, gated by {@code commission:rule:manage}; the backend is the authority.
 */
@Component({
  selector: 'app-commission-rule-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    CheckboxModule,
    DatePickerModule,
    DialogModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './commission-rule-list.html',
  styleUrl: './commission-rule-list.css',
})
export class CommissionRuleList implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly rules = inject(CommissionRuleService);
  private readonly fb = inject(FormBuilder);
  private readonly messages = inject(MessageService);
  private readonly unsaved = inject(UnsavedChangesService);

  protected readonly items = signal<CommissionRuleListItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly includeInactive = signal(false);
  protected readonly responsibles = signal<Responsible[]>([]);

  protected readonly targetTypes = (Object.keys(TARGET_LABELS) as CommissionTargetType[]).map((value) => ({
    value,
    label: TARGET_LABELS[value],
  }));

  // Create/edit dialog state.
  protected readonly dialogOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly today = new Date();

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    percentage: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(100)]],
    targetType: ['SELLER' as CommissionTargetType, Validators.required],
    targetUserId: [null as string | null],
    startDate: [null as Date | null, Validators.required],
    endDate: [null as Date | null],
    notes: [''],
    allowAboveLimit: [false],
  });

  constructor() {
    effect(() => this.unsaved.set(this.dialogOpen()));
  }

  ngOnInit(): void {
    this.load();
    this.rules.responsibles().subscribe({ next: (r) => this.responsibles.set(r) });
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  /** Whether the dialog is open with modified fields (route guard + tab-close warning). */
  hasUnsavedChanges(): boolean {
    return this.dialogOpen() && this.form.dirty;
  }

  protected targetLabel(type: CommissionTargetType): string {
    return TARGET_LABELS[type];
  }

  protected toggleInactive(): void {
    this.includeInactive.update((v) => !v);
    this.load();
  }

  /** Opens the dialog to create a new rule (defaults: active percentage, today's start date). */
  protected openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({
      name: '',
      percentage: null,
      targetType: 'SELLER',
      targetUserId: null,
      startDate: new Date(),
      endDate: null,
      notes: '',
      allowAboveLimit: false,
    });
    this.dialogOpen.set(true);
  }

  /** Opens the dialog to edit an existing rule, loading its full detail. */
  protected openEdit(item: CommissionRuleListItem): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.rules.detail(item.id).subscribe({
      next: (rule) => {
        this.form.reset({
          name: rule.name,
          percentage: rule.percentage,
          targetType: rule.targetType,
          targetUserId: rule.targetUserId,
          startDate: new Date(rule.startDate + 'T00:00:00'),
          endDate: rule.endDate ? new Date(rule.endDate + 'T00:00:00') : null,
          notes: rule.notes ?? '',
          allowAboveLimit: false,
        });
        this.dialogOpen.set(true);
      },
      error: () => this.messages.add({ severity: 'error', summary: 'Não foi possível abrir a regra.' }),
    });
  }

  /** Closes the dialog: if the form was changed, confirms before discarding. */
  protected async close(): Promise<void> {
    if (this.form.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.dialogOpen.set(false);
  }

  /** Creates or updates the rule, then refreshes the list. */
  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    const v = this.form.getRawValue();
    const payload = {
      name: v.name.trim(),
      percentage: v.percentage!,
      targetType: v.targetType,
      targetUserId: v.targetUserId,
      startDate: toIsoDate(v.startDate)!,
      endDate: toIsoDate(v.endDate),
      notes: emptyToNull(v.notes),
      allowAboveLimit: v.allowAboveLimit,
    };
    const id = this.editingId();
    const request$ = id ? this.rules.update(id, payload) : this.rules.create(payload);
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.form.markAsPristine();
        this.dialogOpen.set(false);
        this.messages.add({
          severity: 'success',
          summary: id ? 'Regra atualizada' : 'Regra criada',
        });
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(this.errorMessage(err));
      },
    });
  }

  protected toggleActive(item: CommissionRuleListItem): void {
    const request$ = item.active ? this.rules.deactivate(item.id) : this.rules.activate(item.id);
    request$.subscribe({ next: () => this.load() });
  }

  private errorMessage(err: HttpErrorResponse): string {
    const body = err.error as { message?: string } | null;
    if (err.status === 422 || err.status === 400) {
      return body?.message ?? 'Não foi possível salvar a regra com estes dados.';
    }
    return body?.message ?? 'Não foi possível salvar a regra.';
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rules.list(this.includeInactive()).subscribe({
      next: (rules) => {
        this.items.set(rules);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para gerenciar regras de comissão.'
            : 'Não foi possível carregar as regras de comissão.',
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
