import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { BookingDetail } from './booking-detail';
import { BookingRequestDetail, BookingService } from '../../../core/api/booking.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('BookingDetail', () => {
  const bookings = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const sample: BookingRequestDetail = {
    id: 'bk1',
    commercialOrderId: 'ord1',
    commercialOrderNumber: 7,
    status: 'PARTIALLY_CONFIRMED',
    bookingOperatorId: 'u6',
    bookingOperatorName: 'operacoes',
    operatorUnassigned: false,
    responsiblePersonId: 'u1',
    responsibleName: 'comercial',
    notes: 'Reservar com urgência',
    itemsRequiringBooking: 2,
    itemsConfirmed: 1,
    itemsFailed: 1,
    createdAt: '2026-06-20T10:00:00Z',
    updatedAt: '2026-06-21T09:00:00Z',
    createdByName: 'operacoes',
    items: [
      {
        id: 'i1',
        orderItemId: 'oi1',
        type: 'TRAVEL_PACKAGE',
        description: 'Pacote Caribe',
        quantity: 2,
        requiresBooking: true,
        status: 'CONFIRMED',
      },
      {
        id: 'i2',
        orderItemId: 'oi2',
        type: 'CAR_RENTAL',
        description: 'Locação SUV',
        quantity: 1,
        requiresBooking: true,
        status: 'FAILED',
      },
      {
        id: 'i3',
        orderItemId: 'oi3',
        type: 'SERVICE_FEE',
        description: 'Taxa de emissão',
        quantity: 1,
        requiresBooking: false,
        status: 'NOT_REQUIRED',
      },
    ],
    sourceOrder: { id: 'ord1', number: 7, status: 'PENDING_BOOKING' },
    sourceProposal: { id: 'p1', title: 'Proposta Aurora', status: 'ACCEPTED' },
    sourceOpportunity: { id: 'o1', name: 'Aurora', stage: 'WON' },
    sourceLead: { id: 'l1', name: 'Cliente Aurora' },
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BookingDetail],
      providers: [
        providePrimeNG(),
        { provide: BookingService, useValue: bookings },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'bk1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(BookingDetail).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(BookingDetail);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    bookings.detail.mockReset();
    router.navigateByUrl.mockReset();
    bookings.detail.mockReturnValue(of(sample));
  });

  it('loads the booking on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(bookings.detail).toHaveBeenCalledWith('bk1');
    expect(comp['booking']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the reservation and item status labels/severities to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('PARTIALLY_CONFIRMED')).toBe('Parcialmente confirmada');
    expect(comp['statusSeverity']('FAILED')).toBe('danger');
    expect(comp['itemStatusLabel']('CONFIRMED')).toBe('Confirmado');
    expect(comp['itemStatusLabel']('FAILED')).toBe('Falhou');
    expect(comp['itemStatusLabel']('NOT_REQUIRED')).toBe('Não requer reserva');
    expect(comp['itemStatusSeverity']('CONFIRMED')).toBe('success');
  });

  it('formats the reservation code from the source order number', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['orderCode'](1234)).toBe('PC-1234');
  });

  it('shows a permission message on 403', () => {
    bookings.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    bookings.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  it('navigates back to the reservation list', () => {
    const comp = build();
    comp.ngOnInit();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/reservas');
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      bookings.detail.mockReturnValue(NEVER);
      const el = render();
      expect(el.textContent).toContain('Carregando');
    });

    it('renders the reservation record: status, counts, items with statuses and source links', () => {
      bookings.detail.mockReturnValue(of(sample));
      const el = render();

      expect(el.querySelector('h1')?.textContent).toContain('Reserva PC-0007');
      expect(el.textContent).toContain('Parcialmente confirmada'); // reservation status tag
      expect(el.textContent).toContain('Reservar com urgência'); // operational notes
      // Item rows with their booking statuses (the confirmation/failure signal).
      expect(el.textContent).toContain('Pacote Caribe');
      expect(el.textContent).toContain('Confirmado');
      expect(el.textContent).toContain('Locação SUV');
      expect(el.textContent).toContain('Falhou');
      expect(el.textContent).toContain('Não requer reserva');
      // Counts.
      expect(el.textContent).toContain('Itens p/ reservar');
      expect(el.textContent).toContain('Confirmados');
      expect(el.textContent).toContain('Com falha');
      // Source traceability links.
      expect(el.textContent).toContain('Ver pedido de origem');
      expect(el.textContent).toContain('Proposta Aurora');
      expect(el.textContent).toContain('Ver proposta de origem');
      expect(el.textContent).toContain('Aurora'); // opportunity
      expect(el.textContent).toContain('Ver oportunidade de origem');
    });

    it('hides the notes row when there are none', () => {
      bookings.detail.mockReturnValue(of({ ...sample, notes: null } satisfies BookingRequestDetail));
      const el = render();
      expect(el.textContent).not.toContain('Observações');
    });

    it('renders the error state with a back button on 404', () => {
      bookings.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
      const el = render();
      expect(el.textContent).toContain('não encontrada');
      expect(el.querySelector('p-message')).not.toBeNull();
      expect(el.textContent).toContain('Voltar');
    });
  });
});
