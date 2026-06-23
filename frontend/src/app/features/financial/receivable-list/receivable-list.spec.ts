import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { ReceivableList } from './receivable-list';
import { ReceivableListItem, ReceivableService } from '../../../core/api/receivable.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('ReceivableList', () => {
  const receivables = { list: vi.fn() };
  const auth = { canCreateReceivable: vi.fn() };

  const item: ReceivableListItem = {
    id: 'r1',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    customerName: 'Maria Silva',
    totalAmount: 1500,
    dueDate: '2026-07-15',
    status: 'OPEN',
    financialResponsibleId: null,
    financialResponsibleName: null,
    createdAt: '2026-06-20T10:00:00Z',
  };
  const pageOf = (content: ReceivableListItem[]) => ({
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
      imports: [ReceivableList],
      providers: [
        providePrimeNG(),
        provideRouter([]),
        { provide: ReceivableService, useValue: receivables },
        { provide: AuthService, useValue: auth },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReceivableList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReceivableList);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    receivables.list.mockReset();
    auth.canCreateReceivable.mockReset().mockReturnValue(true);
    receivables.list.mockReturnValue(of(pageOf([item])));
  });

  it('loads the receivables on lazy load with the default (active) filters', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(receivables.list).toHaveBeenCalledWith(expect.objectContaining({ status: [] }), 0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('applies the chosen status filter and resets to the first page', () => {
    const comp = build();
    comp['firstRow'] = 40;
    comp['status'] = ['OVERDUE'];
    comp['applyFilters']();
    expect(comp['firstRow']).toBe(0);
    expect(receivables.list).toHaveBeenCalledWith(expect.objectContaining({ status: ['OVERDUE'] }), 0, 20);
  });

  it('clears the status filter and reloads', () => {
    const comp = build();
    comp['status'] = ['PAID'];
    comp['clearFilters']();
    expect(comp['status']).toEqual([]);
    expect(receivables.list).toHaveBeenCalled();
  });

  it('formats the order code and maps the status label', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['statusLabel']('OPEN')).toBe('Em aberto');
    expect(comp['statusLabel']('OVERDUE')).toBe('Vencida');
    expect(comp['statusSeverity']('OVERDUE')).toBe('danger');
  });

  it('shows an error message when the load fails', () => {
    receivables.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('contas a receber');
  });

  describe('DOM rendering', () => {
    it('renders the headers and a receivable row with the financial columns', () => {
      receivables.list.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Pedido');
      expect(el.textContent).toContain('Cliente');
      expect(el.textContent).toContain('Vencimento');
      expect(el.textContent).toContain('PC-0007'); // source order code
      expect(el.textContent).toContain('Maria Silva'); // payer
      expect(el.textContent).toContain('Em aberto'); // status tag
    });

    it('shows the "Nova conta a receber" action when the user may create one', () => {
      auth.canCreateReceivable.mockReturnValue(true);
      expect(render().textContent).toContain('Nova conta a receber');
    });

    it('hides the create action for a consultation-only user', () => {
      auth.canCreateReceivable.mockReturnValue(false);
      expect(render().textContent).not.toContain('Nova conta a receber');
    });

    it('renders the empty state when there are no receivables', () => {
      receivables.list.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toContain('Nenhuma conta a receber ainda.');
    });
  });
});
