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
    proposalId: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    status: 'PENDING_BOOKING',
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
    sourceProposal: { id: 'p1', title: 'Proposta Aurora', status: 'ACCEPTED' },
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

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      orders.detail.mockReturnValue(NEVER);
      const el = render();
      expect(el.textContent).toContain('Carregando');
    });

    it('renders the order record: status, items, totals and source links', () => {
      orders.detail.mockReturnValue(of(sample));
      const el = render();

      expect(el.querySelector('h1')?.textContent).toContain('Pedido comercial');
      expect(el.textContent).toContain('Pendente de reserva'); // status tag
      expect(el.textContent).toContain('Pacote Caribe'); // item row
      expect(el.textContent).toContain('Total');
      expect(el.textContent).toContain('Proposta Aurora'); // source proposal
      expect(el.textContent).toContain('Ver proposta de origem');
      expect(el.textContent).toContain('Ganha'); // won opportunity stage
      expect(el.textContent).toContain('Cliente Aurora'); // source lead
      expect(el.textContent).toContain('Ver oportunidade de origem');
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
