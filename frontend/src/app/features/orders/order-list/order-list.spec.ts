import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { OrderList } from './order-list';
import { CommercialOrderListItem, OrderService } from '../../../core/api/order.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('OrderList', () => {
  const orders = { list: vi.fn(), responsibles: vi.fn() };

  const item: CommercialOrderListItem = {
    id: 'ord1',
    number: 7,
    proposalId: 'p1',
    proposalTitle: 'Contrato Acme',
    opportunityId: 'o1',
    opportunityName: 'Acme Viagens',
    status: 'PENDING_BOOKING',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    total: 2500,
    requiresBooking: true,
    bookingStatus: null,
    financialStatus: null,
    commissionStatus: null,
    createdAt: '2026-06-20T10:00:00Z',
  };
  const pageOf = (content: CommercialOrderListItem[]) => ({
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
      imports: [OrderList],
      providers: [providePrimeNG(), provideRouter([]), { provide: OrderService, useValue: orders }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OrderList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(OrderList);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    orders.list.mockReset();
    orders.responsibles.mockReset();
    orders.list.mockReturnValue(of(pageOf([item])));
    orders.responsibles.mockReturnValue(of([]));
  });

  it('loads the orders on lazy load with the default (active) filters', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(orders.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: [], responsible: null, bookingNeed: null }),
      0,
      20,
    );
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('applies the chosen status/booking-need filters and resets to the first page', () => {
    const comp = build();
    comp['firstRow'] = 40;
    comp['status'] = ['CANCELLED'];
    comp['bookingNeed'] = 'REQUIRED';

    comp['applyFilters']();

    expect(comp['firstRow']).toBe(0);
    expect(orders.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['CANCELLED'], bookingNeed: 'REQUIRED' }),
      0,
      20,
    );
  });

  it('clears every filter and reloads', () => {
    const comp = build();
    comp['status'] = ['PENDING_BOOKING'];
    comp['responsible'] = 'u1';
    comp['bookingNeed'] = 'NOT_REQUIRED';
    comp['search'] = 'acme';
    comp['totalMin'] = 100;

    comp['clearFilters']();

    expect(comp['status']).toEqual([]);
    expect(comp['responsible']).toBeNull();
    expect(comp['bookingNeed']).toBeNull();
    expect(comp['search']).toBe('');
    expect(comp['totalMin']).toBeNull();
    expect(orders.list).toHaveBeenCalled();
  });

  it('loads the responsibles (with the unassigned bucket prepended) on init', () => {
    orders.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
    const comp = build();
    comp.ngOnInit();
    expect(orders.responsibles).toHaveBeenCalled();
    const opts = comp['responsibleOptions']();
    expect(opts[0]).toEqual({ id: 'unassigned', name: 'Sem responsável' });
    expect(opts).toHaveLength(2);
  });

  it('formats the order code and maps the status label', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['statusLabel']('PENDING_BOOKING')).toBe('Pendente de reserva');
    expect(comp['statusLabel']('BOOKING_NOT_REQUIRED')).toBe('Reserva não necessária');
  });

  it('maps the reflected booking status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['bookingStatusLabel']('CONFIRMED')).toBe('Confirmada');
    expect(comp['bookingStatusLabel']('FAILED')).toBe('Falhou');
    expect(comp['bookingStatusSeverity']('CONFIRMED')).toBe('success');
    expect(comp['bookingStatusSeverity']('FAILED')).toBe('danger');
  });

  it('maps the reflected commission status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['commissionStatusLabel']('EXPECTED')).toBe('Prevista');
    expect(comp['commissionStatusLabel']('PAID')).toBe('Paga');
    expect(comp['commissionStatusLabel']('ISSUE')).toBe('Problema na comissão');
    expect(comp['commissionStatusSeverity']('PAID')).toBe('success');
    expect(comp['commissionStatusSeverity']('ISSUE')).toBe('danger');
  });

  it('shows an error message when the load fails', () => {
    orders.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('pedidos');
  });

  describe('DOM rendering', () => {
    it('renders the table headers and an order row with the operational columns', () => {
      orders.list.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Identificador');
      expect(el.textContent).toContain('Resumo');
      expect(el.textContent).toContain('Reserva');
      expect(el.textContent).toContain('PC-0007'); // the order code
      expect(el.textContent).toContain('Contrato Acme'); // the client-facing summary (proposal title)
      expect(el.textContent).toContain('Acme Viagens'); // source opportunity name
      expect(el.textContent).toContain('Pendente de reserva'); // status tag
      expect(el.textContent).toContain('Exige reserva'); // booking-need indicator
      expect(el.textContent).toContain('Status da reserva'); // booking-status column header
      expect(el.textContent).toContain('Não iniciada'); // no booking request yet (bookingStatus null)
    });

    it('renders the reflected booking status tag when present', () => {
      orders.list.mockReturnValue(of(pageOf([{ ...item, bookingStatus: 'CONFIRMED' }])));
      const el = render();
      expect(el.textContent).toContain('Confirmada');
    });

    it('renders the reflected financial status column and tag', () => {
      orders.list.mockReturnValue(of(pageOf([{ ...item, financialStatus: 'PAID' }])));
      const el = render();
      expect(el.textContent).toContain('Status financeiro'); // column header
      expect(el.textContent).toContain('Paga'); // financial-status tag
    });

    it('renders the reflected commission status column header and the empty hint', () => {
      orders.list.mockReturnValue(of(pageOf([item]))); // commissionStatus null
      const el = render();
      expect(el.textContent).toContain('Status da comissão'); // column header
      expect(el.textContent).toContain('Sem comissão'); // empty hint
    });

    it('renders the reflected commission status tag when present', () => {
      orders.list.mockReturnValue(of(pageOf([{ ...item, commissionStatus: 'ISSUE' }])));
      const el = render();
      expect(el.textContent).toContain('Problema na comissão');
    });

    it('renders the filter controls (status, booking need, responsible, search)', () => {
      const el = render();
      expect(el.querySelector('#q')).not.toBeNull();
      expect(el.textContent).toContain('Status');
      expect(el.textContent).toContain('Reserva');
      expect(el.textContent).toContain('Responsável');
      expect(el.textContent).toContain('Limpar');
    });

    it('renders the empty state when there are no orders', () => {
      orders.list.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toContain('Nenhum pedido ainda.');
    });
  });
});
