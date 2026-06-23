import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { WorkflowService, WorkflowSummary } from '../../../core/api/workflow.service';

/**
 * Lists the configurable workflows (a card per definition) and links to the visual editor. Gated by
 * {@code workflow:manage}: a user without it gets a clear permission-denied state (the backend is the
 * authority).
 */
@Component({
  selector: 'app-workflow-list',
  imports: [ButtonModule, CardModule, MessageModule],
  templateUrl: './workflow-list.html',
  styleUrl: './workflow-list.css',
})
export class WorkflowList implements OnInit {
  private readonly workflows = inject(WorkflowService);
  private readonly router = inject(Router);

  protected readonly items = signal<WorkflowSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.workflows.list().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para administrar workflows.'
            : 'Não foi possível carregar os workflows.',
        );
      },
    });
  }

  protected open(code: string): void {
    this.router.navigateByUrl('/fluxos/' + code);
  }
}
