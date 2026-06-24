import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { ReceivableDetailPage } from './receivable-detail';
import { ReceivableDetail, ReceivableService } from '../../../core/api/receivable.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableDetailPage', () => {
  const receivables = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

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
      { number: 1, amount: 600, dueDate: '2026-07-15', status: 'OPEN', paymentNotes: 'entrada' },
      { number: 2, amount: 900, dueDate: '2026-08-15', status: 'OPEN', paymentNotes: null },
    ],
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
    router.navigateByUrl.mockReset();
    receivables.detail.mockReturnValue(of(sample));
  });

  it('loads the receivable on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(receivables.detail).toHaveBeenCalledWith('r1');
    expect(comp['receivable']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('OPEN')).toBe('Em aberto');
    expect(comp['statusSeverity']('PAID')).toBe('success');
    expect(comp['orderCode'](7)).toBe('PC-0007');
  });

  it('flags a past-due open installment as overdue, but not a future or settled one', () => {
    const comp = build();
    const past = '2020-01-01';
    const future = '2999-12-31';
    expect(comp['installmentOverdue']({ number: 1, amount: 1, dueDate: past, status: 'OPEN', paymentNotes: null })).toBe(
      true,
    );
    expect(
      comp['installmentOverdue']({ number: 1, amount: 1, dueDate: future, status: 'OPEN', paymentNotes: null }),
    ).toBe(false);
    expect(comp['installmentOverdue']({ number: 1, amount: 1, dueDate: past, status: 'PAID', paymentNotes: null })).toBe(
      false,
    );
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
      expect(el.textContent).toContain('Pagamentos e estornos');
      expect(el.textContent).toContain('Nenhum pagamento registrado ainda.');
    });

    it('shows the overdue marker and the days-overdue note when the receivable is past due', () => {
      receivables.detail.mockReturnValue(
        of({ ...sample, overdue: true, dueDate: '2020-01-01' } satisfies ReceivableDetail),
      );
      const el = render();
      expect(el.textContent).toContain('Vencida');
      expect(el.textContent).toMatch(/vencida há \d+ dia/);
    });

    it('renders the installment schedule (numbers, amounts and status)', () => {
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.textContent).toContain('Parcelas');
      expect(el.textContent).toContain('entrada'); // installment 1 notes
      expect(el.textContent).toContain('R$'); // currency-formatted amounts
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
