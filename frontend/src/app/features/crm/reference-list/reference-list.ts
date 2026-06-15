import { Component, inject, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ReferenceItem, ReferenceService } from '../../../core/api/reference.service';

/** Generic CRUD screen for a CRM reference-data cadastro, driven by route data {title, path}. */
@Component({
  selector: 'app-reference-list',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    TableModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    ToggleSwitchModule,
    TagModule,
    TooltipModule,
  ],
  templateUrl: './reference-list.html',
  styleUrl: './reference-list.css',
})
export class ReferenceList {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ReferenceService);
  private readonly fb = inject(FormBuilder);
  private readonly messages = inject(MessageService);

  protected readonly title = signal('');
  private readonly path = signal('');

  protected readonly items = signal<ReferenceItem[]>([]);
  protected readonly loading = signal(false);
  protected readonly includeInactive = signal(false);

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<ReferenceItem | null>(null);
  protected readonly saving = signal(false);
  protected readonly dialogTitle = computed(() =>
    this.editing() ? 'Editar registro' : 'Novo registro',
  );

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    label: ['', Validators.required],
    sortOrder: [0, Validators.required],
    active: [true],
  });

  constructor() {
    this.route.data.subscribe((data) => {
      this.title.set(data['title'] ?? 'Cadastro');
      this.path.set(data['path'] ?? '');
      this.reload();
    });
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list(this.path(), this.includeInactive()).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Não foi possível carregar os registros.',
        });
      },
    });
  }

  protected toggleInactive(): void {
    this.includeInactive.update((v) => !v);
    this.reload();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ code: '', label: '', sortOrder: this.nextSortOrder(), active: true });
    this.form.controls.code.enable();
    this.dialogOpen.set(true);
  }

  protected openEdit(item: ReferenceItem): void {
    this.editing.set(item);
    this.form.reset({
      code: item.code,
      label: item.label,
      sortOrder: item.sortOrder,
      active: item.active,
    });
    this.form.controls.code.disable();
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const current = this.editing();
    const request = current
      ? this.api.update(this.path(), current.id, {
          label: v.label.trim(),
          sortOrder: v.sortOrder,
          active: v.active,
        })
      : this.api.create(this.path(), {
          code: v.code.trim(),
          label: v.label.trim(),
          sortOrder: v.sortOrder,
        });

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogOpen.set(false);
        this.messages.add({ severity: 'success', summary: 'Salvo', detail: 'Registro salvo.' });
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        const body = err.error as { message?: string } | null;
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: body?.message ?? 'Não foi possível salvar o registro.',
        });
      },
    });
  }

  protected deactivate(item: ReferenceItem): void {
    this.api.deactivate(this.path(), item.id).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Inativado', detail: item.label });
        this.reload();
      },
      error: () =>
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Não foi possível inativar o registro.',
        }),
    });
  }

  protected activate(item: ReferenceItem): void {
    this.api
      .update(this.path(), item.id, { label: item.label, sortOrder: item.sortOrder, active: true })
      .subscribe({
        next: () => {
          this.messages.add({ severity: 'success', summary: 'Reativado', detail: item.label });
          this.reload();
        },
        error: () =>
          this.messages.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Não foi possível reativar o registro.',
          }),
      });
  }

  private nextSortOrder(): number {
    const max = this.items().reduce((acc, i) => Math.max(acc, i.sortOrder), 0);
    return max + 1;
  }
}
