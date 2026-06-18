import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProposalService } from './proposal.service';

describe('ProposalService', () => {
  let service: ProposalService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProposalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a proposal from an opportunity', () => {
    const payload = { opportunityId: 'o1', title: 'Proposta' };
    service.create(payload).subscribe();
    const req = http.expectOne('/api/proposals');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'p1', status: 'DRAFT' });
  });

  it('gets the proposal detail', () => {
    service.detail('p1').subscribe();
    const req = http.expectOne('/api/proposals/p1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('updates the commercial details (PUT)', () => {
    const payload = { validUntil: '2026-12-31', discountType: 'AMOUNT' as const, discountValue: 50 };
    service.updateDetails('p1', payload).subscribe();
    const req = http.expectOne('/api/proposals/p1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('submits for review (POST with empty body)', () => {
    service.submitForReview('p1').subscribe();
    const req = http.expectOne('/api/proposals/p1/submit');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({});
  });

  it('adds, updates and removes an item on the right endpoints', () => {
    const item = { type: 'OTHER' as const, description: 'x', quantity: 1, unitValue: 10 };

    service.addItem('p1', item).subscribe();
    const add = http.expectOne('/api/proposals/p1/items');
    expect(add.request.method).toBe('POST');
    expect(add.request.body).toEqual(item);
    add.flush({});

    service.updateItem('p1', 'i1', item).subscribe();
    const upd = http.expectOne('/api/proposals/p1/items/i1');
    expect(upd.request.method).toBe('PUT');
    upd.flush({});

    service.removeItem('p1', 'i1').subscribe();
    const del = http.expectOne('/api/proposals/p1/items/i1');
    expect(del.request.method).toBe('DELETE');
    del.flush({});
  });

  it('lists proposals with paging params', () => {
    service.list(2, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/proposals');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });
  });
});
