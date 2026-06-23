import {
  Component,
  DestroyRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import {
  ConditionDescriptor,
  WorkflowAttentionRuleView,
  WorkflowCatalog,
  WorkflowDetail,
  WorkflowService,
  WorkflowStateView,
  WorkflowTransitionView,
} from '../../../core/api/workflow.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  HasUnsavedChanges,
  UnsavedChangesService,
} from '../../../core/forms/unsaved-changes.service';
import { Diagram, buildDiagram } from './workflow-diagram';

type TagSeverity = 'success' | 'info' | 'warn' | 'secondary' | 'contrast' | 'danger';

const CATEGORY_LABELS: Record<string, string> = {
  INITIAL: 'Inicial',
  ACTIVE: 'Em andamento',
  TERMINAL_POSITIVE: 'Sucesso',
  TERMINAL_NEGATIVE: 'Encerrado',
};

/** What the side panel is editing. */
type Selection =
  | { kind: 'state'; code: string }
  | { kind: 'transition'; id: string }
  | { kind: 'attention-new' }
  | { kind: 'attention'; id: string }
  | null;

/**
 * Visual workflow editor: renders a configurable workflow as a clean, hand-rolled **SVG flowchart** (states =
 * nodes laid out left-to-right by flow depth with terminal states on a lower row, transitions = arrows) and
 * edits the editable attributes — a state's label/order/active and the attention rules that drive the
 * pending-items worklists (create, edit, delete) — respecting the {@code system} lock the read models expose.
 * Gated by {@code workflow:manage}; the backend stays the only authority. Transitions and their rules are shown
 * read-only (the workflow topology is coded structure, not data the admin edits here). The layout is computed in
 * TypeScript (no library), so it renders identically in the browser and in jsdom tests.
 */
