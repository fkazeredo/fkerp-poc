import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { BookingList } from './booking-list';
import { BookingRequestListItem, BookingService } from '../../../core/api/booking.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('BookingList', () => {
  const bookings = { list: vi.fn(), responsibles: vi.fn() };

  const item: BookingRequestListItem = {
    id: 'bk1',
    commercialOrderId: 'ord1',
    commercialOrderNumber: 7,
    proposalId: 'p1',
    proposalTitle: 'Contrato Acme',
    status: 'PENDING',
    bookingOperatorId: null,
    bookingOperatorName: null,
    operatorUnassigned: true,
    responsiblePersonId: 'u1',
    responsibleName: 'comercial',
    itemsRequiringBooking: 2,
    confirmedItems: 1,
    createdAt: '2026-06-20T10:00:00Z',
    updatedAt: '2026-06-21T09:00:00Z',
    lastBookingAttemptAt: '2026-06-21T08:00:00Z',
  };
  const pageOf = (content: BookingRequestListItem[]) => ({
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
      imports: [BookingList],
      providers: [providePrimeNG(), provideRouter([]), { provide: BookingService, useValue: bookings }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(BookingList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(BookingList);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    bookings.list.mockReset();
    bookings.responsibles.mockReset();
    bookings.list.mockReturnValue(of(pageOf([item])));
    bookings.responsibles.mockReturnValue(of([]));
  });

  it('loads the bookings on lazy load with the default (active) filters', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(bookings.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: [], operator: null, responsible: null, hasFailedItems: false }),
      0,
      20,
    );
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('applies the chosen status/item-type/has-failed filters and resets to the first page', () => {
    const comp = build();
    comp['firstRow'] = 40;
    comp['status'] = ['FAILED'];
    comp['itemType'] = 'CAR_RENTAL';
    comp['hasFailedItems'] = true;

    comp['applyFilters']();

    expect(comp['firstRow']).toBe(0);
    expect(bookings.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['FAILED'], itemType: 'CAR_RENTAL', hasFailedItems: true }),
      0,
      20,
    );
  });

  it('clears every filter and reloads', () => {
    const comp = build();
    comp['status'] = ['CONFIRMED'];
    comp['operator'] = 'unassigned';
    comp['responsible'] = 'u1';
    comp['itemType'] = 'TRAVEL_PACKAGE';
    comp['hasFailedItems'] = true;

    comp['clearFilters']();

    expect(comp['status']).toEqual([]);
    expect(comp['operator']).toBeNull();
    expect(comp['responsible']).toBeNull();
    expect(comp['itemType']).toBeNull();
    expect(comp['hasFailedItems']).toBe(false);
    expect(bookings.list).toHaveBeenCalled();
  });

  it('loads the operators (with the unassigned bucket prepended) and responsibles on init', () => {
    bookings.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
    const comp = build();
    comp.ngOnInit();
    expect(bookings.responsibles).toHaveBeenCalled();
    const operators = comp['operatorOptions']();
    expect(operators[0]).toEqual({ id: 'unassigned', name: 'Sem operador' });
    expect(operators).toHaveLength(2);
    expect(comp['responsibleOptions']()).toHaveLength(1);
  });

  it('formats the order code (reservation id) and maps the status label', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['statusLabel']('PENDING')).toBe('Pendente');
    expect(comp['statusLabel']('FAILED')).toBe('Falhou');
  });

  it('shows an error message when the load fails', () => {
    bookings.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('reservas');
  });

  describe('DOM rendering', () => {
    it('renders the table headers and a booking row with the operational columns', () => {
      bookings.list.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Pedido');
      expect(el.textContent).toContain('Proposta');
      expect(el.textContent).toContain('Itens p/ reservar');
      expect(el.textContent).toContain('Confirmados');
      expect(el.textContent).toContain('Última tentativa');
      expect(el.textContent).toContain('PC-0007'); // the reservation id (source order code)
      expect(el.textContent).toContain('Contrato Acme'); // the commercial reference (proposal title)
      expect(el.textContent).toContain('Pendente'); // status tag
      expect(el.textContent).toContain('Sem operador'); // unassigned operator
      expect(el.textContent).toContain('comercial'); // commercial responsible name
    });

    it('renders the filter controls (status, operator, responsible, item type, has-failed)', () => {
      const el = render();
      expect(el.textContent).toContain('Status');
      expect(el.textContent).toContain('Operador');
      expect(el.textContent).toContain('Responsável comercial');
      expect(el.textContent).toContain('Tipo de item');
      expect(el.textContent).toContain('Com falhas');
      expect(el.textContent).toContain('Limpar');
    });

    it('renders the empty state when there are no bookings', () => {
      bookings.list.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toContain('Nenhuma reserva ainda.');
    });
  });
});
