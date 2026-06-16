import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { LeadService, LeadStatus, PendingItem, PendingReason } from '../../../core/api/lead.service';

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Novo',
  CONTACTED: 'Em contato',
  QUALIFIED: 'Qualificado',
  LOST: 'Perdido',
};

const REASON_LABELS: Record<PendingReason, string> = {
  UNASSIGNED: 'Sem responsável',
  NEW_WITHOUT_INTERACTION: 'Sem interação',
  OVERDUE_NEXT_CONTACT: 'Contato atrasado',
  CONTACTED_WITHOUT_OUTCOME: 'Sem desfecho',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const REASON_SEVERITY: Record<PendingReason, TagSeverity> = {
  UNASSIGNED: 'warn',
  NEW_WITHOUT_INTERACTION: 'info',
  OVERDUE_NEXT_CONTACT: 'danger',
  CONTACTED_WITHOUT_OUTCOME: 'secondary',
};

/** Operational pending-items worklist: leads that need action, tagged with the reasons why. */
@Component({
  selector: 'app-lead-pending',
  imports: [DatePipe, RouterLink, TableModule, TagModule, MessageModule],
  templateUrl: './lead-pending.html',
  styleUrl: './lead-pending.css',
})
export class LeadPending implements OnInit {
  private readonly leads = inject(LeadService);

  protected readonly items = signal<PendingItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = 20;
  protected firstRow = 0;

  ngOnInit(): void {
    // The lazy table fires the first load.
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

  protected reasonLabel(reason: PendingReason): string {
    return REASON_LABELS[reason];
  }

  protected reasonSeverity(reason: PendingReason): TagSeverity {
    return REASON_SEVERITY[reason];
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.firstRow = event.first ?? 0;
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.leads.pending(this.firstRow / this.rows, this.rows).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set('Não foi possível carregar as pendências.');
      },
    });
  }
}