@Component({
  selector: 'app-workflow-editor',
  imports: [
    FormsModule,
    ButtonModule,
    CardModule,
    TagModule,
    SelectModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    MessageModule,
  ],
  templateUrl: './workflow-editor.html',
  styleUrl: './workflow-editor.css',
})
export class WorkflowEditor implements OnInit, OnDestroy, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly workflows = inject(WorkflowService);
  private readonly messages = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly unsaved = inject(UnsavedChangesService);

  protected readonly workflow = signal<WorkflowDetail | null>(null);
  protected readonly catalog = signal<WorkflowCatalog | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly acting = signal(false);

  /** The computed SVG diagram (positioned nodes + edges + viewBox), derived from the workflow topology. */
  protected readonly diagram = computed<Diagram>(() => buildDiagram(this.workflow()));

  protected readonly selection = signal<Selection>(null);

  // State edit form.
  protected stateLabel = '';
  protected stateSortOrder = 0;
  protected stateActive = true;

  // Attention-rule form (create + edit share these fields).
  protected ruleConditionKey: string | null = null;
  protected ruleThresholdDays: number | null = null;
  protected ruleStateValue = '';
  protected ruleCode = '';
  protected ruleLabel = '';
  protected ruleSortOrder = 0;
  protected ruleActive = true;

  private snapshot = '';
  private code = '';

  constructor() {
    effect(() => this.unsaved.set(this.hasUnsavedChanges()));
  }

  ngOnInit(): void {
    this.workflows.catalog().subscribe({ next: (c) => this.catalog.set(c) });
    // React to the :code param so navigating between workflows (e.g. via the sidebar sub-items) reloads the
    // editor. The router reuses this component across same-route param changes, so ngOnInit runs only once —
    // reading route.snapshot alone would keep showing the first workflow opened.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.code = params.get('code') ?? '';
      this.selection.set(null);
      this.load();
    });
  }

  ngOnDestroy(): void {
    this.unsaved.set(false);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.workflows.detail(this.code).subscribe({
      next: (detail) => {
        this.workflow.set(detail);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 403
            ? 'Você não tem permissão para administrar workflows.'
            : err.status === 404
              ? 'Workflow não encontrado.'
              : 'Não foi possível carregar o workflow.',
        );
      },
    });
  }

  protected categoryLabel(category: string): string {
    return CATEGORY_LABELS[category] ?? category;
  }

  protected categorySeverity(category: string): TagSeverity {
    switch (category) {
      case 'INITIAL':
        return 'info';
      case 'TERMINAL_POSITIVE':
        return 'success';
      case 'TERMINAL_NEGATIVE':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  /** The contextual title of the edit panel, so the user always knows what they are editing. */
  protected panelTitle(): string {
    const sel = this.selection();
    switch (sel?.kind) {
      case 'state':
        return 'Editar estado · ' + (this.selectedState()?.label ?? '');
      case 'transition':
        return 'Transição';
      case 'attention':
        return 'Editar regra de atenção';
      case 'attention-new':
        return 'Nova regra de atenção';
      default:
        return 'Edição';
    }
  }

  /** Whether the given state is the currently selected one (for the node highlight). */
  protected isStateSelected(code: string): boolean {
    const sel = this.selection();
    return sel?.kind === 'state' && sel.code === code;
  }

  /** The condition descriptors available for this workflow (drives the new-rule form). */
  protected conditionOptions(): ConditionDescriptor[] {
    return this.catalog()?.attentionConditions[this.code] ?? [];
  }

  /** The chosen condition's descriptor (which parameters it uses). */
  protected chosenCondition(): ConditionDescriptor | undefined {
    return this.conditionOptions().find((c) => c.conditionKey === this.ruleConditionKey);
  }

  protected selectedState(): WorkflowStateView | null {
    const sel = this.selection();
    if (sel?.kind !== 'state') {
      return null;
    }
    return this.workflow()?.states.find((s) => s.code === sel.code) ?? null;
  }

  protected selectedTransition(): WorkflowTransitionView | null {
    const sel = this.selection();
    if (sel?.kind !== 'transition') {
      return null;
    }
    return this.workflow()?.transitions.find((t) => t.id === sel.id) ?? null;
  }

  protected editingRule(): WorkflowAttentionRuleView | null {
    const sel = this.selection();
    if (sel?.kind !== 'attention') {
      return null;
    }
    return this.workflow()?.attentionRules.find((r) => r.id === sel.id) ?? null;
  }

  /** Opens the panel for a clicked state node. */
  protected onNodeClick(state: WorkflowStateView): void {
    this.stateLabel = state.label;
    this.stateSortOrder = state.sortOrder;
    this.stateActive = state.active;
    this.selection.set({ kind: 'state', code: state.code });
    this.snapshot = this.liveSnapshot();
  }

  /** Opens the (read-only) panel for a clicked transition edge. */
  protected onEdgeClick(transition: WorkflowTransitionView): void {
    this.selection.set({ kind: 'transition', id: transition.id });
    this.snapshot = this.liveSnapshot();
  }

  protected openNewRule(): void {
    // The condition is an explicit choice (consistent with the other cadastro-backed selects); the relevant
    // parameter fields appear once it is picked.
    this.ruleConditionKey = null;
    this.ruleThresholdDays = null;
    this.ruleStateValue = '';
    this.ruleCode = '';
    this.ruleLabel = '';
    this.ruleSortOrder = (this.workflow()?.attentionRules.length ?? 0) + 1;
    this.ruleActive = true;
    this.selection.set({ kind: 'attention-new' });
    this.snapshot = this.liveSnapshot();
  }

  protected openRule(rule: WorkflowAttentionRuleView): void {
    this.ruleConditionKey = rule.conditionKey;
    this.ruleThresholdDays = rule.thresholdDays;
    this.ruleStateValue = rule.stateValue ?? '';
    this.ruleCode = rule.code;
    this.ruleLabel = rule.label;
    this.ruleSortOrder = rule.sortOrder;
    this.ruleActive = rule.active;
    this.selection.set({ kind: 'attention', id: rule.id });
    this.snapshot = this.liveSnapshot();
  }

  protected async closePanel(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.unsaved.confirmDiscard())) {
      return;
    }
    this.selection.set(null);
  }

  protected canSaveState(): boolean {
    return this.stateLabel.trim().length > 0;
  }

  protected saveState(): void {
    const state = this.selectedState();
    if (!state || !this.canSaveState()) {
      return;
    }
    this.acting.set(true);
    this.workflows
      .updateState(state.id, {
        label: this.stateLabel.trim(),
        sortOrder: this.stateSortOrder,
        active: this.stateActive,
      })
      .subscribe({
        next: () => this.afterWrite('Estado atualizado'),
        error: (err: HttpErrorResponse) => this.onError(err),
      });
  }

  protected canSaveRule(): boolean {
    const cond = this.chosenCondition();
    return (
      !!this.ruleConditionKey &&
      !!cond &&
      this.ruleLabel.trim().length > 0 &&
      /^[A-Z0-9_]+$/.test(this.ruleCode.trim()) &&
      (!cond.usesDays || this.ruleThresholdDays != null) &&
      (!cond.usesState || this.ruleStateValue.trim().length > 0)
    );
  }

  protected saveRule(): void {
    if (!this.canSaveRule()) {
      return;
    }
    const sel = this.selection();
    this.acting.set(true);
    const cond = this.chosenCondition()!;
    if (sel?.kind === 'attention') {
      this.workflows
        .updateAttentionRule(sel.id, {
          label: this.ruleLabel.trim(),
          thresholdDays: cond.usesDays ? this.ruleThresholdDays : null,
          sortOrder: this.ruleSortOrder,
          active: this.ruleActive,
        })
        .subscribe({
          next: () => this.afterWrite('Regra atualizada'),
          error: (err: HttpErrorResponse) => this.onError(err),
        });
    } else {
      this.workflows
        .createAttentionRule(this.code, {
          conditionKey: this.ruleConditionKey!,
          thresholdDays: cond.usesDays ? this.ruleThresholdDays : null,
          stateValue: cond.usesState ? this.ruleStateValue.trim() : null,
          code: this.ruleCode.trim(),
          label: this.ruleLabel.trim(),
          sortOrder: this.ruleSortOrder,
        })
        .subscribe({
          next: () => this.afterWrite('Regra criada'),
          error: (err: HttpErrorResponse) => this.onError(err),
        });
    }
  }

  protected deleteRule(rule: WorkflowAttentionRuleView): void {
    this.acting.set(true);
    this.workflows.deleteAttentionRule(rule.id).subscribe({
      next: () => this.afterWrite('Regra removida'),
      error: (err: HttpErrorResponse) => this.onError(err),
    });
  }

  private afterWrite(summary: string): void {
    this.acting.set(false);
    this.selection.set(null);
    this.snapshot = '';
    this.messages.add({ severity: 'success', summary });
    this.load();
  }

  private onError(err: HttpErrorResponse): void {
    this.acting.set(false);
    const body = err.error as { message?: string } | null;
    this.messages.add({
      severity: 'error',
      summary: 'Erro',
      detail: body?.message ?? 'Não foi possível concluir a ação.',
    });
  }

  private liveSnapshot(): string {
    return JSON.stringify([
      this.selection(),
      this.stateLabel,
      this.stateSortOrder,
      this.stateActive,
      this.ruleConditionKey,
      this.ruleThresholdDays,
      this.ruleStateValue,
      this.ruleCode,
      this.ruleLabel,
      this.ruleSortOrder,
      this.ruleActive,
    ]);
  }

  hasUnsavedChanges(): boolean {
    const sel = this.selection();
    const editing = sel?.kind === 'state' || sel?.kind === 'attention' || sel?.kind === 'attention-new';
    return editing && this.snapshot !== '' && this.snapshot !== this.liveSnapshot();
  }

  protected back(): void {
    this.router.navigateByUrl('/fluxos');
  }

  /** Esc closes the open panel (guarded) or returns to the list. */
  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.selection()) {
      void this.closePanel();
    } else {
      this.back();
    }
  }
}
