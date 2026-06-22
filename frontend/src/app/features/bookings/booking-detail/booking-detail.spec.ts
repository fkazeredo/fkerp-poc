import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { BookingDetail } from './booking-detail';
import {
  BookingRequestDetail,
  BookingRequestItem,
  BookingService,
} from '../../../core/api/booking.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('BookingDetail', () => {
  const bookings = {
    detail: vi.fn(),
    registerAttempt: vi.fn(),
    confirmTravelPackage: vi.fn(),
    confirmCarRental: vi.fn(),
    failBookingItem: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };
  const auth = { canOperateBookings: vi.fn(() => false) };

  const sample = (over: Partial<BookingRequestDetail> = {}): BookingRequestDetail => ({
    id: 'bk1',
    commercialOrderId: 'ord1',
    commercialOrderNumber: 7,
    status: 'PENDING',
    bookingOperatorId: 'u6',
    bookingOperatorName: 'operacoes',
    operatorUnassigned: false,
    responsiblePersonId: 'u1',
    responsibleName: 'comercial',
    notes: 'Reservar com urgência',
    itemsRequiringBooking: 2,
    itemsConfirmed: 0,
    itemsFailed: 0,
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
        status: 'PENDING',
        confirmation: null,
        failure: null,
      },
    ],
    attempts: [],
    sourceOrder: { id: 'ord1', number: 7, status: 'PENDING_BOOKING' },
    sourceProposal: { id: 'p1', title: 'Proposta Aurora', status: 'ACCEPTED' },
    sourceOpportunity: { id: 'o1', name: 'Aurora', stage: 'WON' },
    sourceLead: { id: 'l1', name: 'Cliente Aurora' },
    ...over,
  });

  const withAttempt = () =>
    sample({
      status: 'IN_PROGRESS',
      attempts: [
        {
          id: 'a1',
          bookingItemId: 'i1',
          type: 'SUPPLIER_PHONE_CONTACT',
          result: 'WAITING_FOR_SUPPLIER',
          description: 'Liguei para o fornecedor',
          occurredAt: '2026-06-21T08:00:00Z',
          nextActionDate: '2026-06-25',
          registeredByName: 'operacoes',
        },
      ],
    });

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BookingDetail],
      providers: [
        providePrimeNG(),
        ConfirmationService,
        { provide: BookingService, useValue: bookings },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: messages },
        { provide: AuthService, useValue: auth },
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
    bookings.registerAttempt.mockReset();
    bookings.confirmTravelPackage.mockReset();
    bookings.confirmCarRental.mockReset();
    bookings.failBookingItem.mockReset();
    router.navigateByUrl.mockReset();
    messages.add.mockReset();
    auth.canOperateBookings.mockReset().mockReturnValue(false);
    bookings.detail.mockReturnValue(of(sample()));
  });

  it('loads the booking on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(bookings.detail).toHaveBeenCalledWith('bk1');
    expect(comp['booking']()?.id).toBe('bk1');
    expect(comp['loading']()).toBe(false);
  });

  it('maps the reservation, item and attempt status labels to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('PARTIALLY_CONFIRMED')).toBe('Parcialmente confirmada');
    expect(comp['itemStatusLabel']('CONFIRMED')).toBe('Confirmado');
    expect(comp['attemptTypeLabel']('SUPPLIER_PHONE_CONTACT')).toBe('Contato telefônico com fornecedor');
    expect(comp['attemptResultLabel']('WAITING_FOR_SUPPLIER')).toBe('Aguardando fornecedor');
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

  describe('register attempt', () => {
    it('only offers the register action to users with the update scope', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp['canRegisterAttempt']()).toBe(false);
      auth.canOperateBookings.mockReturnValue(true);
      expect(comp['canRegisterAttempt']()).toBe(true);
    });

    it('requires type, result, date and description before saving', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openAttempt']();
      expect(comp['canSaveAttempt']()).toBe(false);
      comp['attemptType'] = 'INTERNAL_VERIFICATION';
      comp['attemptResult'] = 'STARTED';
      comp['attemptDescription'] = 'Conferi internamente';
      comp['attemptOccurredAt'] = new Date('2026-06-21T08:00:00Z');
      expect(comp['canSaveAttempt']()).toBe(true);
    });

    it('builds the item-link options (whole request + each item)', () => {
      const comp = build();
      comp.ngOnInit();
      const opts = comp['itemOptions']();
      expect(opts[0]).toEqual({ value: '', label: 'Reserva toda' });
      expect(opts[1].value).toBe('i1');
    });

    it('registers the attempt, refreshes the detail and closes the dialog', () => {
      bookings.registerAttempt.mockReturnValue(of(withAttempt()));
      const comp = build();
      comp.ngOnInit();
      comp['openAttempt']();
      comp['attemptType'] = 'SUPPLIER_PHONE_CONTACT';
      comp['attemptResult'] = 'WAITING_FOR_SUPPLIER';
      comp['attemptDescription'] = 'Liguei para o fornecedor';
      comp['attemptOccurredAt'] = new Date('2026-06-21T08:00:00Z');
      comp['confirmAttempt']();
      expect(bookings.registerAttempt).toHaveBeenCalledWith(
        'bk1',
        expect.objectContaining({
          type: 'SUPPLIER_PHONE_CONTACT',
          result: 'WAITING_FOR_SUPPLIER',
          description: 'Liguei para o fornecedor',
          bookingItemId: null,
        }),
      );
      expect(comp['attemptOpen']()).toBe(false);
      expect(comp['booking']()?.status).toBe('IN_PROGRESS');
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success', summary: 'Tentativa registrada' }),
      );
    });
  });

  describe('confirm travel package', () => {
    const confirmed = () =>
      sample({
        status: 'CONFIRMED',
        itemsConfirmed: 1,
        items: [
          {
            id: 'i1',
            orderItemId: 'oi1',
            type: 'TRAVEL_PACKAGE',
            description: 'Pacote Caribe',
            quantity: 2,
            requiresBooking: true,
            status: 'CONFIRMED',
            confirmation: {
              externalSystem: 'Amadeus',
              externalLocator: 'ABC123',
              confirmedAt: '2026-06-21T08:00:00Z',
              confirmedByName: 'operacoes',
              packageDescription: 'Cancún 7 noites',
              travelStartDate: '2026-07-01',
              travelEndDate: '2026-07-08',
              travelerNotes: '2 adultos',
              rentalCompany: null,
              pickupLocation: null,
              dropoffLocation: null,
              pickupAt: null,
              dropoffAt: null,
              carCategory: null,
              operationalNotes: null,
            },
            failure: null,
          },
        ],
      });

    function pkgItem() {
      return sample().items[0];
    }

    function carItem(): BookingRequestItem {
      return {
        id: 'i2',
        orderItemId: 'oi2',
        type: 'CAR_RENTAL',
        description: 'Locação SUV',
        quantity: 1,
        requiresBooking: true,
        status: 'PENDING',
        confirmation: null,
        failure: null,
      };
    }

    it('offers confirm for a confirmable travel package OR car rental item with the update scope', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp['canConfirmItem'](pkgItem())).toBe(false); // no scope
      auth.canOperateBookings.mockReturnValue(true);
      expect(comp['canConfirmItem'](pkgItem())).toBe(true);
      // A car rental item is now also confirmable (Slice 7).
      expect(comp['canConfirmItem'](carItem())).toBe(true);
      // A service fee item is not confirmable.
      expect(comp['canConfirmItem']({ ...pkgItem(), type: 'SERVICE_FEE' })).toBe(false);
      // An already-confirmed item is not confirmable.
      expect(comp['canConfirmItem']({ ...pkgItem(), status: 'CONFIRMED' })).toBe(false);
    });

    it('requires system, locator and date before saving', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openConfirm'](pkgItem());
      expect(comp['canSaveConfirm']()).toBe(false);
      comp['confirmExternalSystem'] = 'Amadeus';
      comp['confirmExternalLocator'] = 'ABC123';
      comp['confirmDate'] = new Date('2026-06-21T08:00:00Z');
      expect(comp['canSaveConfirm']()).toBe(true);
    });

    it('confirms the item, refreshes the detail and closes the dialog', () => {
      bookings.confirmTravelPackage.mockReturnValue(of(confirmed()));
      const comp = build();
      comp.ngOnInit();
      comp['openConfirm'](pkgItem());
      comp['confirmExternalSystem'] = 'Amadeus';
      comp['confirmExternalLocator'] = 'ABC123';
      comp['confirmDate'] = new Date('2026-06-21T08:00:00Z');
      comp['confirmItem']();
      expect(bookings.confirmTravelPackage).toHaveBeenCalledWith(
        'bk1',
        'i1',
        expect.objectContaining({ externalSystem: 'Amadeus', externalLocator: 'ABC123' }),
      );
      expect(comp['confirmOpen']()).toBe(false);
      expect(comp['booking']()?.status).toBe('CONFIRMED');
      expect(comp['confirmations']()).toHaveLength(1);
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success', summary: 'Reserva confirmada' }),
      );
    });

    it('renders the confirmation block for a confirmed item (DOM)', () => {
      bookings.detail.mockReturnValue(of(confirmed()));
      const el = render();
      expect(el.textContent).toContain('Confirmações de reserva');
      expect(el.textContent).toContain('Amadeus');
      expect(el.textContent).toContain('ABC123');
      expect(el.textContent).toContain('Cancún 7 noites');
    });

    it('shows the Confirmar action for a confirmable item only with the scope (DOM)', () => {
      auth.canOperateBookings.mockReturnValue(true);
      bookings.detail.mockReturnValue(of(sample()));
      const el = render();
      expect(el.textContent).toContain('Confirmar');
    });
  });

  describe('confirm car rental', () => {
    const carConfirmed = () =>
      sample({
        status: 'CONFIRMED',
        itemsConfirmed: 1,
        items: [
          {
            id: 'i2',
            orderItemId: 'oi2',
            type: 'CAR_RENTAL',
            description: 'Locação SUV',
            quantity: 1,
            requiresBooking: true,
            status: 'CONFIRMED',
            confirmation: {
              externalSystem: 'Localiza Connect',
              externalLocator: 'CAR-77',
              confirmedAt: '2026-06-21T08:00:00Z',
              confirmedByName: 'operacoes',
              packageDescription: null,
              travelStartDate: null,
              travelEndDate: null,
              travelerNotes: null,
              rentalCompany: 'Localiza',
              pickupLocation: 'GRU Aeroporto',
              dropoffLocation: 'Centro',
              pickupAt: '2026-07-01T12:00:00Z',
              dropoffAt: '2026-07-08T12:00:00Z',
              carCategory: 'SUV',
              operationalNotes: null,
            },
            failure: null,
          },
        ],
      });

    function carItem(): BookingRequestItem {
      return {
        id: 'i2',
        orderItemId: 'oi2',
        type: 'CAR_RENTAL',
        description: 'Locação SUV',
        quantity: 1,
        requiresBooking: true,
        status: 'PENDING',
        confirmation: null,
        failure: null,
      };
    }

    it('opens the dialog in car-rental mode and confirms via confirmCarRental', () => {
      bookings.confirmCarRental.mockReturnValue(of(carConfirmed()));
      const comp = build();
      comp.ngOnInit();
      comp['openConfirm'](carItem());
      expect(comp['confirmItemType']).toBe('CAR_RENTAL');
      comp['confirmExternalSystem'] = 'Localiza Connect';
      comp['confirmExternalLocator'] = 'CAR-77';
      comp['confirmDate'] = new Date('2026-06-21T08:00:00Z');
      comp['confirmRentalCompany'] = 'Localiza';
      comp['confirmCarCategory'] = 'SUV';
      comp['confirmItem']();
      expect(bookings.confirmCarRental).toHaveBeenCalledWith(
        'bk1',
        'i2',
        expect.objectContaining({
          externalSystem: 'Localiza Connect',
          externalLocator: 'CAR-77',
          rentalCompany: 'Localiza',
          carCategory: 'SUV',
        }),
      );
      expect(bookings.confirmTravelPackage).not.toHaveBeenCalled();
      expect(comp['confirmOpen']()).toBe(false);
      expect(comp['booking']()?.status).toBe('CONFIRMED');
    });

    it('renders the car-rental confirmation block for a confirmed car item (DOM)', () => {
      bookings.detail.mockReturnValue(of(carConfirmed()));
      const el = render();
      expect(el.textContent).toContain('Localiza Connect');
      expect(el.textContent).toContain('CAR-77');
      expect(el.textContent).toContain('Localiza'); // rental company
      expect(el.textContent).toContain('SUV'); // car category
      expect(el.textContent).toContain('GRU Aeroporto'); // pickup location
    });
  });

  describe('fail item', () => {
    const failed = () =>
      sample({
        status: 'FAILED',
        itemsFailed: 1,
        items: [
          {
            id: 'i1',
            orderItemId: 'oi1',
            type: 'TRAVEL_PACKAGE',
            description: 'Pacote Caribe',
            quantity: 2,
            requiresBooking: true,
            status: 'FAILED',
            confirmation: null,
            failure: {
              failureReason: 'NO_AVAILABILITY',
              failureNote: 'Sem vagas para a data',
              failedByName: 'operacoes',
              failedAt: '2026-06-21T08:00:00Z',
            },
          },
        ],
      });

    function pkgItem() {
      return sample().items[0];
    }

    it('maps the failure reasons to pt-BR', () => {
      const comp = build();
      expect(comp['failureReasonLabel']('NO_AVAILABILITY')).toBe('Sem disponibilidade');
      expect(comp['failureReasonLabel']('SUPPLIER_UNAVAILABLE')).toBe('Fornecedor indisponível');
      expect(comp['failureReasonLabel']('OTHER')).toBe('Outro');
    });

    it('offers fail only for a requiring, not-resolved item with the update scope', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp['canFailItem'](pkgItem())).toBe(false); // no scope
      auth.canOperateBookings.mockReturnValue(true);
      expect(comp['canFailItem'](pkgItem())).toBe(true);
      // A failed item may still be failed again (update the reason).
      expect(comp['canFailItem']({ ...pkgItem(), status: 'FAILED' })).toBe(true);
      // An item that does not require booking is not failable.
      expect(comp['canFailItem']({ ...pkgItem(), requiresBooking: false })).toBe(false);
      // An already-resolved item is not failable.
      expect(comp['canFailItem']({ ...pkgItem(), status: 'CONFIRMED' })).toBe(false);
      expect(comp['canFailItem']({ ...pkgItem(), status: 'CANCELLED' })).toBe(false);
    });

    it('requires a reason and a date before saving', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openFail'](pkgItem());
      expect(comp['canSaveFail']()).toBe(false);
      comp['failReason'] = 'NO_AVAILABILITY';
      comp['failDate'] = new Date('2026-06-21T08:00:00Z');
      expect(comp['canSaveFail']()).toBe(true);
    });

    it('marks the item as failed, refreshes the detail and closes the dialog', () => {
      bookings.failBookingItem.mockReturnValue(of(failed()));
      const comp = build();
      comp.ngOnInit();
      comp['openFail'](pkgItem());
      comp['failReason'] = 'NO_AVAILABILITY';
      comp['failNote'] = 'Sem vagas para a data';
      comp['failDate'] = new Date('2026-06-21T08:00:00Z');
      comp['failItem']();
      expect(bookings.failBookingItem).toHaveBeenCalledWith(
        'bk1',
        'i1',
        expect.objectContaining({
          failureReason: 'NO_AVAILABILITY',
          failureNote: 'Sem vagas para a data',
        }),
      );
      expect(comp['failOpen']()).toBe(false);
      expect(comp['booking']()?.status).toBe('FAILED');
      expect(comp['failedItems']()).toHaveLength(1);
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success', summary: 'Falha registrada' }),
      );
    });

    it('sends a null note when left blank', () => {
      bookings.failBookingItem.mockReturnValue(of(failed()));
      const comp = build();
      comp.ngOnInit();
      comp['openFail'](pkgItem());
      comp['failReason'] = 'OTHER';
      comp['failDate'] = new Date('2026-06-21T08:00:00Z');
      comp['failItem']();
      expect(bookings.failBookingItem).toHaveBeenCalledWith(
        'bk1',
        'i1',
        expect.objectContaining({ failureReason: 'OTHER', failureNote: null }),
      );
    });

    it('still offers Confirmar for a failed item (retry)', () => {
      auth.canOperateBookings.mockReturnValue(true);
      const comp = build();
      comp.ngOnInit();
      const failedItem = failed().items[0];
      expect(comp['canConfirmItem'](failedItem)).toBe(true);
      expect(comp['canFailItem'](failedItem)).toBe(true);
    });

    it('renders the operational-problems card for a failed item (DOM)', () => {
      bookings.detail.mockReturnValue(of(failed()));
      const el = render();
      expect(el.textContent).toContain('Problemas operacionais');
      expect(el.textContent).toContain('Sem disponibilidade');
      expect(el.textContent).toContain('Sem vagas para a data');
    });

    it('shows the empty operational-problems message when no item failed (DOM)', () => {
      bookings.detail.mockReturnValue(of(sample()));
      const el = render();
      expect(el.textContent).toContain('Nenhum item com falha.');
    });

    it('shows the Falhar action for a requiring item only with the scope (DOM)', () => {
      auth.canOperateBookings.mockReturnValue(true);
      bookings.detail.mockReturnValue(of(sample()));
      const el = render();
      expect(el.textContent).toContain('Falhar');
    });
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      bookings.detail.mockReturnValue(NEVER);
      const el = render();
      expect(el.textContent).toContain('Carregando');
    });

    it('renders the attempt history when there are attempts', () => {
      bookings.detail.mockReturnValue(of(withAttempt()));
      const el = render();
      expect(el.textContent).toContain('Histórico de tentativas');
      expect(el.textContent).toContain('Contato telefônico com fornecedor');
      expect(el.textContent).toContain('Aguardando fornecedor');
      expect(el.textContent).toContain('Liguei para o fornecedor');
      expect(el.textContent).toContain('Próxima ação');
    });

    it('shows the empty attempt-history message and hides the action without the scope', () => {
      bookings.detail.mockReturnValue(of(sample()));
      const el = render();
      expect(el.textContent).toContain('Nenhuma tentativa registrada ainda.');
      expect(el.textContent).not.toContain('Registrar tentativa');
    });

    it('shows the register action when the user may operate', () => {
      auth.canOperateBookings.mockReturnValue(true);
      bookings.detail.mockReturnValue(of(sample()));
      const el = render();
      expect(el.textContent).toContain('Registrar tentativa');
    });

    it('renders the error state with a back button on 403', () => {
      bookings.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.querySelector('p-message')).not.toBeNull();
      expect(el.textContent).toContain('Voltar');
    });
  });
});
