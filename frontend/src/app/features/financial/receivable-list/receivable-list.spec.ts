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
  const receivables = { list: vi.fn(), responsibles: vi.fn() };
  const auth = { canCreateReceivable: vi.fn() };

  const item: ReceivableListItem = {
    id: 'r1',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    customerName: 'Maria Silva',
    totalAmount: 1500,
    amountPaid: 0,
    outstandingAmount: 1500,
    status: 'OPEN',
    dueDate: '2026-07-15',
    overdue: false,
    commercialResponsibleId: 'u1',
    commercialResponsibleName: 'comercial',
    financialResponsibleId: null,
    financialResponsibleName: null,
    createdAt: '2026-06-20T10:00:00Z',
    lastPaymentDate: null,
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
    receivables.responsibles.mockReset().mockReturnValue(of([]));
    auth.canCreateReceivable.mockReset().mockReturnValue(true);
    receivables.list.mockReturnValue(of(pageOf([item])));
  });

  it('loads the receivables on lazy load with the default (operational) filters', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(receivables.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: [], overdueOnly: false }),
      0,
      20,
    );
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('loads the responsibles for the filter selects on init', () => {
    receivables.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
    const comp = build();
    comp.ngOnInit();
    expect(receivables.responsibles).toHaveBeenCalled();
    expect(comp['responsibleOptions']()).toHaveLength(1);
  });

  it('applies the status / payer / amount / overdue filters and resets to the first page', () => {
    const comp = build();
    comp['firstRow'] = 40;
    comp['status'] = ['OVERDUE'];
    comp['payer'] = 'maria';
    comp['amountMin'] = 100;
    comp['overdueOnly'] = true;
    comp['applyFilters']();
    expect(comp['firstRow']).toBe(0);
    expect(receivables.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['OVERDUE'], payer: 'maria', amountMin: 100, overdueOnly: true }),
      0,
      20,
    );
  });

  it('converts the due/creation date filters to ISO', () => {
    const comp = build();
    comp['dueFrom'] = new Date(2026, 6, 1);
    comp['createdTo'] = new Date(2026, 5, 30);
    comp['applyFilters']();
    expect(receivables.list).toHaveBeenCalledWith(
      expect.objectContaining({ dueFrom: '2026-07-01', createdTo: '2026-06-30' }),
      0,
      20,
    );
  });

  it('clears every filter and reloads', () => {
    const comp = build();
    comp['status'] = ['PAID'];
    comp['payer'] = 'x';
    comp['amountMin'] = 5;
    comp['overdueOnly'] = true;
    comp['dueFrom'] = new Date(2026, 0, 1);
    comp['commercialResponsible'] = 'u1';

    comp['clearFilters']();

    expect(comp['status']).toEqual([]);
    expect(comp['payer']).toBeNull();
    expect(comp['amountMin']).toBeNull();
    expect(comp['overdueOnly']).toBe(false);
    expect(comp['dueFrom']).toBeNull();
    expect(comp['commercialResponsible']).toBeNull();
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
    it('renders the operational columns and a receivable row', () => {
      receivables.list.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Pedido');
      expect(el.textContent).toContain('Cliente');
      expect(el.textContent).toContain('Total');
      expect(el.textContent).toContain('Pago');
      expect(el.textContent).toContain('Em aberto'); // outstanding column header (and the OPEN status label)
      expect(el.textContent).toContain('Próx. vencimento');
      expect(el.textContent).toContain('Resp. comercial');
      expect(el.textContent).toContain('Resp. financeiro');
      expect(el.textContent).toContain('Último pgto.');
      expect(el.textContent).toContain('PC-0007'); // source order code
      expect(el.textContent).toContain('Maria Silva'); // payer
      expect(el.textContent).toContain('comercial'); // commercial responsible name
    });

    it('renders the operational filters (payer, source order, amount range, overdue only)', () => {
      const el = render();
      expect(el.textContent).toContain('Cliente (pagador)');
      expect(el.textContent).toContain('Pedido nº');
      expect(el.textContent).toContain('Valor mín.');
      expect(el.textContent).toContain('Valor máx.');
      expect(el.textContent).toContain('Somente vencidas');
      expect(el.textContent).toContain('Vence de');
      expect(el.textContent).toContain('Criado de');
    });

    it('flags an overdue receivable with a "Vencida" tag in the row', () => {
      receivables.list.mockReturnValue(of(pageOf([{ ...item, overdue: true, status: 'OVERDUE' }])));
      const el = render();
      expect(el.querySelector('.overdue-row')).not.toBeNull();
      expect(el.textContent).toContain('Vencida');
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
      expect(render().textContent).toContain('Nenhuma conta a receber para acompanhar.');
    });
  });
});
