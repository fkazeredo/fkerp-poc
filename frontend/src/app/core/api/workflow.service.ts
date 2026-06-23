import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** A configurable workflow in the admin list. */
export interface WorkflowSummary {
  code: string;
  label: string;
}

/** A catalog attention-condition: its key and which parameters it uses. */
export interface ConditionDescriptor {
  conditionKey: string;
  usesDays: boolean;
  usesState: boolean;
}

/** The authoring catalog: attention conditions per workflow + the transition guard / post-function keys. */
export interface WorkflowCatalog {
  attentionConditions: Record<string, ConditionDescriptor[]>;
  guardKeys: string[];
  postFunctionKeys: string[];
}

/** A workflow state — a diagram node. */
export interface WorkflowStateView {
  id: string;
  code: string;
  label: string;
  category: string;
  sortOrder: number;
  active: boolean;
  system: boolean;
}

/** A transition rule attached to a transition (read-only display). */
export interface WorkflowRuleView {
  id: string;
  kind: string;
  ruleKey: string;
  params: string | null;
  sortOrder: number;
  system: boolean;
}

/** A workflow transition — a diagram edge — with its attached rules. */
export interface WorkflowTransitionView {
  id: string;
  code: string;
  label: string;
  fromState: string;
  toState: string;
  trigger: string;
  system: boolean;
  rules: WorkflowRuleView[];
}

/** A configurable attention rule — a pending-items worklist reason. */
export interface WorkflowAttentionRuleView {
  id: string;
  conditionKey: string;
  thresholdDays: number | null;
  stateValue: string | null;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
  system: boolean;
}

/** The full editor read model of a workflow: states (nodes), transitions (edges) and attention rules. */
export interface WorkflowDetail {
  code: string;
  label: string;
  states: WorkflowStateView[];
  transitions: WorkflowTransitionView[];
  attentionRules: WorkflowAttentionRuleView[];
}

/** Body to create a custom attention rule. */
export interface CreateAttentionRule {
  conditionKey: string;
  thresholdDays: number | null;
  stateValue: string | null;
  code: string;
  label: string;
  sortOrder: number;
}

/** Body to update an attention rule (code and condition stay immutable). */
export interface UpdateAttentionRule {
  label: string;
  thresholdDays: number | null;
  sortOrder: number;
  active: boolean;
}

/** Body to update a state's editable attributes (code and category stay as seeded). */
export interface UpdateState {
  label: string;
  sortOrder: number;
  active: boolean;
}

/**
 * API client for the workflow administration surface ({@code /api/workflows}, gated by {@code workflow:manage}).
 * Drives the visual editor: lists the configurable workflows, loads a workflow's full topology (states,
 * transitions and the attention rules that feed the pending-items worklists), and edits the editable
 * attributes — a state's label/order/active and the attention rules (create/update/delete) — respecting the
 * {@code system} lock the read models expose.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly http = inject(HttpClient);

  list(): Observable<WorkflowSummary[]> {
    return this.http.get<WorkflowSummary[]>('/api/workflows');
  }

  catalog(): Observable<WorkflowCatalog> {
    return this.http.get<WorkflowCatalog>('/api/workflows/catalog');
  }

  detail(code: string): Observable<WorkflowDetail> {
    return this.http.get<WorkflowDetail>(`/api/workflows/${code}`);
  }

  createAttentionRule(code: string, body: CreateAttentionRule): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`/api/workflows/${code}/attention-rules`, body);
  }

  updateAttentionRule(id: string, body: UpdateAttentionRule): Observable<void> {
    return this.http.put<void>(`/api/workflows/attention-rules/${id}`, body);
  }

  deleteAttentionRule(id: string): Observable<void> {
    return this.http.delete<void>(`/api/workflows/attention-rules/${id}`);
  }

  updateState(id: string, body: UpdateState): Observable<void> {
    return this.http.put<void>(`/api/workflows/states/${id}`, body);
  }
}
