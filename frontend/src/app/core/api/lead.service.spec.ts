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

  it('gets the lead detail', () => {
    service.detail('l1').subscribe();
    const req = http.expectOne('/api/leads/l1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('fetches the pending worklist with paging params', () => {
    service.pending(1, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/leads/pending');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
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

  it('posts qualify with the main interest and note', () => {
    service.qualify('l1', 'Pacote corporativo', 'bom perfil').subscribe();
    const req = http.expectOne('/api/leads/l1/qualify');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mainInterest: 'Pacote corporativo', note: 'bom perfil' });
    req.flush({});
  });

  it('posts lose with the reason and note', () => {
    service.lose('l1', 'r1', null).subscribe();
    const req = http.expectOne('/api/leads/l1/lose');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ lossReasonId: 'r1', note: null });
    req.flush({});
  });

  it('posts reassign with the responsible id', () => {
    service.reassign('l1', 'u1').subscribe();
    const req = http.expectOne('/api/leads/l1/reassign');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ responsiblePersonId: 'u1' });
    req.flush({});
  });

  it('posts a registered interaction with type, result, description and dates', () => {
    const body = {
      typeId: 't1',
      resultId: 'r1',
      description: 'Conversamos',
      occurredAt: '2026-06-15T13:00:00.000Z',
      nextContactAt: '2026-06-18T13:00:00.000Z',
    };
    service.recordInteraction('l1', body).subscribe();
    const req = http.expectOne('/api/leads/l1/interactions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });
});
