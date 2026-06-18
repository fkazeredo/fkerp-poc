import {
  Component,
  inject,
  signal,
  viewChild,
  AfterViewInit,
  ElementRef,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { CreateLead, LeadService, Origin, Responsible } from '../../../core/api/lead.service';
import { HasUnsavedChanges, UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

/** Reactive form to register a new lead, including the optional first note and responsible person. */
@Component({
  selector: 'app-lead-create',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    MessageModule,
  ],
  templateUrl: './lead-create.html',
  styleUrl: './lead-create.css',
})
export class LeadCreate implements OnInit, AfterViewInit, OnDestroy, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly leads = inject(LeadService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly unsaved = inject(UnsavedChangesService);

  // Set when the user is intentionally leaving (cancel or successful submit), so the guard does not prompt.
  private leaving = false;

  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');

  protected readonly origins = signal<Origin[]>([]);
  protected readonly responsibles = signal<Responsible[]>([]);
  protected readonly loading = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    originId: ['', Validators.required],
    phone: [''],
    whatsapp: [''],
    email: ['', Validators.email],
    responsibleId: [''],
    initialNote: [''],
  });

  constructor() {
    // Keep the global unsaved flag (used by the tab-close warning) in sync with the form's dirty state.
    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.unsaved.set(this.hasUnsavedChanges()));
  }

  ngOnInit(): void {
    this.leads.origins().subscribe({
      next: (list) => this.origins.set(list),
      error: () => this.formError.set('Não foi possível carregar as origens.'),
    });
    this.leads.responsibles().subscribe({
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

  ngAfterViewInit(): void {
    queueMicrotask(() => this.nameInput()?.nativeElement.focus());
  }

  protected fieldError(name: string): string | null {
    return this.fieldErrors()[name] ?? null;
  }

  /** Cancels: if the form has edits, confirms before discarding; otherwise returns to the home screen. */
  protected async cancel(): Promise<void> {
    if (this.form.dirty && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.leaving = true;
    this.unsaved.set(false);
    this.router.navigateByUrl('/');
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.formError.set(null);
    this.fieldErrors.set({});

    const v = this.form.getRawValue();
    const payload: CreateLead = {
      name: v.name.trim(),
      phone: emptyToNull(v.phone),
      whatsapp: emptyToNull(v.whatsapp),
      email: emptyToNull(v.email),
      originId: v.originId,
      responsiblePersonId: emptyToNull(v.responsibleId),
      initialNote: emptyToNull(v.initialNote),
    };

    this.leads.create(payload).subscribe({
      next: (created) => {
        this.leaving = true;
        this.unsaved.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Lead criado',
          detail: `${created.name} cadastrado com sucesso.`,
        });
        this.router.navigateByUrl('/');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.handleError(err);
      },
    });
  }

  private handleError(err: HttpErrorResponse): void {
    const body = err.error as { message?: string; fields?: Record<string, string> } | null;
    if (body?.fields) {
      this.fieldErrors.set(body.fields);
    }
    this.formError.set(body?.message ?? 'Não foi possível criar o lead.');
  }
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
