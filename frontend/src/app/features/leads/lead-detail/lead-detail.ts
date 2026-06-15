import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { Observable } from 'rxjs';
import { LeadDetail, LeadService, LeadStatus, Responsible } from '../../../core/api/lead.service';
import { ReferenceItem, ReferenceService } from '../../../core/api/reference.service';

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Novo',
  CONTACTED: 'Em contato',
  QUALIFIED: 'Qualificado',
  LOST: 'Perdido',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

/** Lead detail page: core data + commercial history, with qualify / lose / reassign actions. */
@Component({
  selector: 'app-lead-detail',
  imports: [
    DatePipe,
    FormsModule,
    ButtonModule,
    CardModule,
    TagModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    MessageModule,
  ],
  templateUrl: './lead-detail.html',
  styleUrl: './lead-detail.css',
})
export class LeadDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly leads = inject(LeadService);
  private readonly references = inject(ReferenceService);
  private readonly messages = inject(MessageService);

  protected readonly lead = signal<LeadDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly qualifyOpen = signal(false);
  protected readonly loseOpen = signal(false);
  protected readonly reassignOpen = signal(false);
  protected readonly acting = signal(false);

  protected readonly lossReasons = signal<ReferenceItem[]>([]);
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  protected qualifyNote = '';
  protected lossReasonId: string | null = null;
  protected lossNote = '';
  protected reassignTo: string | null = null;

  private leadId = '';

  ngOnInit(): void {
    this.leadId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: LeadStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: LeadStatus): TagSeverity {
    switch (status) {
      case 'NEW':
        return 'info';
      case 'CONTACTED':
        return 'warn';
      case 'QUALIFIED':
        return 'success';
      case 'LOST':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  protected canQualify(): boolean {
    const status = this.lead()?.status;
    return status === 'NEW' || status === 'CONTACTED';
  }

  protected canLose(): boolean {
    const status = this.lead()?.status;
    return !!status && status !== 'LOST';
  }

  protected canReassign(): boolean {
    const status = this.lead()?.status;
    return !!status && status !== 'LOST';
  }

  protected back(): void {
    this.router.navigateByUrl('/leads');
  }

  protected openQualify(): void {
    this.qualifyNote = '';
    this.qualifyOpen.set(true);
  }

  protected openLose(): void {
    this.lossReasonId = null;
    this.lossNote = '';
    if (this.lossReasons().length === 0) {
      this.references
        .list('loss-reasons')
        .subscribe({ next: (list) => this.lossReasons.set(list) });
    }
    this.loseOpen.set(true);
  }

  protected openReassign(): void {
    this.reassignTo = this.lead()?.responsibleId ?? null;
    if (this.responsibleOptions().length === 0) {
      this.leads.responsibles().subscribe({ next: (list) => this.responsibleOptions.set(list) });
    }
    this.reassignOpen.set(true);
  }

  protected confirmQualify(): void {
    this.act(
      this.leads.qualify(this.leadId, this.qualifyNote || null),
      'Lead qualificado',
      this.qualifyOpen,
    );
  }

  protected confirmLose(): void {
    if (!this.lossReasonId) {
      return;
    }
    this.act(
      this.leads.lose(this.leadId, this.lossReasonId, this.lossNote || null),
      'Lead marcado como perdido',
      this.loseOpen,
    );
  }

  protected confirmReassign(): void {
    this.act(
      this.leads.reassign(this.leadId, this.reassignTo),
      'Responsável atualizado',
      this.reassignOpen,
    );
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.leads.detail(this.leadId).subscribe({
      next: (detail) => {
        this.lead.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver este lead.'
            : err.status === 404
              ? 'Lead não encontrado.'
              : 'Não foi possível carregar o lead.',
        );
      },
    });
  }

  private act(
    action: Observable<LeadDetail>,
    successSummary: string,
    dialog: { set: (v: boolean) => void },
  ): void {
    this.acting.set(true);
    action.subscribe({
      next: (detail) => {
        this.lead.set(detail);
        this.acting.set(false);
        dialog.set(false);
        this.messages.add({ severity: 'success', summary: successSummary });
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        const body = err.error as { message?: string } | null;
        this.messages.add({
          severity: 'error',
          summary: 'Erro',
          detail: body?.message ?? 'Não foi possível concluir a ação.',
        });
      },
    });
  }
}
