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

  it('sets every list filter when provided (responsible, origin, dates, value bounds)', () => {
    service
      .list({
        responsible: 'unassigned',
        originId: 'o1',
        createdFrom: '2026-06-01',
        createdTo: '2026-06-30',
        closeFrom: '2026-07-01',
        closeTo: '2026-07-31',
        valueMin: 100,
        valueMax: 5000,
      })
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/opportunities');
    const p = req.request.params;
    expect(p.get('responsible')).toBe('unassigned');
    expect(p.get('originId')).toBe('o1');
    expect(p.get('createdFrom')).toBe('2026-06-01');
    expect(p.get('createdTo')).toBe('2026-06-30');
    expect(p.get('closeFrom')).toBe('2026-07-01');
    expect(p.get('closeTo')).toBe('2026-07-31');
    expect(p.get('valueMin')).toBe('100');
    expect(p.get('valueMax')).toBe('5000');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it('fetches origins and responsibles', () => {
    service.origins().subscribe();
    http.expectOne('/api/crm/origins').flush([]);
    service.responsibles().subscribe();
    http.expectOne('/api/crm/responsibles').flush([]);
  });

  it('gets the opportunity detail', () => {
    service.detail('o1').subscribe();
    const req = http.expectOne('/api/opportunities/o1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('fetches the pending worklist with paging params', () => {
    service.pending(2, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/opportunities/pending');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('fetches indicators with the period and omits absent dates', () => {
    service.indicators('2026-06-01', '2026-06-30').subscribe();
    const withDates = http.expectOne((r) => r.url === '/api/opportunities/indicators');
    expect(withDates.request.params.get('createdFrom')).toBe('2026-06-01');
    expect(withDates.request.params.get('createdTo')).toBe('2026-06-30');
    withDates.flush({});

    service.indicators().subscribe();
    const allTime = http.expectOne((r) => r.url === '/api/opportunities/indicators');
    expect(allTime.request.params.has('createdFrom')).toBe(false);
    expect(allTime.request.params.has('createdTo')).toBe(false);
    allTime.flush({});
  });

  it('posts lose, change-stage, activity and puts the commercial details on the right endpoints', () => {
    service.lose('o1', 'NO_RESPONSE', 'sumiu').subscribe();
    const lose = http.expectOne('/api/opportunities/o1/lose');
    expect(lose.request.method).toBe('POST');
    expect(lose.request.body).toEqual({ reason: 'NO_RESPONSE', note: 'sumiu' });
    lose.flush({});

    service.changeStage('o1', 'DISCOVERY').subscribe();
    const stage = http.expectOne('/api/opportunities/o1/stage');
    expect(stage.request.method).toBe('POST');
    expect(stage.request.body).toEqual({ stage: 'DISCOVERY' });
    stage.flush({});

    const activity = {
      type: 'PHONE_CALL' as const,
      result: 'CLIENT_ENGAGED' as const,
      description: 'ligou',
      occurredAt: '2026-06-15T13:00:00.000Z',
      nextActionDate: null,
    };
    service.registerActivity('o1', activity).subscribe();
    const act = http.expectOne('/api/opportunities/o1/activities');
    expect(act.request.method).toBe('POST');
    expect(act.request.body).toEqual(activity);
    act.flush({});

    const details = { estimatedValue: 9000, expectedCloseDate: null, productType: 'Pacote', notes: null };
    service.updateDetails('o1', details).subscribe();
    const upd = http.expectOne('/api/opportunities/o1');
    expect(upd.request.method).toBe('PUT');
    expect(upd.request.body).toEqual(details);
    upd.flush({});
  });
});
