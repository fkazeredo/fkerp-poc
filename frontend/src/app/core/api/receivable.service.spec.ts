import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CreateReceivable, ReceivableService } from './receivable.service';

describe('ReceivableService', () => {
  let service: ReceivableService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReceivableService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches the responsibles', () => {
    service.responsibles().subscribe();
    const req = http.expectOne('/api/crm/responsibles');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('posts the create payload', () => {
    const payload: CreateReceivable = {
      commercialOrderId: 'ord1',
      dueDate: '2026-07-15',
      paymentNotes: 'boleto',
      financialResponsiblePersonId: null,
    };
    service.create(payload).subscribe();
    const req = http.expectOne('/api/receivables');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'r1', status: 'OPEN' });
  });

  it('posts a multi-installment create payload', () => {
    const payload: CreateReceivable = {
      commercialOrderId: 'ord1',
      dueDate: '2026-07-15',
      paymentNotes: null,
      financialResponsiblePersonId: null,
      installments: [
        { amount: 1000, dueDate: '2026-07-15', paymentNotes: 'entrada' },
        { amount: 500, dueDate: '2026-08-15', paymentNotes: null },
      ],
    };
    service.create(payload).subscribe();
    const req = http.expectOne('/api/receivables');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.installments).toHaveLength(2);
    expect(req.request.body.installments[0].amount).toBe(1000);
    req.flush({ id: 'r1', status: 'OPEN' });
  });

  it('fetches a receivable detail by id', () => {
    service.detail('r1').subscribe();
    const req = http.expectOne('/api/receivables/r1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('fetches the eligible orders', () => {
    service.eligibleOrders().subscribe();
    const req = http.expectOne('/api/receivables/eligible-orders');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('fetches the active payment methods cadastro', () => {
    service.paymentMethods().subscribe();
    const req = http.expectOne('/api/financial/payment-methods');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('posts a full payment for an installment', () => {
    service
      .registerPayment('r1', 'inst1', {
        paymentMethodId: 'm1',
        amount: 500,
        paymentDate: '2026-06-01',
        note: 'pix',
      })
      .subscribe();
    const req = http.expectOne('/api/receivables/r1/installments/inst1/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      paymentMethodId: 'm1',
      amount: 500,
      paymentDate: '2026-06-01',
      note: 'pix',
    });
    req.flush({});
  });

  const emptyIndicators = {
    totalReceivablesInPeriod: 0,
    totalToReceive: 0,
    receivedAmount: 0,
    paymentsRegistered: 0,
    paymentsByMethod: [],
    paidReceivablesInPeriod: 0,
    avgDaysToPayment: null,
    byStatus: [],
    outstandingAmount: 0,
    overdueAmount: 0,
    readyForCommission: 0,
  };

  it('fetches the indicators with the period params', () => {
    service.indicators('2026-06-01', '2026-06-30').subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables/indicators');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-06-01');
    expect(req.request.params.get('to')).toBe('2026-06-30');
    req.flush(emptyIndicators);
  });

  it('omits the period params when no dates are given (all-time)', () => {
    service.indicators().subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables/indicators');
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('to')).toBe(false);
    req.flush(emptyIndicators);
  });

  it('posts a payment reversal with the reason to the reversals path', () => {
    service.reversePayment('r1', 'pay1', { reason: 'lançamento duplicado' }).subscribe();
    const req = http.expectOne('/api/receivables/r1/payments/pay1/reversals');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'lançamento duplicado' });
    req.flush({});
  });

  it('builds list query params from the filters (repeated status, order, paging)', () => {
    service.list({ status: ['OPEN', 'OVERDUE'], order: 'ord1' }, 2, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables');
    const params = req.request.params;
    expect(params.getAll('status')).toEqual(['OPEN', 'OVERDUE']);
    expect(params.get('order')).toBe('ord1');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('10');
    req.flush({
      content: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: false,
      last: true,
    });
  });

  it('omits the status param when no status is chosen (defaults to operational on the backend)', () => {
    service.list({}, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('order')).toBe(false);
    expect(req.request.params.has('overdueOnly')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it('builds the operational filter params (payer, source order, periods, responsibles, amount, overdue)', () => {
    service
      .list(
        {
          payer: '  maria ',
          orderNumber: 7,
          dueFrom: '2026-07-01',
          dueTo: '2026-07-31',
          createdFrom: '2026-06-01',
          createdTo: '2026-06-30',
          commercialResponsible: 'u1',
          financialResponsible: 'u5',
          amountMin: 100,
          amountMax: 5000,
          overdueOnly: true,
        },
        0,
        20,
      )
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables');
    const params = req.request.params;
    expect(params.get('payer')).toBe('maria');
    expect(params.get('orderNumber')).toBe('7');
    expect(params.get('dueFrom')).toBe('2026-07-01');
    expect(params.get('dueTo')).toBe('2026-07-31');
    expect(params.get('createdFrom')).toBe('2026-06-01');
    expect(params.get('createdTo')).toBe('2026-06-30');
    expect(params.get('commercialResponsible')).toBe('u1');
    expect(params.get('financialResponsible')).toBe('u5');
    expect(params.get('amountMin')).toBe('100');
    expect(params.get('amountMax')).toBe('5000');
    expect(params.get('overdueOnly')).toBe('true');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  });
});
