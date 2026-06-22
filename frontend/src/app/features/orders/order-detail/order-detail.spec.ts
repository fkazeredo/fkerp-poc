import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { OrderDetailPage } from './order-detail';
import { CommercialOrderDetail, OrderService } from '../../../core/api/order.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('OrderDetailPage', () => {
  const orders = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const sample: CommercialOrderDetail = {
    id: 'ord1',
    number: 7,
    proposalId: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    status: 'PENDING_BOOKING',
    requiresBooking: true,
    bookingStatus: null,
    responsibleId: 'u1',
    responsibleName: 'comercial',
    unassigned: false,
    items: [
      {
        id: 'i1',
        type: 'TRAVEL_PACKAGE',
        description: 'Pacote Caribe',
        quantity: 2,
        unitValue: 1500,
        discountType: null,
        discountValue: null,
        lineTotal: 3000,
      },
    ],
    subtotal: 3000,
    total: 3000,
    createdAt: '2026-06-20T10:00:00Z',
    createdByName: 'comercial',
    sourceProposal: {
      id: 'p1',
      title: 'Proposta Aurora',
      status: 'ACCEPTED',
      validUntil: '2026-12-31',
      commercialTerms: 'Pagamento à vista',
      notes: 'Cliente prefere voos diretos',
      paymentNotes: 'Sinal de 30% na reserva',
    },
    sourceOpportunity: { id: 'o1', name: 'Aurora', stage: 'WON' },
    sourceLead: {
      id: 'l1',
      name: 'Cliente Aurora',
      phone: '11999998888',
      whatsapp: null,
      email: 'cliente@aurora.com',
      status: 'QUALIFIED',
    },
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OrderDetailPage],
      providers: [
        providePrimeNG(),
        { provide: OrderService, useValue: orders },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ord1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OrderDetailPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    orders.detail.mockReset();
    router.navigateByUrl.mockReset();
    orders.detail.mockReturnValue(of(sample));
  });

  it('loads the order on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(orders.detail).toHaveBeenCalledWith('ord1');
    expect(comp['order']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('PENDING_BOOKING')).toBe('Pendente de reserva');
    expect(comp['statusLabel']('BOOKING_NOT_REQUIRED')).toBe('Reserva não necessária');
    expect(comp['statusSeverity']('PENDING_BOOKING')).toBe('warn');
    expect(comp['stageLabel']('WON')).toBe('Ganha');
  });

  it('formats the order code and the next-step note', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['orderCode'](1234)).toBe('PC-1234');
    expect(comp['nextStep']('PENDING_BOOKING')).toContain('operações de reserva');
    expect(comp['nextStep']('BOOKING_NOT_REQUIRED')).toContain('não necessária');
  });

  it('shows a permission message on 403', () => {
    orders.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    orders.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrado');
  });

  it('navigates back to the source proposal', () => {
    const comp = build();
    comp.ngOnInit();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/propostas/p1');
  });

  describe('booking status reflection', () => {
    it('maps the booking status labels and severities to pt-BR', () => {
      const comp = build();
      expect(comp['bookingStatusLabel']('CONFIRMED')).toBe('Confirmada');
      expect(comp['bookingStatusLabel']('PARTIALLY_CONFIRMED')).toBe('Parcialmente confirmada');
      expect(comp['bookingStatusLabel']('FAILED')).toBe('Falhou');
      expect(comp['bookingStatusSeverity']('CONFIRMED')).toBe('success');
      expect(comp['bookingStatusSeverity']('FAILED')).toBe('danger');
    });

    it('gives a ready-for-finance hint when confirmed and a problem hint when failed', () => {
      const comp = build();
      expect(comp['bookingHint']('CONFIRMED')).toContain('Financeiro');
      expect(comp['bookingHint']('FAILED')).toContain('Problema');
      expect(comp['bookingHint'](null)).toContain('não iniciada');
    });

    it('renders the confirmed booking status and the ready-for-finance hint (DOM)', () => {
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'CONFIRMED' } satisfies CommercialOrderDetail));
      const el = render();
      expect(el.textContent).toContain('Status da reserva');
      expect(el.textContent).toContain('Confirmada');
      expect(el.textContent).toContain('pode seguir para o Financeiro');
    });

    it('renders the failed booking status as a problem (DOM)', () => {
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'FAILED' } satisfies CommercialOrderDetail));
      const el = render();
      expect(el.textContent).toContain('Falhou');
      expect(el.textContent).toContain('Problema na reserva');
    });

    it('renders "não iniciada" when there is no booking request yet (DOM)', () => {
      orders.detail.mockReturnValue(of(sample)); // bookingStatus = null
      const el = render();
      expect(el.textContent).toContain('Status da reserva');
      expect(el.textContent).toContain('Reserva ainda não iniciada');
    });
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      orders.detail.mockReturnValue(NEVER);
      const el = render();
      expect(el.textContent).toContain('Carregando');
    });

    it('renders the order record: status, items, totals and source links', () => {
      orders.detail.mockReturnValue(of(sample));
      const el = render();

      expect(el.querySelector('h1')?.textContent).toContain('Pedido PC-0007');
      expect(el.textContent).toContain('Pendente de reserva'); // status tag
      expect(el.textContent).toContain('Próximo passo'); // next operational step note
      expect(el.textContent).toContain('Pacote Caribe'); // item row
      expect(el.textContent).toContain('Total');
      expect(el.textContent).toContain('Proposta Aurora'); // source proposal
      expect(el.textContent).toContain('Ver proposta de origem');
      expect(el.textContent).toContain('Ganha'); // won opportunity stage
      expect(el.textContent).toContain('Cliente Aurora'); // source lead
      expect(el.textContent).toContain('Ver oportunidade de origem');

      // The commercial context surfaced from the source Proposal, ready for Sprint 4 booking.
      expect(el.textContent).toContain('Necessita reserva');
      expect(el.textContent).toContain('Sim'); // requiresBooking = true (PENDING_BOOKING)
      expect(el.textContent).toContain('Termos comerciais');
      expect(el.textContent).toContain('Pagamento à vista');
      expect(el.textContent).toContain('Observações de pagamento');
      expect(el.textContent).toContain('Sinal de 30% na reserva');
    });

    it('hides the optional commercial-context rows when the source proposal has none', () => {
      orders.detail.mockReturnValue(
        of({
          ...sample,
          status: 'BOOKING_NOT_REQUIRED',
          requiresBooking: false,
          sourceProposal: {
            id: 'p1',
            title: 'Proposta Aurora',
            status: 'ACCEPTED',
            validUntil: null,
            commercialTerms: null,
            notes: null,
            paymentNotes: null,
          },
        } satisfies CommercialOrderDetail),
      );
      const el = render();
      expect(el.textContent).toContain('Necessita reserva');
      expect(el.textContent).toContain('Não'); // requiresBooking = false
      expect(el.textContent).not.toContain('Termos comerciais');
      expect(el.textContent).not.toContain('Observações de pagamento');
    });

    it('renders the error state with a back button on 403', () => {
      orders.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.querySelector('p-message')).not.toBeNull();
      expect(el.textContent).toContain('Voltar');
    });
  });
});
