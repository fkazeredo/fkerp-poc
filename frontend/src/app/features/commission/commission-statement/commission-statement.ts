import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../../core/auth/auth.service';
import { Responsible } from '../../../core/api/lead.service';
import {
  CommissionListItem,
  CommissionService,
  CommissionStatement,
  CommissionStatus,
} from '../../../core/api/commission.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const STATUS_LABELS: Record<CommissionStatus, string> = {
  EXPECTED: 'Prevista',
  ELIGIBLE: 'Pendente de aprovação',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  PAID: 'Paga',
  CANCELLED: 'Cancelada',
};

const STATUS_SEVERITY: Record<CommissionStatus, TagSeverity> = {
  EXPECTED: 'info',
  ELIGIBLE: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
  PAID: 'success',
  CANCELLED: 'secondary',
};

/**
 * Simple commission statement by beneficiary (Commission Management): an informational read view grouping a
 * beneficiary's commission entries over an optional period with per-status totals (expected / eligible / approved /
 * paid). It respects visibility — a seller/representative sees only their own statement (the beneficiary picker is
 * locked to them); a manager/finance can pick any beneficiary. It approves/pays nothing and shows commission +
 * commercial-origin data only — never payroll, tax or accounting data.
 */
@Component({
  selector: 'app-commission-statement',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    CardModule,
    TableModule,
    TagModule,
    SelectModule,
    DatePickerModule,
    ToggleSwitchModule,
    ButtonModule,
    MessageModule,
  ],
  templateUrl: './commission-statement.html',
  styleUrl: './commission-statement.css',
})
export class CommissionStatementPage implements OnInit {
  private readonly commissions = inject(CommissionService);
  protected readonly auth = inject(AuthService);

  protected readonly statement = signal<CommissionStatement | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly responsibleOptions = signal<Responsible[]>([]);

  /** Managers can choose any beneficiary; own-tier users are locked to themselves. */
  protected readonly canChooseBeneficiary = computed(() => this.auth.canSeeAllCommissions());

  protected beneficiary: string | null = null;
  protected from: Date | null = null;
  protected to: Date | null = null;
  protected includeVoided = false;

  ngOnInit(): void {
    this.beneficiary = this.auth.userId();
    if (this.canChooseBeneficiary()) {
      this.commissions.responsibles().subscribe({
        next: (list) => this.responsibleOptions.set(list),
      });
    }
    this.load();
  }

  protected statusLabel(status: CommissionStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusSeverity(status: CommissionStatus): TagSeverity {
    return STATUS_SEVERITY[status];
  }

  /** The human-friendly source order code (PC-000n). */
  protected orderCode(n: number): string {
    return 'PC-' + String(n).padStart(4, '0');
  }

  /** Reloads the statement for the chosen beneficiary + period. */
  protected apply(): void {
    this.load();
  }

  private load(): void {
    if (!this.beneficiary) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.commissions
      .statement(this.beneficiary, toIsoDate(this.from), toIsoDate(this.to), this.includeVoided)
      .subscribe({
        next: (s) => {
          this.statement.set(s);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.statement.set(null);
          this.error.set(
            err.status === 403
              ? 'Você só pode ver o seu próprio extrato.'
              : 'Não foi possível carregar o extrato de comissões.',
          );
        },
      });
  }

  protected trackEntry(_: number, entry: CommissionListItem): string {
    return entry.id;
  }
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
