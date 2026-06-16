import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OpportunityService } from './opportunity.service';

describe('OpportunityService', () => {
  let service: OpportunityService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OpportunityService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the opportunity creation payload to /api/opportunities', () => {
    service
      .create({
        leadId: 'l1',
        responsiblePersonId: 'u1',
        productType: 'Software',
        estimatedValue: 1500,
        expectedCloseDate: '2026-07-01',
        initialNote: 'nota',
      })
      .subscribe();

    const req = http.expectOne('/api/opportunities');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.leadId).toBe('l1');
    expect(req.request.body.estimatedValue).toBe(1500);
    req.flush({ id: 'o1', stage: 'NEW_OPPORTUNITY' });
  });

  it('builds the list query with page/size, repeated stage and a trimmed search', () => {
    service.list({ stage: ['NEW_OPPORTUNITY', 'LOST'], q: '  acme  ' }, 1, 10).subscribe();

    const req = http.expectOne((r) => r.url === '/api/opportunities');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.getAll('stage')).toEqual(['NEW_OPPORTUNITY', 'LOST']);
    expect(req.request.params.get('q')).toBe('acme');
    req.flush({
      content: [],
      page: 1,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: false,
      last: true,
    });
  });

  it('omits the search param when blank and defaults page/size', () => {
    service.list({ q: '   ' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/opportunities');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('stage')).toBe(false);
    req.flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
  });
});
