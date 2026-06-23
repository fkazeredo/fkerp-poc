import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { GraphComponent } from '@swimlane/ngx-graph';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkflowEditor } from './workflow-editor';
import { WorkflowDetail, WorkflowService } from '../../../core/api/workflow.service';
import { AuthService } from '../../../core/auth/auth.service';

// jsdom has no SVG layout; stub what ngx-graph measures so it can render without throwing.
(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
const svgProto =
  typeof SVGElement !== 'undefined'
    ? (SVGElement.prototype as unknown as Record<string, unknown>)
    : null;
if (svgProto) {
  svgProto['getBBox'] ??= () => ({ x: 0, y: 0, width: 100, height: 50 });
  svgProto['getScreenCTM'] ??= () => ({ a: 1, b: 0, c: 0, d: 1, e: 0, f: 0, inverse: () => ({}) });
  svgProto['getComputedTextLength'] ??= () => 80;
}

describe('WorkflowEditor', () => {
  const workflows = {
    detail: vi.fn(),
    catalog: vi.fn(),
    updateState: vi.fn(),
    createAttentionRule: vi.fn(),
    updateAttentionRule: vi.fn(),
    deleteAttentionRule: vi.fn(),
  };
  const messages = { add: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const auth = { canManageWorkflows: vi.fn().mockReturnValue(true) };

  const detail = (): WorkflowDetail => ({
    code: 'opportunity',
    label: 'Oportunidade',
    states: [
      { id: 'st-new', code: 'NEW_OPPORTUNITY', label: 'Nova', category: 'INITIAL', sortOrder: 1, active: true, system: true },
      { id: 'st-disc', code: 'DISCOVERY', label: 'Descoberta', category: 'INTERMEDIATE', sortOrder: 2, active: true, system: true },
      { id: 'st-lost', code: 'LOST', label: 'Perdida', category: 'TERMINAL', sortOrder: 9, active: true, system: true },
    ],
    transitions: [
      { id: 'tr-1', code: 'ADVANCE', label: 'Avançar', fromState: 'NEW_OPPORTUNITY', toState: 'DISCOVERY', trigger: 'USER', system: true, rules: [{ id: 'ru-1', kind: 'GUARD', ruleKey: 'strictFunnel', params: null, sortOrder: 1, system: true }] },
    ],
    attentionRules: [
      { id: 'ar-sys', conditionKey: 'NO_RECENT_ACTIVITY', thresholdDays: 14, stateValue: null, code: 'WITHOUT_RECENT_ACTIVITY', label: 'Sem atividade recente', sortOrder: 1, active: true, system: true },
      { id: 'ar-cust', conditionKey: 'IN_STATE', thresholdDays: null, stateValue: 'DISCOVERY', code: 'CUSTOM', label: 'Regra custom', sortOrder: 2, active: true, system: false },
    ],
  });

  const catalog = () => ({
    attentionConditions: {
      opportunity: [
        { conditionKey: 'NO_RECENT_ACTIVITY', usesDays: true, usesState: false },
        { conditionKey: 'IN_STATE', usesDays: false, usesState: true },
      ],
    },
    guardKeys: ['strictFunnel'],
    postFunctionKeys: [],
  });

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [WorkflowEditor],
      providers: [
        providePrimeNG(),
        MessageService,
        ConfirmationService,
        { provide: WorkflowService, useValue: workflows },
        { provide: MessageService, useValue: messages },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'opportunity' } } } },
      ],
    });
  }

  function build() {
    configure();
    const comp = TestBed.createComponent(WorkflowEditor).componentInstance;
    comp.ngOnInit();
    return comp;
  }

  beforeEach(() => {
    Object.values(workflows).forEach((fn) => fn.mockReset());
    messages.add.mockReset();
    router.navigateByUrl.mockReset();
    workflows.detail.mockReturnValue(of(detail()));
    workflows.catalog.mockReturnValue(of(catalog()));
    workflows.updateState.mockReturnValue(of(undefined));
    workflows.createAttentionRule.mockReturnValue(of({ id: 'new-rule' }));
    workflows.updateAttentionRule.mockReturnValue(of(undefined));
    workflows.deleteAttentionRule.mockReturnValue(of(undefined));
  });

  it('loads the workflow and maps states→nodes and transitions→edges', () => {
    const comp = build();
    expect(comp['workflow']()?.code).toBe('opportunity');
    expect(comp['nodes']()).toHaveLength(3);
    expect(comp['nodes']()[0]).toMatchObject({ id: 's_NEW_OPPORTUNITY', label: 'Nova' });
    expect(comp['links']()).toHaveLength(1);
    expect(comp['links']()[0]).toMatchObject({ source: 's_NEW_OPPORTUNITY', target: 's_DISCOVERY', label: 'Avançar' });
  });

  it('shows a permission message on 403', () => {
    workflows.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    expect(comp['error']()).toContain('permissão');
  });

  it('opens the state panel on a node click and saves the edited label', () => {
    const comp = build();
    comp['onNodeClick']({ id: 's_DISCOVERY', label: 'Descoberta', data: { state: detail().states[1] } });
    expect(comp['selectedState']()?.code).toBe('DISCOVERY');

    comp['stateLabel'] = 'Descoberta (editada)';
    comp['stateSortOrder'] = 5;
    comp['stateActive'] = false;
    comp['saveState']();

    expect(workflows.updateState).toHaveBeenCalledWith('st-disc', {
      label: 'Descoberta (editada)',
      sortOrder: 5,
      active: false,
    });
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('requires a non-empty state label', () => {
    const comp = build();
    comp['onNodeClick']({ id: 's_DISCOVERY', label: 'Descoberta', data: { state: detail().states[1] } });
    comp['stateLabel'] = '   ';
    expect(comp['canSaveState']()).toBe(false);
  });

  it('creates a custom attention rule with the chosen condition parameters', () => {
    const comp = build();
    comp['openNewRule']();
    comp['ruleConditionKey'] = 'NO_RECENT_ACTIVITY'; // usesDays
    comp['ruleCode'] = 'MY_REASON';
    comp['ruleLabel'] = 'Meu motivo';
    comp['ruleThresholdDays'] = 10;
    comp['ruleSortOrder'] = 3;
    expect(comp['canSaveRule']()).toBe(true);

    comp['saveRule']();

    expect(workflows.createAttentionRule).toHaveBeenCalledWith('opportunity', {
      conditionKey: 'NO_RECENT_ACTIVITY',
      thresholdDays: 10,
      stateValue: null,
      code: 'MY_REASON',
      label: 'Meu motivo',
      sortOrder: 3,
    });
  });

  it('requires a state value when the chosen condition uses one', () => {
    const comp = build();
    comp['openNewRule']();
    comp['ruleConditionKey'] = 'IN_STATE'; // usesState
    comp['ruleCode'] = 'CODE';
    comp['ruleLabel'] = 'X';
    comp['ruleStateValue'] = '';
    expect(comp['canSaveRule']()).toBe(false);
    comp['ruleStateValue'] = 'DISCOVERY';
    expect(comp['canSaveRule']()).toBe(true);
  });

  it('rejects a lowercase reason code', () => {
    const comp = build();
    comp['openNewRule']();
    comp['ruleConditionKey'] = 'NO_RECENT_ACTIVITY';
    comp['ruleThresholdDays'] = 5;
    comp['ruleLabel'] = 'X';
    comp['ruleCode'] = 'lower_case';
    expect(comp['canSaveRule']()).toBe(false);
  });

  it('updates an existing rule (condition and code stay immutable)', () => {
    const comp = build();
    comp['openRule'](detail().attentionRules[1]);
    comp['ruleLabel'] = 'Regra renomeada';
    comp['ruleActive'] = false;
    comp['saveRule']();
    expect(workflows.updateAttentionRule).toHaveBeenCalledWith('ar-cust', {
      label: 'Regra renomeada',
      thresholdDays: null,
      sortOrder: 2,
      active: false,
    });
  });

  it('deletes a custom rule', () => {
    const comp = build();
    comp['deleteRule'](detail().attentionRules[1]);
    expect(workflows.deleteAttentionRule).toHaveBeenCalledWith('ar-cust');
  });

  it('flags unsaved changes only after the open panel is edited', () => {
    const comp = build();
    expect(comp.hasUnsavedChanges()).toBe(false);
    comp['onNodeClick']({ id: 's_DISCOVERY', label: 'Descoberta', data: { state: detail().states[1] } });
    expect(comp.hasUnsavedChanges()).toBe(false); // just opened, untouched
    comp['stateLabel'] = 'changed';
    expect(comp.hasUnsavedChanges()).toBe(true);
  });

  describe('DOM', () => {
    function render() {
      configure();
      // The d3/dagre graph needs a real browser layout engine (verified by E2E). For the jsdom DOM tests,
      // stub <ngx-graph> out so the panels, attention-rule list and system-lock can be asserted.
      TestBed.overrideComponent(WorkflowEditor, {
        remove: { imports: [GraphComponent] },
        add: { schemas: [NO_ERRORS_SCHEMA] },
      });
      const fixture = TestBed.createComponent(WorkflowEditor);
      fixture.componentInstance.ngOnInit();
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    it('renders the workflow label, the graph element and the attention rules', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Oportunidade');
      expect(el.querySelector('ngx-graph')).not.toBeNull();
      expect(el.textContent).toContain('Sem atividade recente');
      expect(el.textContent).toContain('Regra custom');
    });

    it('hides the delete control for a system rule but shows it for a custom rule', () => {
      const el = render();
      const items = el.querySelectorAll('.rule-list li');
      expect(items).toHaveLength(2);
      // system rule (first) has no delete button; custom rule (second) does.
      expect(items[0].querySelector('button.p-button-danger, .pi-trash')).toBeNull();
      expect(items[1].querySelector('.pi-trash')).not.toBeNull();
    });
  });
});
