import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
  const opportunities = { list: vi.fn(), origins: vi.fn(), responsibles: vi.fn() };

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

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OpportunityList],
      providers: [providePrimeNG(), provideRouter([]), { provide: OpportunityService, useValue: opportunities }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OpportunityList).componentInstance;
  }

  /** Renders the list to the DOM after init + a lazy load and returns the host element. */
  function render() {
    configure();
    const fixture = TestBed.createComponent(OpportunityList);
    fixture.componentInstance.ngOnInit();
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    opportunities.list.mockReset();
    opportunities.list.mockReturnValue(of(pageOf([sampleItem])));
    opportunities.origins.mockReset();
    opportunities.origins.mockReturnValue(of([{ id: 'og1', code: 'WEBSITE', label: 'Website' }]));
    opportunities.responsibles.mockReset();
    opportunities.responsibles.mockReturnValue(of([{ id: 'u1', name: 'Ana' }]));
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

  it('applying responsible/origin/value/period filters re-queries from page 0 with those values', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(40));
    opportunities.list.mockClear();

    comp['responsible'] = 'u1';
    comp['originId'] = 'og1';
    comp['valueMin'] = 500;
    comp['valueMax'] = 2000;
    comp['createdFrom'] = new Date(2026, 0, 1); // 2026-01-01 (local)
    comp['createdTo'] = new Date(2026, 0, 31);
    comp['closeFrom'] = new Date(2026, 1, 1);
    comp['closeTo'] = new Date(2026, 1, 28);
    comp['applyFilters']();

    expect(comp['firstRow']).toBe(0);
    const [filters, page] = opportunities.list.mock.calls[0];
    expect(filters.responsible).toBe('u1');
    expect(filters.originId).toBe('og1');
    expect(filters.valueMin).toBe(500);
    expect(filters.valueMax).toBe(2000);
    expect(filters.createdFrom).toBe('2026-01-01');
    expect(filters.createdTo).toBe('2026-01-31');
    expect(filters.closeFrom).toBe('2026-02-01');
    expect(filters.closeTo).toBe('2026-02-28');
    expect(page).toBe(0);
  });

  it('ngOnInit loads origins and prepends the unassigned bucket to responsibles', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['origins']()).toHaveLength(1);
    expect(comp['responsibleOptions']()[0]).toEqual({ id: 'unassigned', name: 'Sem responsável' });
    expect(comp['responsibleOptions']()[1]).toEqual({ id: 'u1', name: 'Ana' });
  });

  it('clearFilters resets every filter and reloads', () => {
    const comp = build();
    comp['stage'] = ['DISCOVERY'];
    comp['responsible'] = 'u1';
    comp['originId'] = 'og1';
    comp['valueMin'] = 100;
    comp['valueMax'] = 900;
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['closeTo'] = new Date(2026, 1, 28);
    comp['search'] = 'x';

    comp['clearFilters']();

    expect(comp['stage']).toEqual([]);
    expect(comp['responsible']).toBeNull();
    expect(comp['originId']).toBeNull();
    expect(comp['valueMin']).toBeNull();
    expect(comp['valueMax']).toBeNull();
    expect(comp['createdFrom']).toBeNull();
    expect(comp['closeTo']).toBeNull();
    expect(comp['search']).toBe('');
    expect(opportunities.list).toHaveBeenCalled();
  });

  it('maps stages to pt-BR labels', () => {
    const comp = build();
    expect(comp['stageLabel']('NEW_OPPORTUNITY')).toBe('Nova');
    expect(comp['stageLabel']('READY_FOR_PROPOSAL')).toBe('Pronta p/ proposta');
    expect(comp['stageLabel']('LOST')).toBe('Perdida');
  });

  describe('DOM rendering', () => {
    it('renders an opportunity row', () => {
      opportunities.list.mockReturnValue(of(pageOf([sampleItem])));
      const el = render();
      expect(el.textContent).toContain('Alpha');
      expect(el.textContent).toContain('Nova'); // stage tag
    });

    it('renders the empty state when there are no opportunities', () => {
      opportunities.list.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toMatch(/Nenhuma oportunidade/i);
    });
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
