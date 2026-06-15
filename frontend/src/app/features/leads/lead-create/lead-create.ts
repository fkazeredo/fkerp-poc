import {
  Component,
  inject,
  signal,
  viewChild,
  AfterViewInit,
  ElementRef,
  OnInit,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { CreateLead, LeadService, Origin } from '../../../core/api/lead.service';

/** Reactive form to register a new lead, including the optional first note. */
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
export class LeadCreate implements OnInit, AfterViewInit {
  private readonly fb = inject(FormBuilder);
  private readonly leads = inject(LeadService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');

  protected readonly origins = signal<Origin[]>([]);
  protected readonly loading = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    originId: ['', Validators.required],
    phone: [''],
    whatsapp: [''],
    email: ['', Validators.email],
    initialNote: [''],
  });

  ngOnInit(): void {
    this.leads.origins().subscribe({
      next: (list) => this.origins.set(list),
      error: () => this.formError.set('Não foi possível carregar as origens.'),
    });
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.nameInput()?.nativeElement.focus());
  }

  protected fieldError(name: string): string | null {
    return this.fieldErrors()[name] ?? null;
  }

  /** Discards the entry and returns to the home screen. */
  protected cancel(): void {
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
      responsiblePersonId: null,
      initialNote: emptyToNull(v.initialNote),
    };

    this.leads.create(payload).subscribe({
      next: (created) => {
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
