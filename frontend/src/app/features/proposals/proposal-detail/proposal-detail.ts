import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProposalDetail, ProposalService, ProposalStatus } from '../../../core/api/proposal.service';
import { OpportunityStage } from '../../../core/api/opportunity.service';

const STATUS_LABELS: Record<ProposalStatus, string> = {
  DRAFT: 'Rascunho',
  READY_FOR_REVIEW: 'Pronta para revisão',
  APPROVED: 'Aprovada',
  SENT: 'Enviada',
  ACCEPTED: 'Aceita',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

const STAGE_LABELS: Record<OpportunityStage, string> = {
  NEW_OPPORTUNITY: 'Nova',
  DISCOVERY: 'Descoberta',
  PRODUCT_FIT: 'Aderência',
  READY_FOR_PROPOSAL: 'Pronta p/ proposta',
  LOST: 'Perdida',
};

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_SEVERITY: Record<ProposalStatus, TagSeverity> = {
  DRAFT: 'secondary',
  READY_FOR_REVIEW: 'info',
  APPROVED: 'info',
  SENT: 'warn',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'danger',
  CANCELLED: 'danger',
};

/**
 * Proposal detail page (Sales & Proposals): the commercial offer's summary, its status, and the source
 * Opportunity (and Lead) kept traceable. Read-only in this slice — the lifecycle transitions and the
 * proposal items/values are later slices. Shows commercial-offer data only — never sale/order/booking/
 * financial data.
 */
@Component({
  selector: 'app-proposal-detail',
  imports: [DatePipe, RouterLink, ButtonModule, CardModule, TagModule, MessageModule],
  templateUrl: './proposal-detail.html',
  styleUrl: './proposal-detail.css',
})
export class ProposalDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly proposals = inject(ProposalService);

  protected readonly proposal = signal<ProposalDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private proposalId = '';

  ngOnInit(): void {
    this.proposalId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected statusLabel(status: ProposalStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: ProposalStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  protected stageLabel(stage: OpportunityStage): string {
    return STAGE_LABELS[stage];
  }

  protected back(): void {
    this.router.navigateByUrl('/propostas');
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.proposals.detail(this.proposalId).subscribe({
      next: (detail) => {
        this.proposal.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para ver esta proposta.'
            : err.status === 404
              ? 'Proposta não encontrada.'
              : 'Não foi possível carregar a proposta.',
        );
      },
    });
  }
}
