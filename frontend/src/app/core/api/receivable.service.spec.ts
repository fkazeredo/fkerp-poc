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

  it('omits the status param when no status is chosen (defaults to active on the backend)', () => {
    service.list({}, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/receivables');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('order')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  });
});
