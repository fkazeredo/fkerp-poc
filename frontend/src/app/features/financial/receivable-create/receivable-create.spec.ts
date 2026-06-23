import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { ReceivableCreate } from './receivable-create';
import { EligibleOrder, ReceivableService } from '../../../core/api/receivable.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableCreate', () => {
  const receivables = { eligibleOrders: vi.fn(), responsibles: vi.fn(), create: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };
  let queryOrder: string | null = null;

  const orders: EligibleOrder[] = [
    { orderId: 'ord1', number: 7, customerName: 'Maria Silva', total: 1500 },
    { orderId: 'ord2', number: 8, customerName: 'João Souza', total: 900 },
  ];

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReceivableCreate],
      providers: [
        providePrimeNG(),
        ConfirmationService,
        { provide: ReceivableService, useValue: receivables },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: messages },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => queryOrder } } },
        },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReceivableCreate).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReceivableCreate);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    receivables.eligibleOrders.mockReset();
    receivables.responsibles.mockReset();
    receivables.create.mockReset();
    router.navigateByUrl.mockReset();
    messages.add.mockReset();
    queryOrder = null;
    receivables.eligibleOrders.mockReturnValue(of(orders));
    receivables.responsibles.mockReturnValue(of([{ id: 'u1', name: 'financeiro' }]));
  });

  it('loads the eligible orders and responsibles on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(receivables.eligibleOrders).toHaveBeenCalled();
    expect(receivables.responsibles).toHaveBeenCalled();
    expect(comp['eligibleOrders']()).toHaveLength(2);
    expect(comp['loadingOrders']()).toBe(false);
  });

  it('pre-selects the order passed via the query param when it is eligible', () => {
    queryOrder = 'ord2';
    const comp = build();
    comp.ngOnInit();
    expect(comp['form'].controls.commercialOrderId.value).toBe('ord2');
  });

  it('ignores a query-param order that is not eligible', () => {
    queryOrder = 'unknown';
    const comp = build();
    comp.ngOnInit();
    expect(comp['form'].controls.commercialOrderId.value).toBe('');
  });

  it('does not submit while the form is invalid (missing order/due date)', () => {
    const comp = build();
    comp.ngOnInit();
    comp['submit']();
    expect(receivables.create).not.toHaveBeenCalled();
  });

  it('submits the payload (ISO due date, optional fields nulled) and navigates to the detail', () => {
    receivables.create.mockReturnValue(of({ id: 'r1', status: 'OPEN' }));
    const comp = build();
    comp.ngOnInit();
    comp['form'].patchValue({
      commercialOrderId: 'ord1',
      dueDate: new Date(2026, 6, 15),
      paymentNotes: '  boleto  ',
    });
    comp['submit']();
    expect(receivables.create).toHaveBeenCalledWith({
      commercialOrderId: 'ord1',
      dueDate: '2026-07-15',
      financialResponsiblePersonId: null,
      paymentNotes: 'boleto',
    });
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/financeiro/contas-a-receber/r1');
  });

  it('shows the conflict message when the order already has an active receivable (409)', () => {
    receivables.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Já existe' } })),
    );
    const comp = build();
    comp.ngOnInit();
    comp['form'].patchValue({ commercialOrderId: 'ord1', dueDate: new Date(2026, 6, 15) });
    comp['submit']();
    expect(comp['formError']()).toBe('Já existe');
  });

  it('shows the not-confirmed message on 422', () => {
    receivables.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'Reserva não confirmada' } })),
    );
    const comp = build();
    comp.ngOnInit();
    comp['form'].patchValue({ commercialOrderId: 'ord1', dueDate: new Date(2026, 6, 15) });
    comp['submit']();
    expect(comp['formError']()).toBe('Reserva não confirmada');
  });

  it('tracks unsaved changes once the form is edited', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp.hasUnsavedChanges()).toBe(false);
    comp['form'].patchValue({ paymentNotes: 'x' });
    comp['form'].markAsDirty();
    expect(comp.hasUnsavedChanges()).toBe(true);
  });

  describe('installments', () => {
    it('adds and removes installment rows and tracks the live remaining against the order total', () => {
      const comp = build();
      comp.ngOnInit();
      comp['form'].controls.commercialOrderId.setValue('ord1'); // total 1500
      expect(comp['hasInstallments']()).toBe(false);

      comp['addInstallment']();
      comp['addInstallment']();
      expect(comp['hasInstallments']()).toBe(true);
      const rows = comp['installments'].controls;
      rows[0].patchValue({ amount: 1000, dueDate: new Date(2026, 6, 15) });
      rows[1].patchValue({ amount: 500, dueDate: new Date(2026, 7, 15) });

      expect(comp['installmentsSum']()).toBe(1500);
      expect(comp['remaining']()).toBe(0);
      expect(comp['scheduleBalanced']()).toBe(true);

      comp['removeInstallment'](1);
      expect(comp['installments'].length).toBe(1);
      expect(comp['remaining']()).toBe(500); // 1500 - 1000
      expect(comp['scheduleBalanced']()).toBe(false);
    });

    it('submits a multi-installment schedule and derives the reference due date from the first installment', () => {
      receivables.create.mockReturnValue(of({ id: 'r1', status: 'OPEN' }));
      const comp = build();
      comp.ngOnInit();
      comp['form'].controls.commercialOrderId.setValue('ord1'); // total 1500
      comp['addInstallment']();
      comp['addInstallment']();
      comp['installments'].controls[0].patchValue({
        amount: 1000,
        dueDate: new Date(2026, 6, 15),
        paymentNotes: 'entrada',
      });
      comp['installments'].controls[1].patchValue({ amount: 500, dueDate: new Date(2026, 7, 15) });

      comp['submit']();

      expect(receivables.create).toHaveBeenCalledWith({
        commercialOrderId: 'ord1',
        dueDate: '2026-07-15',
        financialResponsiblePersonId: null,
        paymentNotes: null,
        installments: [
          { amount: 1000, dueDate: '2026-07-15', paymentNotes: 'entrada' },
          { amount: 500, dueDate: '2026-08-15', paymentNotes: null },
        ],
      });
      expect(router.navigateByUrl).toHaveBeenCalledWith('/financeiro/contas-a-receber/r1');
    });

    it('does not submit when the installments do not sum to the order total', () => {
      const comp = build();
      comp.ngOnInit();
      comp['form'].controls.commercialOrderId.setValue('ord1'); // total 1500
      comp['addInstallment']();
      comp['installments'].controls[0].patchValue({ amount: 100, dueDate: new Date(2026, 6, 15) });

      comp['submit']();

      expect(receivables.create).not.toHaveBeenCalled();
      expect(comp['formError']()).toContain('soma das parcelas');
    });
  });

  describe('DOM rendering', () => {
    it('renders the form fields and the submit/cancel actions', () => {
      const el = render();
      expect(el.textContent).toContain('Nova conta a receber');
      expect(el.textContent).toContain('Pedido');
      expect(el.textContent).toContain('Vencimento');
      expect(el.textContent).toContain('Responsável financeiro');
      expect(el.textContent).toContain('Observações de pagamento');
      expect(el.textContent).toContain('Gerar conta a receber');
      expect(el.textContent).toContain('Cancelar');
      // The installment editor section with its add action.
      expect(el.textContent).toContain('Parcelas');
      expect(el.textContent).toContain('Adicionar parcela');
    });
  });
});
