import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { ReceivableDetailPage } from './receivable-detail';
import { Payment, ReceivableDetail, ReceivableService } from '../../../core/api/receivable.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableDetailPage', () => {
  const receivables = {
    detail: vi.fn(),
    paymentMethods: vi.fn(),
    registerPayment: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn() };
  const auth = { canRegisterPayment: vi.fn() };

  const samplePayment: Payment = {
    id: 'pay1',
    installmentId: 'i1',
    installmentNumber: 1,
    amount: 600,
    paymentDate: '2026-06-01',
    paymentMethodId: 'm1',
    paymentMethodCode: 'PIX',
    paymentMethodLabel: 'Pix',
    note: 'recebido',
    registeredById: 'u5',
    registeredByName: 'financeiro',
    registeredAt: '2026-06-01T12:00:00Z',
  };

  const sample: ReceivableDetail = {
    id: 'r1',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    proposalId: 'p1',
    proposalReference: 'Proposta Aurora',
    opportunityId: 'o1',
    opportunityReference: 'Oportunidade Aurora',
    leadId: 'l1',
    customerId: 'c1',
    customerName: 'Maria Silva',
    commercialResponsibleId: 'u1',
    commercialResponsibleName: 'comercial',
    financialResponsibleId: null,
    financialResponsibleName: null,
    totalAmount: 1500,
    amountPaid: 0,
    outstandingAmount: 1500,
    dueDate: '2026-07-15',
    overdue: false,
    paymentNotes: 'Boleto à vista',
    status: 'OPEN',
    installments: [
      {
        id: 'i1',
        number: 1,
        amount: 600,
        amountPaid: 0,
        outstanding: 600,
        dueDate: '2026-07-15',
        status: 'OPEN',
        paymentNotes: 'entrada',
      },
      {
        id: 'i2',
        number: 2,
        amount: 900,
        amountPaid: 0,
        outstanding: 900,
        dueDate: '2026-08-15',
        status: 'OPEN',
        paymentNotes: null,
      },
    ],
    payments: [],
    createdAt: '2026-06-20T10:00:00Z',
    createdByName: 'financeiro',
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReceivableDetailPage],
      providers: [
        providePrimeNG(),
        { provide: ReceivableService, useValue: receivables },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'r1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReceivableDetailPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReceivableDetailPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    receivables.detail.mockReset();
    receivables.paymentMethods.mockReset();
    receivables.registerPayment.mockReset();
    router.navigateByUrl.mockReset();
    auth.canRegisterPayment.mockReset();
    auth.canRegisterPayment.mockReturnValue(false);
    receivables.detail.mockReturnValue(of(sample));
    receivables.paymentMethods.mockReturnValue(of([{ id: 'm1', code: 'PIX', label: 'Pix', active: true, sortOrder: 3 }]));
  });

  it('loads the receivable on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(receivables.detail).toHaveBeenCalledWith('r1');
    expect(comp['receivable']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('does not load the payment methods for a read-only user', () => {
    const comp = build();
    comp.ngOnInit();
    expect(receivables.paymentMethods).not.toHaveBeenCalled();
  });

  it('loads the payment methods when the user may register payments', () => {
    auth.canRegisterPayment.mockReturnValue(true);
    const comp = build();
    comp.ngOnInit();
    expect(receivables.paymentMethods).toHaveBeenCalled();
    expect(comp['paymentMethods']()).toHaveLength(1);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('OPEN')).toBe('Em aberto');
    expect(comp['statusSeverity']('PAID')).toBe('success');
    expect(comp['orderCode'](7)).toBe('PC-0007');
  });

  it('flags a past-due open installment as overdue, but not a future or settled one', () => {
    const comp = build();
    const base = { id: 'x', number: 1, amount: 1, amountPaid: 0, outstanding: 1, paymentNotes: null };
    expect(comp['installmentOverdue']({ ...base, dueDate: '2020-01-01', status: 'OPEN' })).toBe(true);
    expect(comp['installmentOverdue']({ ...base, dueDate: '2999-12-31', status: 'OPEN' })).toBe(false);
    expect(comp['installmentOverdue']({ ...base, dueDate: '2020-01-01', status: 'PAID' })).toBe(false);
  });

  it('allows paying an OPEN or PARTIALLY_PAID installment when authorized, never a settled one', () => {
    const comp = build();
    const open = {
      id: 'x',
      number: 1,
      amount: 100,
      amountPaid: 0,
      outstanding: 100,
      dueDate: '2026-07-15',
      status: 'OPEN' as const,
      paymentNotes: null,
    };
    const partial = { ...open, status: 'PARTIALLY_PAID' as const, amountPaid: 40, outstanding: 60 };
    const paid = { ...open, status: 'PAID' as const, amountPaid: 100, outstanding: 0 };
    auth.canRegisterPayment.mockReturnValue(false);
    expect(comp['canPay'](open)).toBe(false);
    auth.canRegisterPayment.mockReturnValue(true);
    expect(comp['canPay'](open)).toBe(true);
    expect(comp['canPay'](partial)).toBe(true);
    expect(comp['canPay'](paid)).toBe(false);
  });

  it('computes whole days overdue (positive for the past, zero for the future)', () => {
    const comp = build();
    expect(comp['daysOverdue']('2020-01-01')).toBeGreaterThan(0);
    expect(comp['daysOverdue']('2999-12-31')).toBe(0);
  });

  it('shows a permission message on 403', () => {
    receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  it('navigates back to the receivable list', () => {
    const comp = build();
    comp.ngOnInit();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/financeiro/contas-a-receber');
  });

  describe('register payment', () => {
    beforeEach(() => auth.canRegisterPayment.mockReturnValue(true));

    it('opens the dialog defaulting the amount to the outstanding and the date to today', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openPayment'](sample.installments[0]);
      expect(comp['paymentDialogOpen']()).toBe(true);
      expect(comp['targetInstallment']()?.id).toBe('i1');
      expect(comp['paymentForm'].controls.amount.value).toBe(600); // the installment's outstanding
      expect(comp['paymentForm'].controls.paymentDate.value).toBeInstanceOf(Date);
    });

    it('registers a partial payment and refreshes the detail from the response', () => {
      const partiallyPaid: ReceivableDetail = {
        ...sample,
        status: 'PARTIALLY_PAID',
        amountPaid: 200,
        outstandingAmount: 1300,
        installments: [
          { ...sample.installments[0], status: 'PARTIALLY_PAID', amountPaid: 200, outstanding: 400 },
          sample.installments[1],
        ],
        payments: [{ ...samplePayment, amount: 200 }],
      };
      receivables.registerPayment.mockReturnValue(of(partiallyPaid));
      const comp = build();
      comp.ngOnInit();
      comp['openPayment'](sample.installments[0]);
      comp['paymentForm'].controls.paymentMethodId.setValue('m1');
      comp['paymentForm'].controls.amount.setValue(200);
      comp['submitPayment']();

      expect(receivables.registerPayment).toHaveBeenCalledWith(
        'r1',
        'i1',
        expect.objectContaining({ paymentMethodId: 'm1', amount: 200 }),
      );
      expect(comp['receivable']()?.status).toBe('PARTIALLY_PAID');
      expect(comp['receivable']()?.installments[0].outstanding).toBe(400);
      expect(comp['paymentDialogOpen']()).toBe(false);
    });

    it('registers the full payment and refreshes the detail from the response', () => {
      const settled: ReceivableDetail = {
        ...sample,
        status: 'PARTIALLY_PAID',
        amountPaid: 600,
        outstandingAmount: 900,
        installments: [
          { ...sample.installments[0], status: 'PAID' },
          sample.installments[1],
        ],
        payments: [samplePayment],
      };
      receivables.registerPayment.mockReturnValue(of(settled));
      const comp = build();
      comp.ngOnInit();
      comp['openPayment'](sample.installments[0]);
      comp['paymentForm'].controls.paymentMethodId.setValue('m1');
      comp['submitPayment']();

      expect(receivables.registerPayment).toHaveBeenCalledWith(
        'r1',
        'i1',
        expect.objectContaining({ paymentMethodId: 'm1', amount: 600 }),
      );
      expect(comp['receivable']()?.status).toBe('PARTIALLY_PAID');
      expect(comp['paymentDialogOpen']()).toBe(false);
      expect(comp['saving']()).toBe(false);
    });

    it('does not submit without a payment method (form invalid)', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openPayment'](sample.installments[0]);
      comp['submitPayment']();
      expect(receivables.registerPayment).not.toHaveBeenCalled();
    });

    it('surfaces a 422 business error from the payment endpoint', () => {
      receivables.registerPayment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 422,
              error: {
                code: 'financial.payment.exceeds-outstanding',
                message: 'O valor do pagamento não pode exceder o saldo em aberto da parcela',
              },
            }),
        ),
      );
      const comp = build();
      comp.ngOnInit();
      comp['openPayment'](sample.installments[0]);
      comp['paymentForm'].controls.paymentMethodId.setValue('m1');
      comp['submitPayment']();
      expect(comp['paymentError']()).toContain('exceder o saldo em aberto');
      expect(comp['paymentDialogOpen']()).toBe(true);
    });
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      receivables.detail.mockReturnValue(NEVER);
      expect(render().textContent).toContain('Carregando');
    });

    it('renders the receivable record: paid/outstanding, references, status and traceable origin links', () => {
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Conta a receber · PC-0007');
      expect(el.textContent).toContain('Em aberto'); // status + outstanding label
      expect(el.textContent).toContain('Valor pago');
      expect(el.textContent).toContain('Maria Silva'); // payer
      expect(el.textContent).toContain('Boleto à vista'); // financial notes
      expect(el.textContent).toContain('Proposta Aurora'); // proposal reference
      expect(el.textContent).toContain('Oportunidade Aurora'); // opportunity reference
      expect(el.textContent).toContain('Ver pedido de origem');
      expect(el.textContent).toContain('Ver proposta');
      expect(el.textContent).toContain('Ver lead de origem');
      // The contract carries no Commission / bank-reconciliation labels.
      expect(el.textContent).not.toContain('Comissão');
      expect(el.textContent).not.toContain('Conciliação');
    });

    it('renders the payment-history section with an empty state (no payments yet)', () => {
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.textContent).toContain('Pagamentos');
      expect(el.textContent).toContain('Nenhum pagamento registrado ainda.');
    });

    it('renders the payment history table when payments exist', () => {
      receivables.detail.mockReturnValue(of({ ...sample, amountPaid: 600, payments: [samplePayment] }));
      const el = render();
      const rows = el.querySelectorAll('.payments-table tbody tr');
      expect(rows.length).toBe(1);
      expect(el.textContent).toContain('Pix'); // method label
      expect(el.textContent).toContain('recebido'); // note
    });

    it('shows the "Registrar pagamento" action for open installments when authorized', () => {
      auth.canRegisterPayment.mockReturnValue(true);
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.textContent).toContain('Registrar pagamento');
    });

    it('hides the payment action for a read-only user', () => {
      auth.canRegisterPayment.mockReturnValue(false);
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.querySelector('.col-action p-button')).toBeNull();
    });

    it('shows the overdue marker and the days-overdue note when the receivable is past due', () => {
      receivables.detail.mockReturnValue(
        of({ ...sample, overdue: true, dueDate: '2020-01-01' } satisfies ReceivableDetail),
      );
      const el = render();
      expect(el.textContent).toContain('Vencida');
      expect(el.textContent).toMatch(/vencida há \d+ dia/);
    });

    it('renders the installment schedule (numbers, amounts, paid/outstanding and status)', () => {
      receivables.detail.mockReturnValue(
        of({
          ...sample,
          status: 'PARTIALLY_PAID',
          installments: [
            { ...sample.installments[0], status: 'PARTIALLY_PAID', amountPaid: 250, outstanding: 350 },
            sample.installments[1],
          ],
        } satisfies ReceivableDetail),
      );
      const el = render();
      expect(el.textContent).toContain('Parcelas');
      expect(el.textContent).toContain('entrada'); // installment 1 notes
      expect(el.textContent).toContain('R$'); // currency-formatted amounts
      // The schedule exposes the Pago / Em aberto columns and the partial status.
      expect(el.textContent).toContain('Pago');
      expect(el.textContent).toContain('Em aberto');
      expect(el.textContent).toContain('Parcialmente paga');
      // Two installment rows are rendered (number column shows 1 and 2).
      const rows = el.querySelectorAll('.installments-table tbody tr');
      expect(rows.length).toBe(2);
    });

    it('renders the error state with a back button on 403', () => {
      receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.textContent).toContain('Voltar');
    });
  });
});
