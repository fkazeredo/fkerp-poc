import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { OpportunityPending } from './opportunity-pending';
import { OpportunityService, PendingOpportunity } from '../../../core/api/opportunity.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('OpportunityPending', () => {
  const opportunities = { pending: vi.fn() };

  const item: PendingOpportunity = {
    id: 'o1',
    leadId: 'l1',
    name: 'Alpha',
    stage: 'NEW_OPPORTUNITY',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    estimatedValue: 1000,
    expectedCloseDate: null,
    nextActionDate: null,
    createdAt: '2026-05-01T10:00:00Z',
    lastActivityAt: null,
    reasons: ['STUCK_IN_NEW', 'WITHOUT_RECENT_ACTIVITY'],
  };
  const pageOf = (content: PendingOpportunity[]) => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
  });

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OpportunityPending],
      providers: [providePrimeNG(), provideRouter([]), { provide: OpportunityService, useValue: opportunities }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OpportunityPending).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(OpportunityPending);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    opportunities.pending.mockReset();
    opportunities.pending.mockReturnValue(of(pageOf([item])));
  });

  it('loads the pending worklist on lazy load', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(opportunities.pending).toHaveBeenCalledWith(0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('maps reason and stage labels to pt-BR', () => {
    const comp = build();
    expect(comp['reasonLabel']('WITHOUT_RECENT_ACTIVITY')).toBe('Sem atividade recente');
    expect(comp['reasonLabel']('OVERDUE_NEXT_ACTION')).toBe('Próxima ação atrasada');
    expect(comp['reasonLabel']('EXPECTED_CLOSE_OVERDUE')).toBe('Fechamento vencido');
    expect(comp['stageLabel']('NEW_OPPORTUNITY')).toBe('Nova');
  });

  it('shows an error message when the load fails', () => {
    opportunities.pending.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('pendências');
  });

  describe('DOM rendering', () => {
    it('renders a pending opportunity row with its reason tags', () => {
      opportunities.pending.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Alpha');
      expect(el.textContent).toContain('Sem atividade recente'); // a reason label
    });

    it('renders the empty state when nothing is pending', () => {
      opportunities.pending.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toMatch(/Nenhuma|nenhum|pend/i);
    });
  });
});
