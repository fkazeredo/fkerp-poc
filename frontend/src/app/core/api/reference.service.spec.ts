import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReferenceService } from './reference.service';

describe('ReferenceService', () => {
  let service: ReferenceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReferenceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists active items by default and includes inactive when asked', () => {
    service.list('origins').subscribe();
    http.expectOne('/api/crm/origins?includeInactive=false').flush([]);

    service.list('origins', true).subscribe();
    http.expectOne('/api/crm/origins?includeInactive=true').flush([]);
  });

  it('creates a reference item', () => {
    const body = { code: 'WEBSITE', label: 'Website', sortOrder: 1 };
    service.create('origins', body).subscribe();
    const req = http.expectOne('/api/crm/origins');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('updates a reference item by id', () => {
    const body = { label: 'Website', sortOrder: 2, active: true };
    service.update('origins', 'i1', body).subscribe();
    const req = http.expectOne('/api/crm/origins/i1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('deactivates a reference item by id', () => {
    service.deactivate('origins', 'i1').subscribe();
    const req = http.expectOne('/api/crm/origins/i1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
