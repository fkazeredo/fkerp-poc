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
});
