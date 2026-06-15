import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of } from 'rxjs';
import { LeadList } from './lead-list';
import { LeadListItem, LeadService } from '../../../core/api/lead.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('LeadList', () => {
  const leads = { origins: vi.fn(), responsibles: vi.fn(), list: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const sampleItem: LeadListItem = {
    id: 'l1',
    name: 'Alpha',
    mainContact: '11999990001',
    origin: 'Website',
    status: 'NEW',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    createdAt: '2026-06-15T10:00:00Z',
    lastInteractionAt: null,
    lastInteractionType: null,
    nextContactAt: null,
  };
  const pageOf = (content: LeadListItem[]) => ({
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
      imports: [LeadList],
      providers: [
        providePrimeNG(),
        { provide: LeadService, useValue: leads },
        { provide: Router, useValue: router },
      ],
    });
    return TestBed.createComponent(LeadList).componentInstance;
  }

  beforeEach(() => {
    leads.origins.mockReset();
    leads.responsibles.mockReset();
    leads.list.mockReset();
    router.navigateByUrl.mockReset();
    leads.origins.mockReturnValue(of([{ id: 'o1', code: 'WEBSITE', label: 'Website' }]));
    leads.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
    leads.list.mockReturnValue(of(pageOf([sampleItem])));
  });

  it('loads origins and a responsible filter that starts with an Unassigned option', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['origins']()).toHaveLength(1);
    expect(comp['responsibleOptions']()[0]).toEqual({ id: 'unassigned', name: 'Sem responsável' });
    expect(comp['responsibleOptions']()).toHaveLength(2);
  });

  it('initial lazy load queries with no status so LOST is excluded by default', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(leads.list).toHaveBeenCalledTimes(1);
    const [filters, page, size] = leads.list.mock.calls[0];
    expect(filters.status).toEqual([]);
    expect(page).toBe(0);
    expect(size).toBe(20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('applying a status filter re-queries from page 0 with that status (LOST shown when chosen)', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(40));
    leads.list.mockClear();

    comp['status'] = ['LOST'];
    comp['applyFilters']();

    expect(comp['firstRow']).toBe(0);
    const [filters, page] = leads.list.mock.calls[0];
    expect(filters.status).toEqual(['LOST']);
    expect(page).toBe(0);
  });

  it('clearFilters resets every filter and reloads', () => {
    const comp = build();
    comp['status'] = ['NEW'];
    comp['search'] = 'x';
    comp['originId'] = 'o1';
    comp['responsible'] = 'unassigned';

    comp['clearFilters']();

    expect(comp['status']).toEqual([]);
    expect(comp['search']).toBe('');
    expect(comp['originId']).toBeNull();
    expect(comp['responsible']).toBeNull();
    expect(leads.list).toHaveBeenCalled();
  });

  it('maps statuses to pt-BR labels', () => {
    const comp = build();
    expect(comp['statusLabel']('LOST')).toBe('Perdido');
    expect(comp['statusLabel']('NEW')).toBe('Novo');
    expect(comp['statusLabel']('CONTACTED')).toBe('Em contato');
  });
});
