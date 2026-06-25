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
});
