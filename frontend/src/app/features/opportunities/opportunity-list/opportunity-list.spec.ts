import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of } from 'rxjs';
import { OpportunityList } from './opportunity-list';
import { OpportunityListItem, OpportunityService } from '../../../core/api/opportunity.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('OpportunityList', () => {
  const opportunities = { list: vi.fn() };

  const sampleItem: OpportunityListItem = {
    id: 'o1',
    leadId: 'l1',
    name: 'Alpha',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    stage: 'NEW_OPPORTUNITY',
    estimatedValue: 1000,
    expectedCloseDate: null,
    createdAt: '2026-06-15T10:00:00Z',
    lastActivityAt: null,
    nextActionDate: null,
  };
  const pageOf = (content: OpportunityListItem[]) => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
  });

  function build() {
    TestBed.configureTestingModule({
      imports: [OpportunityList],
      providers: [providePrimeNG(), { provide: OpportunityService, useValue: opportunities }],
    });
    return TestBed.createComponent(OpportunityList).componentInstance;
  }

  beforeEach(() => {
    opportunities.list.mockReset();
    opportunities.list.mockReturnValue(of(pageOf([sampleItem])));
  });

  it('initial lazy load queries with no stage so LOST is excluded by default', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(opportunities.list).toHaveBeenCalledTimes(1);
    const [filters, page, size] = opportunities.list.mock.calls[0];
    expect(filters.stage).toEqual([]);
    expect(page).toBe(0);
    expect(size).toBe(20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('applying a stage filter re-queries from page 0 with that stage (LOST shown when chosen)', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(40));
    opportunities.list.mockClear();

    comp['stage'] = ['LOST'];
    comp['applyFilters']();

    expect(comp['firstRow']).toBe(0);
    const [filters, page] = opportunities.list.mock.calls[0];
    expect(filters.stage).toEqual(['LOST']);
    expect(page).toBe(0);
  });

  it('clearFilters resets stage and search and reloads', () => {
    const comp = build();
    comp['stage'] = ['DISCOVERY'];
    comp['search'] = 'x';

    comp['clearFilters']();

    expect(comp['stage']).toEqual([]);
    expect(comp['search']).toBe('');
    expect(opportunities.list).toHaveBeenCalled();
  });

  it('maps stages to pt-BR labels', () => {
    const comp = build();
    expect(comp['stageLabel']('NEW_OPPORTUNITY')).toBe('Nova');
    expect(comp['stageLabel']('READY_FOR_PROPOSAL')).toBe('Pronta p/ proposta');
    expect(comp['stageLabel']('LOST')).toBe('Perdida');
  });

  it('surfaces a friendly error when the list request fails', () => {
    const comp = build();
    opportunities.list.mockReturnValueOnce({
      subscribe: ({ error }: { error: (e: unknown) => void }) => error(new Error('boom')),
    });
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toBe('Não foi possível carregar as oportunidades.');
    expect(comp['loading']()).toBe(false);
  });
});
