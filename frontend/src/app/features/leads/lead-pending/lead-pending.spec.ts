import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { LeadPending } from './lead-pending';
import { LeadService, PendingItem } from '../../../core/api/lead.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('LeadPending', () => {
  const leads = { pending: vi.fn() };

  const item: PendingItem = {
    id: 'l1',
    name: 'Alpha',
    mainContact: '11999990001',
    status: 'NEW',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    createdAt: '2026-06-15T10:00:00Z',
    nextContactAt: null,
    reasons: ['UNASSIGNED', 'NEW_WITHOUT_INTERACTION'],
  };
  const pageOf = (content: PendingItem[]) => ({
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
      imports: [LeadPending],
      providers: [providePrimeNG(), provideRouter([]), { provide: LeadService, useValue: leads }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(LeadPending).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(LeadPending);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    leads.pending.mockReset();
    leads.pending.mockReturnValue(of(pageOf([item])));
  });

  it('loads the pending worklist on lazy load', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(leads.pending).toHaveBeenCalledWith(0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('maps reason and status labels to pt-BR', () => {
    const comp = build();
    expect(comp['reasonLabel']('UNASSIGNED')).toBe('Sem responsável');
    expect(comp['reasonLabel']('OVERDUE_NEXT_CONTACT')).toBe('Contato atrasado');
    expect(comp['reasonLabel']('CONTACTED_WITHOUT_OUTCOME')).toBe('Sem desfecho');
    expect(comp['statusLabel']('NEW')).toBe('Novo');
  });

  it('shows an error message when the load fails', () => {
    leads.pending.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('pendências');
  });

  describe('DOM rendering', () => {
    it('renders a pending row with its reason tags', () => {
      leads.pending.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Alpha');
      expect(el.textContent).toContain('Sem responsável'); // a reason label
    });

    it('renders the empty state when nothing is pending', () => {
      leads.pending.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toMatch(/Nenhuma|nenhum|pend/i);
    });
  });
});
