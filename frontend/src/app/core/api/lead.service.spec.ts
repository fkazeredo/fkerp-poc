import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LeadService } from './lead.service';

describe('LeadService', () => {
  let service: LeadService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LeadService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches the responsibles', () => {
    service.responsibles().subscribe();
    const req = http.expectOne('/api/crm/responsibles');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('builds list query params from the filters (repeated status, trimmed search, period, paging)', () => {
    service
      .list(
        {
          status: ['NEW', 'CONTACTED'],
          originId: 'o1',
          responsible: 'unassigned',
          createdFrom: '2026-06-01',
          createdTo: '2026-06-30',
          q: '  maria ',
        },
        2,
        10,
      )
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/leads');
    const params = req.request.params;
    expect(params.getAll('status')).toEqual(['NEW', 'CONTACTED']);
    expect(params.get('originId')).toBe('o1');
    expect(params.get('responsible')).toBe('unassigned');
    expect(params.get('createdFrom')).toBe('2026-06-01');
    expect(params.get('createdTo')).toBe('2026-06-30');
    expect(params.get('q')).toBe('maria');
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

  it('omits empty optional filters and defaults page/size', () => {
    service.list({ status: [], q: '   ' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/leads');
    const params = req.request.params;
    expect(params.has('status')).toBe(false);
    expect(params.has('q')).toBe(false);
    expect(params.has('originId')).toBe(false);
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
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
