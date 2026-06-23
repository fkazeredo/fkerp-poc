import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkflowEditor } from './workflow-editor';
import { WorkflowDetail, WorkflowService, WorkflowStateView } from '../../../core/api/workflow.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

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

  const routeParam = (code: string) => ({ get: (k: string) => (k === 'code' ? code : null) });
  let paramMap$: BehaviorSubject<ReturnType<typeof routeParam>>;

  const state = (over: Partial<WorkflowStateView>): WorkflowStateView => ({
    id: 'st',
    code: 'X',
    label: 'X',
    category: 'ACTIVE',
    sortOrder: 1,
    active: true,
    system: true,
    ...over,
  });

  const detail = (): WorkflowDetail => ({
    code: 'opportunity',
    label: 'Oportunidade',
    states: [
      state({ id: 'st-new', code: 'NEW_OPPORTUNITY', label: 'Nova', category: 'INITIAL', sortOrder: 1 }),
      state({ id: 'st-disc', code: 'DISCOVERY', label: 'Descoberta', category: 'ACTIVE', sortOrder: 2 }),
      state({ id: 'st-won', code: 'WON', label: 'Ganha', category: 'TERMINAL_POSITIVE', sortOrder: 8 }),
      state({ id: 'st-lost', code: 'LOST', label: 'Perdida', category: 'TERMINAL_NEGATIVE', sortOrder: 9 }),
    ],
    transitions: [
      {
        id: 'tr-1',
        code: 'ADVANCE',
        label: 'Avançar',
        fromState: 'NEW_OPPORTUNITY',
        toState: 'DISCOVERY',
        trigger: 'USER',
        system: true,
        rules: [{ id: 'ru-1', kind: 'GUARD', ruleKey: 'strictFunnel', params: null, sortOrder: 1, system: true }],
      },
      {
        id: 'tr-2',
        code: 'LOSE',
        label: 'Perder',
        fromState: 'DISCOVERY',
        toState: 'LOST',
        trigger: 'USER',
        system: true,
        rules: [],
      },
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
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
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
    paramMap$ = new BehaviorSubject(routeParam('opportunity'));
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

  it('loads the workflow and lays out the diagram (non-terminals on the main row, terminals below)', () => {
    const comp = build();
    expect(comp['workflow']()?.code).toBe('opportunity');
    const dg = comp['diagram']();
    expect(dg.nodes).toHaveLength(4);
    expect(dg.edges).toHaveLength(2);
    const find = (code: string) => dg.nodes.find((n) => n.state.code === code)!;
    // Terminals sit on a lower row than the main path.
    expect(find('LOST').y).toBeGreaterThan(find('NEW_OPPORTUNITY').y);
    expect(find('WON').y).toBe(find('LOST').y);
    // The main path flows left to right.
    expect(find('DISCOVERY').x).toBeGreaterThan(find('NEW_OPPORTUNITY').x);
    expect(dg.edges[0].label).toBe('Avançar');
  });

  it('reloads when the route :code changes (navigating between workflows via the sidebar)', () => {
    const comp = build();
    expect(workflows.detail).toHaveBeenLastCalledWith('opportunity');
    comp['selection'].set({ kind: 'attention-new' }); // an open panel...

    paramMap$.next(routeParam('lead'));

    expect(workflows.detail).toHaveBeenLastCalledWith('lead');
    expect(comp['selection']()).toBeNull(); // ...is reset on switch
  });

  it('shows a permission message on 403', () => {
    workflows.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    expect(comp['error']()).toContain('permissão');
  });

  it('opens the state panel on a node click and saves the edited label', () => {
    const comp = build();
    comp['onNodeClick'](detail().states[1]); // Descoberta
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
    comp['onNodeClick'](detail().states[1]);
    comp['stateLabel'] = '   ';
    expect(comp['canSaveState']()).toBe(false);
  });

  it('opens the read-only transition panel on an edge click', () => {
    const comp = build();
    comp['onEdgeClick'](detail().transitions[0]);
    expect(comp['selectedTransition']()?.code).toBe('ADVANCE');
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
    comp['onNodeClick'](detail().states[1]);
    expect(comp.hasUnsavedChanges()).toBe(false); // just opened, untouched
    comp['stateLabel'] = 'changed';
    expect(comp.hasUnsavedChanges()).toBe(true);
  });

  it('gives the edit panel a contextual title for what is selected', () => {
    const comp = build();
    expect(comp['panelTitle']()).toBe('Edição');
    comp['onNodeClick'](detail().states[1]);
    expect(comp['panelTitle']()).toBe('Editar estado · Descoberta');
    comp['openNewRule']();
    expect(comp['panelTitle']()).toBe('Nova regra de atenção');
    comp['openRule'](detail().attentionRules[1]);
    expect(comp['panelTitle']()).toBe('Editar regra de atenção');
  });

  describe('DOM', () => {
    function render() {
      configure();
      const fixture = TestBed.createComponent(WorkflowEditor);
      fixture.componentInstance.ngOnInit();
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    it('renders the SVG diagram with a node per state and the attention rules', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Oportunidade');
      expect(el.querySelector('svg.wf-diagram')).not.toBeNull();
      const nodes = el.querySelectorAll('.wf-node');
      expect(nodes).toHaveLength(4);
      expect(el.querySelector('svg.wf-diagram')?.textContent).toContain('Nova');
      expect(el.querySelector('svg.wf-diagram')?.textContent).toContain('Perdida');
      // One edge line + one inline arrowhead per transition (no SVG <marker>, which a <base href> breaks).
      expect(el.querySelectorAll('.wf-edge-line')).toHaveLength(2);
      expect(el.querySelectorAll('.wf-arrow-head')).toHaveLength(2);
      expect(el.querySelector('marker')).toBeNull();
      expect(el.textContent).toContain('Sem atividade recente');
      expect(el.textContent).toContain('Regra custom');
    });

    it('shows the counts and the category legend for orientation', () => {
      const el = render();
      const counts = el.querySelector('.counts')?.textContent ?? '';
      expect(counts).toContain('4 estados');
      expect(counts).toContain('2 transições');
      expect(counts).toContain('2 regras de atenção');
      const legend = el.querySelector('.legend');
      expect(legend?.textContent).toContain('Inicial');
      expect(legend?.textContent).toContain('Em andamento');
      expect(legend?.textContent).toContain('Sucesso');
      expect(legend?.querySelectorAll('.sw').length).toBeGreaterThanOrEqual(5);
    });

    it('explains there are no transitions for a computed-status workflow (no arrows to draw)', () => {
      workflows.detail.mockReturnValue(of({ ...detail(), transitions: [] }));
      const el = render();
      expect(el.querySelectorAll('.wf-node')).toHaveLength(4); // states still drawn
      expect(el.querySelectorAll('.wf-edge-line')).toHaveLength(0); // no arrows
      expect(el.textContent).toContain('não possui transições');
    });

    it('hides the delete control for a system rule but shows it for a custom rule', () => {
      const el = render();
      const items = el.querySelectorAll('.rule-list li');
      expect(items).toHaveLength(2);
      expect(items[0].querySelector('.pi-trash')).toBeNull();
      expect(items[1].querySelector('.pi-trash')).not.toBeNull();
    });
  });
});
