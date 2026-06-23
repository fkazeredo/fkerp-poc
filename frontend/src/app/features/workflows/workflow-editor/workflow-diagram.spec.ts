import { buildDiagram } from './workflow-diagram';
import { WorkflowDetail, WorkflowStateView } from '../../../core/api/workflow.service';

function st(over: Partial<WorkflowStateView>): WorkflowStateView {
  return { id: 'i', code: 'C', label: 'L', category: 'ACTIVE', sortOrder: 1, active: true, system: false, ...over };
}

function wf(over: Partial<WorkflowDetail>): WorkflowDetail {
  return { code: 'w', label: 'W', states: [], transitions: [], attentionRules: [], ...over };
}

describe('buildDiagram', () => {
  it('returns an empty diagram for no workflow / no states', () => {
    expect(buildDiagram(null)).toEqual({ nodes: [], edges: [], width: 0, height: 0 });
    expect(buildDiagram(wf({ states: [] })).nodes).toHaveLength(0);
  });

  it('orders the main row left-to-right by flow depth and drops terminals to a lower row', () => {
    const dg = buildDiagram(
      wf({
        states: [
          st({ code: 'NEW', category: 'INITIAL', sortOrder: 1 }),
          st({ code: 'MID', category: 'ACTIVE', sortOrder: 2 }),
          st({ code: 'DONE', category: 'TERMINAL_POSITIVE', sortOrder: 3 }),
          st({ code: 'LOST', category: 'TERMINAL_NEGATIVE', sortOrder: 4 }),
        ],
        transitions: [
          { id: 't1', code: 'A', label: 'avançar', fromState: 'NEW', toState: 'MID', trigger: 'U', system: true, rules: [] },
          { id: 't2', code: 'B', label: 'perder', fromState: 'MID', toState: 'LOST', trigger: 'U', system: true, rules: [] },
        ],
      }),
    );
    const at = (code: string) => dg.nodes.find((n) => n.state.code === code)!;
    // main row: NEW then MID (NEW left of MID); same row.
    expect(at('MID').x).toBeGreaterThan(at('NEW').x);
    expect(at('MID').y).toBe(at('NEW').y);
    // terminals below the main row.
    expect(at('DONE').y).toBeGreaterThan(at('NEW').y);
    expect(at('LOST').y).toBe(at('DONE').y);
    // an edge per transition, each with a path and a label.
    expect(dg.edges).toHaveLength(2);
    expect(dg.edges.every((e) => e.d.startsWith('M') && e.label.length > 0)).toBe(true);
    expect(dg.width).toBeGreaterThan(0);
    expect(dg.height).toBeGreaterThan(0);
  });

  it('handles a single-state workflow without terminals', () => {
    const dg = buildDiagram(wf({ states: [st({ code: 'ONLY', category: 'INITIAL' })] }));
    expect(dg.nodes).toHaveLength(1);
    expect(dg.edges).toHaveLength(0);
  });
});
