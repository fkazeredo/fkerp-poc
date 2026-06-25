import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CommissionService } from './commission.service';

describe('CommissionService', () => {
  let service: CommissionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CommissionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the source order id to generate an expected commission', () => {
    service.generate('ord1').subscribe();
    const req = http.expectOne('/api/commissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ commercialOrderId: 'ord1' });
    req.flush({ id: 'c1' });
  });

  it('gets the commission detail by id', () => {
    service.detail('c1').subscribe();
    const req = http.expectOne('/api/commissions/c1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('posts the optional notes to approve an eligible commission', () => {
    service.approve('c1', 'Conferido').subscribe();
    const req = http.expectOne('/api/commissions/c1/approve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ notes: 'Conferido' });
    req.flush({ id: 'c1', status: 'APPROVED' });
  });

  it('approves with null notes when none are given', () => {
    service.approve('c1', null).subscribe();
    const req = http.expectOne('/api/commissions/c1/approve');
    expect(req.request.body).toEqual({ notes: null });
    req.flush({ id: 'c1', status: 'APPROVED' });
  });

  it('posts the reason and optional note to reject a commission', () => {
    service.reject('c1', 'reason-1', 'Duplicada').subscribe();
    const req = http.expectOne('/api/commissions/c1/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reasonId: 'reason-1', note: 'Duplicada' });
    req.flush({ id: 'c1', status: 'REJECTED' });
  });

  it('posts the reason and optional note to cancel a commission', () => {
    service.cancel('c1', 'reason-2', null).subscribe();
    const req = http.expectOne('/api/commissions/c1/cancel');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reasonId: 'reason-2', note: null });
    req.flush({ id: 'c1', status: 'CANCELLED' });
  });

  it('lists commissions with paging and the chosen filters', () => {
    service
      .list({ status: ['ELIGIBLE', 'APPROVED'], beneficiary: 'u2', orderNumber: 7, amountMin: 10 }, 1, 20)
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/commissions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.getAll('status')).toEqual(['ELIGIBLE', 'APPROVED']);
    expect(req.request.params.get('beneficiary')).toBe('u2');
    expect(req.request.params.get('orderNumber')).toBe('7');
    expect(req.request.params.get('amountMin')).toBe('10');
    req.flush({ content: [], totalElements: 0 });
  });

  it('looks up the order commission via the list and maps to the first item', () => {
    let result: unknown = 'unset';
    service.byOrder('ord1').subscribe((c) => (result = c));
    const req = http.expectOne((r) => r.url === '/api/commissions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('order')).toBe('ord1');
    // It asks for the active statuses (so a PAID commission is still shown on the order detail).
    expect(req.request.params.getAll('status')).toEqual(['EXPECTED', 'ELIGIBLE', 'APPROVED', 'PAID']);
    req.flush({ content: [{ id: 'c1', status: 'ELIGIBLE' }], totalElements: 1 });
    expect(result).toEqual({ id: 'c1', status: 'ELIGIBLE' });
  });

  it('maps an empty order-commission lookup to null', () => {
    let result: unknown = 'unset';
    service.byOrder('ord1').subscribe((c) => (result = c));
    http.expectOne((r) => r.url === '/api/commissions').flush({ content: [], totalElements: 0 });
    expect(result).toBeNull();
  });
});
