import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CommissionRuleService, SaveCommissionRule } from './commission-rule.service';

describe('CommissionRuleService', () => {
  let service: CommissionRuleService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CommissionRuleService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists active rules by default', () => {
    service.list().subscribe();
    const req = http.expectOne((r) => r.url === '/api/commission/rules');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('includeInactive')).toBe('false');
    req.flush([]);
  });

  it('lists including inactive when asked', () => {
    service.list(true).subscribe();
    const req = http.expectOne((r) => r.url === '/api/commission/rules');
    expect(req.request.params.get('includeInactive')).toBe('true');
    req.flush([]);
  });

  it('posts the create payload', () => {
    const payload: SaveCommissionRule = {
      name: 'Padrão',
      percentage: 5,
      targetType: 'SELLER',
      targetUserId: null,
      startDate: '2026-01-01',
      endDate: null,
      notes: null,
      allowAboveLimit: false,
    };
    service.create(payload).subscribe();
    const req = http.expectOne('/api/commission/rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'r1', active: true });
  });

  it('puts the update payload', () => {
    service
      .update('r1', { name: 'X', percentage: 7, targetType: 'SELLER', startDate: '2026-01-01' })
      .subscribe();
    const req = http.expectOne('/api/commission/rules/r1');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('activates and deactivates a rule', () => {
    service.activate('r1').subscribe();
    const a = http.expectOne('/api/commission/rules/r1/activate');
    expect(a.request.method).toBe('POST');
    a.flush({});

    service.deactivate('r1').subscribe();
    const d = http.expectOne('/api/commission/rules/r1/deactivate');
    expect(d.request.method).toBe('POST');
    d.flush({});
  });

  it('fetches the responsibles lookup', () => {
    service.responsibles().subscribe();
    const req = http.expectOne('/api/crm/responsibles');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
