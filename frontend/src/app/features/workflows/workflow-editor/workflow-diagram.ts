import {
  WorkflowDetail,
  WorkflowStateView,
  WorkflowTransitionView,
} from '../../../core/api/workflow.service';

/** A positioned state box in the diagram. */
export interface DiagramNode {
  state: WorkflowStateView;
  x: number;
  y: number;
  w: number;
  h: number;
}

/** A positioned transition arrow (an SVG path) with its label pill. */
export interface DiagramEdge {
  id: string;
  transition: WorkflowTransitionView;
  d: string;
  labelX: number;
  labelY: number;
  pillW: number;
  label: string;
}

/** The full computed diagram: positioned nodes + edges and the SVG viewBox size. */
export interface Diagram {
  nodes: DiagramNode[];
  edges: DiagramEdge[];
  width: number;
  height: number;
}

const NODE_W = 168;
const NODE_H = 58;
const COL_GAP = 64;
const ROW_GAP = 96;
const PAD = 28;

const EMPTY: Diagram = { nodes: [], edges: [], width: 0, height: 0 };

function isTerminal(category: string): boolean {
  return category === 'TERMINAL_POSITIVE' || category === 'TERMINAL_NEGATIVE';
}

/**
 * Lays out a workflow as a left-to-right flowchart: the non-terminal states (INITIAL/ACTIVE) form the main row,
 * ordered by flow depth (longest path from a source) then sort order; the terminal states sit on a lower row,
 * spread across the width. Transitions become smooth cubic-bezier arrows with a label pill at the midpoint. All
 * geometry is computed here (no DOM measurement), so the same result renders in the browser and in jsdom tests.
 *
 * @param wf the workflow detail (or null while loading)
 * @return the positioned diagram (empty when there are no states)
 */
export function buildDiagram(wf: WorkflowDetail | null): Diagram {
  if (!wf || wf.states.length === 0) {
    return EMPTY;
  }

  // Longest-path layer (forward DAG) used to order the main row; capped iterations guard against any cycle.
  const layer = new Map<string, number>(wf.states.map((s) => [s.code, 0] as const));
  for (let i = 0; i < wf.states.length; i++) {
    for (const t of wf.transitions) {
      const next = (layer.get(t.fromState) ?? 0) + 1;
      if (next > (layer.get(t.toState) ?? 0)) {
        layer.set(t.toState, next);
      }
    }
  }

  const top = wf.states
    .filter((s) => !isTerminal(s.category))
    .sort((a, b) => layer.get(a.code)! - layer.get(b.code)! || a.sortOrder - b.sortOrder);
  const bottom = wf.states.filter((s) => isTerminal(s.category)).sort((a, b) => a.sortOrder - b.sortOrder);

  const topY = PAD;
  const bottomY = PAD + NODE_H + ROW_GAP;

  const pos = new Map<string, DiagramNode>();
  top.forEach((s, i) => {
    pos.set(s.code, { state: s, x: PAD + i * (NODE_W + COL_GAP), y: topY, w: NODE_W, h: NODE_H });
  });

  const topWidth = top.length > 0 ? top.length * (NODE_W + COL_GAP) - COL_GAP : NODE_W;
  const contentWidth = Math.max(topWidth, bottom.length * (NODE_W + COL_GAP) - COL_GAP, NODE_W);
  bottom.forEach((s, i) => {
    const slot =
      bottom.length === 1
        ? (contentWidth - NODE_W) / 2
        : (i * (contentWidth - NODE_W)) / (bottom.length - 1);
    pos.set(s.code, { state: s, x: PAD + slot, y: bottomY, w: NODE_W, h: NODE_H });
  });

  const nodes = [...pos.values()];
  const width = PAD * 2 + contentWidth;
  const height = (bottom.length > 0 ? bottomY + NODE_H : topY + NODE_H) + PAD;

  const edges: DiagramEdge[] = [];
  for (const t of wf.transitions) {
    const a = pos.get(t.fromState);
    const b = pos.get(t.toState);
    if (a && b) {
      edges.push(edgeGeometry(t, a, b));
    }
  }

  return { nodes, edges, width, height };
}

function edgeGeometry(t: WorkflowTransitionView, a: DiagramNode, b: DiagramNode): DiagramEdge {
  let sx: number;
  let sy: number;
  let ex: number;
  let ey: number;
  let d: string;
  let labelX: number;
  let labelY: number;

  if (a.y === b.y) {
    // Same row: a horizontal arrow between the right/left edges.
    const leftToRight = b.x >= a.x;
    sx = leftToRight ? a.x + a.w : a.x;
    sy = a.y + a.h / 2;
    ex = leftToRight ? b.x : b.x + b.w;
    ey = b.y + b.h / 2;
    const dx = Math.max(28, Math.abs(ex - sx) / 2) * (leftToRight ? 1 : -1);
    d = `M ${sx} ${sy} C ${sx + dx} ${sy}, ${ex - dx} ${ey}, ${ex} ${ey}`;
    labelX = (sx + ex) / 2;
    labelY = sy - 10;
  } else {
    // To a lower row (a terminal): a vertical arrow from the bottom of a to the top of b.
    sx = a.x + a.w / 2;
    sy = a.y + a.h;
    ex = b.x + b.w / 2;
    ey = b.y;
    const dy = Math.max(28, (ey - sy) / 2);
    d = `M ${sx} ${sy} C ${sx} ${sy + dy}, ${ex} ${ey - dy}, ${ex} ${ey}`;
    labelX = (sx + ex) / 2;
    labelY = (sy + ey) / 2;
  }

  return {
    id: t.id,
    transition: t,
    d,
    labelX,
    labelY,
    pillW: Math.max(30, t.label.length * 6.8 + 14),
    label: t.label,
  };
}
