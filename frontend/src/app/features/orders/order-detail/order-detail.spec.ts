import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { OrderDetailPage } from './order-detail';
import { CommercialOrderDetail, OrderService } from '../../../core/api/order.service';
import { CommissionListItem, CommissionService } from '../../../core/api/commission.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('OrderDetailPage', () => {
  const orders = { detail: vi.fn() };
  const commissions = { generate: vi.fn(), detail: vi.fn(), byOrder: vi.fn() };
  const router = { navigateByUrl: vi.fn(), navigate: vi.fn() };
  const auth = {
    canCreateReceivable: vi.fn(),
    canCreateCommission: vi.fn(),
    canSeeCommissions: vi.fn(),
  };
  const messages = { add: vi.fn() };

  const commissionSample: CommissionListItem = {
    id: 'c1',
    beneficiaryUserId: 'u1',
    beneficiaryName: 'comercial',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    proposalReference: 'Proposta Aurora',
    opportunityReference: 'Aurora',
    amount: 150,
    baseAmount: 3000,
    basisType: 'COMMERCIAL_AMOUNT',
    rulePercentage: 5,
    ruleName: 'Comissão padrão',
    status: 'EXPECTED',
    receivableStatus: null,
    createdAt: '2026-06-25T10:00:00Z',
    eligibleAt: null,
    approvedAt: null,
    paidAt: null,
  };

  const sample: CommercialOrderDetail = {
    id: 'ord1',
    number: 7,
    proposalId: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    status: 'PENDING_BOOKING',
    requiresBooking: true,
    bookingStatus: null,
    financialStatus: null,
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
        { provide: CommissionService, useValue: commissions },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: MessageService, useValue: messages },
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
    commissions.generate.mockReset();
    commissions.detail.mockReset();
    commissions.byOrder.mockReset();
    router.navigateByUrl.mockReset();
    router.navigate.mockReset();
    messages.add.mockReset();
    auth.canCreateReceivable.mockReset().mockReturnValue(false);
    auth.canCreateCommission.mockReset().mockReturnValue(false);
    auth.canSeeCommissions.mockReset().mockReturnValue(false);
    orders.detail.mockReturnValue(of(sample));
    commissions.generate.mockReturnValue(of({ id: 'c1' }));
    commissions.detail.mockReturnValue(of(commissionSample));
    commissions.byOrder.mockReturnValue(of(null));
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

  describe('financial reflection', () => {
    it('renders the financial status tag and the ready-for-commission hint when PAID', () => {
      orders.detail.mockReturnValue(of({ ...sample, financialStatus: 'PAID' } satisfies CommercialOrderDetail));
      const el = render();
      expect(el.textContent).toContain('Paga');
      expect(el.textContent).toContain('Comissionamento');
    });

    it('renders the financial-problem hint when OVERDUE', () => {
      orders.detail.mockReturnValue(of({ ...sample, financialStatus: 'OVERDUE' } satisfies CommercialOrderDetail));
      const el = render();
      expect(el.textContent).toContain('Vencida');
      expect(el.textContent).toContain('problema financeiro');
    });

    it('shows the no-receivable hint when there is no reflected financial status', () => {
      orders.detail.mockReturnValue(of(sample));
      expect(render().textContent).toContain('Sem conta a receber ainda');
    });
  });

  describe('generate-receivable handoff', () => {
    it('offers the receivable action only when the booking is confirmed and the user may create one', () => {
      auth.canCreateReceivable.mockReturnValue(true);
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'CONFIRMED' } satisfies CommercialOrderDetail));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canGenerateReceivable']()).toBe(true);

      // No create scope → no offer, even when confirmed.
      auth.canCreateReceivable.mockReturnValue(false);
      expect(comp['canGenerateReceivable']()).toBe(false);
    });

    it('does not offer the action when the booking is not confirmed', () => {
      auth.canCreateReceivable.mockReturnValue(true);
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'FAILED' } satisfies CommercialOrderDetail));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canGenerateReceivable']()).toBe(false);
    });

    it('navigates to the receivable create form with the order pre-selected', () => {
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'CONFIRMED' } satisfies CommercialOrderDetail));
      const comp = build();
      comp.ngOnInit();
      comp['generateReceivable']();
      expect(router.navigate).toHaveBeenCalledWith(['/financeiro/contas-a-receber/nova'], {
        queryParams: { order: 'ord1' },
      });
    });

    it('renders the "Gerar conta a receber" button when confirmed and permitted (DOM)', () => {
      auth.canCreateReceivable.mockReturnValue(true);
      orders.detail.mockReturnValue(of({ ...sample, bookingStatus: 'CONFIRMED' } satisfies CommercialOrderDetail));
      expect(render().textContent).toContain('Gerar conta a receber');
    });
  });

  describe('generate-commission', () => {
    it('offers the commission action only for a non-cancelled order when the user may generate one', () => {
      auth.canCreateCommission.mockReturnValue(true);
      const comp = build();
      comp.ngOnInit();
      expect(comp['canGenerateCommission']()).toBe(true);

      // No create scope → no offer.
      auth.canCreateCommission.mockReturnValue(false);
      expect(comp['canGenerateCommission']()).toBe(false);
    });

    it('does not offer the action for a cancelled order', () => {
      auth.canCreateCommission.mockReturnValue(true);
      orders.detail.mockReturnValue(of({ ...sample, status: 'CANCELLED' } satisfies CommercialOrderDetail));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canGenerateCommission']()).toBe(false);
    });

    it('generates the commission and shows it inline (refetched from the order)', () => {
      auth.canCreateCommission.mockReturnValue(true);
      commissions.byOrder.mockReturnValue(of(commissionSample)); // the post-generate refetch
      const comp = build();
      comp.ngOnInit();
      comp['generateCommission']();
      expect(commissions.generate).toHaveBeenCalledWith('ord1');
      expect(commissions.byOrder).toHaveBeenCalledWith('ord1');
      expect(comp['commission']()).toEqual(commissionSample);
      expect(comp['generating']()).toBe(false);
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('shows the already-generated message on 409', () => {
      auth.canCreateCommission.mockReturnValue(true);
      commissions.generate.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
      const comp = build();
      comp.ngOnInit();
      comp['generateCommission']();
      expect(comp['commission']()).toBeNull();
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', summary: 'Comissão já gerada para este pedido.' }),
      );
    });

    it('surfaces the backend business-rule message on 422', () => {
      auth.canCreateCommission.mockReturnValue(true);
      commissions.generate.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 422,
              error: { code: 'commission.no-applicable-rule', message: 'Nenhuma regra de comissão ativa se aplica' },
            }),
        ),
      );
      const comp = build();
      comp.ngOnInit();
      comp['generateCommission']();
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', summary: 'Nenhuma regra de comissão ativa se aplica' }),
      );
    });

    it('does not generate twice while a request is in flight', () => {
      auth.canCreateCommission.mockReturnValue(true);
      commissions.generate.mockReturnValue(NEVER);
      const comp = build();
      comp.ngOnInit();
      comp['generateCommission']();
      comp['generateCommission']();
      expect(commissions.generate).toHaveBeenCalledTimes(1);
    });

    it('the "c" shortcut triggers generation when allowed', () => {
      auth.canCreateCommission.mockReturnValue(true);
      const comp = build();
      comp.ngOnInit();
      comp['onShortcut'](new KeyboardEvent('keydown', { key: 'c' }));
      expect(commissions.generate).toHaveBeenCalledWith('ord1');
    });
  });

  describe('commission visibility (pending approval)', () => {
    it('fetches the order commission on load only when the user may read commissions', () => {
      auth.canSeeCommissions.mockReturnValue(false);
      const noScope = build();
      noScope.ngOnInit();
      expect(commissions.byOrder).not.toHaveBeenCalled();

      auth.canSeeCommissions.mockReturnValue(true);
      commissions.byOrder.mockReturnValue(of(commissionSample));
      const reader = build();
      reader.ngOnInit();
      expect(commissions.byOrder).toHaveBeenCalledWith('ord1');
      expect(reader['commission']()).toEqual(commissionSample);
    });

    it('hides the "Gerar comissão" button when a commission already exists for the order', () => {
      auth.canCreateCommission.mockReturnValue(true);
      auth.canSeeCommissions.mockReturnValue(true);
      commissions.byOrder.mockReturnValue(of(commissionSample));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canGenerateCommission']()).toBe(false);
    });

    it('renders the existing commission as "Prevista" (forecast) on load (DOM)', () => {
      auth.canSeeCommissions.mockReturnValue(true);
      commissions.byOrder.mockReturnValue(of(commissionSample));
      const el = render();
      expect(el.textContent).toContain('Comissão padrão');
      expect(el.textContent).toContain('Valor comercial (previsão)');
      expect(el.textContent).toContain('Prevista');
      expect(el.textContent).not.toContain('Gerar comissão');
      // The panel links to the full commission detail (href resolution is covered by commission-list.spec).
      expect(el.textContent).toContain('Ver detalhe da comissão');
    });

    it('renders an eligible commission as "Pendente de aprovação" with the eligible date (DOM)', () => {
      auth.canSeeCommissions.mockReturnValue(true);
      commissions.byOrder.mockReturnValue(
        of({ ...commissionSample, status: 'ELIGIBLE', eligibleAt: '2026-06-25T12:00:00Z' } satisfies CommissionListItem),
      );
      const el = render();
      expect(el.textContent).toContain('Pendente de aprovação');
      expect(el.textContent).toContain('Elegível desde');
      // The hint explains it is not yet approved or paid.
      expect(el.textContent).toContain('pendente de aprovação');
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
