import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { BookingPending } from './booking-pending';
import { BookingService, PendingBookingRequest } from '../../../core/api/booking.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('BookingPending', () => {
  const bookings = { pending: vi.fn() };

  const item: PendingBookingRequest = {
    id: 'bk1',
    commercialOrderId: 'ord1',
    commercialOrderNumber: 7,
    proposalId: 'p1',
    proposalTitle: 'Proposta Aurora',
    status: 'PENDING',
    bookingOperatorId: null,
    bookingOperatorName: null,
    operatorUnassigned: true,
    responsiblePersonId: 'u1',
    responsibleName: 'comercial',
    itemsRequiringBooking: 2,
    confirmedItems: 0,
    failedItems: 0,
    lastBookingAttemptAt: null,
    nextActionDate: null,
    createdAt: '2026-06-20T10:00:00Z',
    updatedAt: '2026-06-20T10:00:00Z',
    reasons: ['UNASSIGNED_OPERATOR', 'PENDING_WITHOUT_ATTEMPT', 'HAS_PENDING_REQUIRED_ITEM'],
  };
  const pageOf = (content: PendingBookingRequest[]) => ({
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
      imports: [BookingPending],
      providers: [providePrimeNG(), provideRouter([]), { provide: BookingService, useValue: bookings }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(BookingPending).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(BookingPending);
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    bookings.pending.mockReset();
    bookings.pending.mockReturnValue(of(pageOf([item])));
  });

  it('loads the pending worklist on lazy load', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(bookings.pending).toHaveBeenCalledWith(0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('maps the reason and status labels to pt-BR', () => {
    const comp = build();
    expect(comp['reasonLabel']('UNASSIGNED_OPERATOR')).toBe('Sem operador');
    expect(comp['reasonLabel']('HAS_FAILED_ITEM')).toBe('Item com falha');
    expect(comp['reasonLabel']('OVERDUE_NEXT_ACTION')).toBe('Próxima ação atrasada');
    expect(comp['reasonSeverity']('HAS_FAILED_ITEM')).toBe('danger');
    expect(comp['statusLabel']('PARTIALLY_CONFIRMED')).toBe('Parcialmente confirmada');
    expect(comp['orderCode'](7)).toBe('PC-0007');
  });

  it('shows an error message when the load fails', () => {
    bookings.pending.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('pendências');
  });

  describe('DOM rendering', () => {
    it('renders a pending booking row with its reason tags', () => {
      bookings.pending.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('PC-0007'); // the reservation code (source order number)
      expect(el.textContent).toContain('Proposta Aurora');
      expect(el.textContent).toContain('Sem operador'); // a reason label (unassigned operator)
      expect(el.textContent).toContain('Item a reservar pendente'); // a reason label
    });

    it('renders the empty state when nothing is pending', () => {
      bookings.pending.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toMatch(/Nenhuma reserva pendente/i);
    });
  });
});
