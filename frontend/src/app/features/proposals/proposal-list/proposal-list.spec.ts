import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { ProposalList } from './proposal-list';
import { ProposalListItem, ProposalService } from '../../../core/api/proposal.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('ProposalList', () => {
  const proposals = { list: vi.fn() };

  const item: ProposalListItem = {
    id: 'p1',
    opportunityId: 'o1',
    title: 'Proposta corporativa',
    status: 'DRAFT',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    total: 2000,
    validUntil: null,
    createdAt: '2026-06-17T10:00:00Z',
  };
  const pageOf = (content: ProposalListItem[]) => ({
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
      imports: [ProposalList],
      providers: [providePrimeNG(), { provide: ProposalService, useValue: proposals }],
    });
    return TestBed.createComponent(ProposalList).componentInstance;
  }

  beforeEach(() => {
    proposals.list.mockReset();
    proposals.list.mockReturnValue(of(pageOf([item])));
  });

  it('loads the proposals on lazy load', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(proposals.list).toHaveBeenCalledWith(0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('maps the status label to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('DRAFT')).toBe('Rascunho');
    expect(comp['statusLabel']('REJECTED')).toBe('Rejeitada');
  });

  it('shows an error message when the load fails', () => {
    proposals.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('propostas');
  });
});
