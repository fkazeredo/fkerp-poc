import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { WorkflowService } from './workflow.service';

describe('WorkflowService', () => {
  let service: WorkflowService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WorkflowService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WorkflowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the configurable workflows', () => {
    let result: unknown;
    service.list().subscribe((r) => (result = r));
    const req = http.expectOne('/api/workflows');
    expect(req.request.method).toBe('GET');
    req.flush([{ code: 'opportunity', label: 'Oportunidade' }]);
    expect(result).toEqual([{ code: 'opportunity', label: 'Oportunidade' }]);
  });

  it('loads a workflow detail by code', () => {
    service.detail('opportunity').subscribe();
    const req = http.expectOne('/api/workflows/opportunity');
    expect(req.request.method).toBe('GET');
    req.flush({ code: 'opportunity', label: 'Op', states: [], transitions: [], attentionRules: [] });
  });

  it('loads the authoring catalog', () => {
    service.catalog().subscribe();
    const req = http.expectOne('/api/workflows/catalog');
    expect(req.request.method).toBe('GET');
    req.flush({ attentionConditions: {}, guardKeys: [], postFunctionKeys: [] });
  });

  it('creates an attention rule with the full body and returns the new id', () => {
    let created: unknown;
    service
      .createAttentionRule('opportunity', {
        conditionKey: 'NO_RECENT_ACTIVITY',
        thresholdDays: 14,
        stateValue: null,
        code: 'CUSTOM_REASON',
        label: 'Motivo custom',
        sortOrder: 9,
      })
      .subscribe((r) => (created = r));
    const req = http.expectOne('/api/workflows/opportunity/attention-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      conditionKey: 'NO_RECENT_ACTIVITY',
      thresholdDays: 14,
      stateValue: null,
      code: 'CUSTOM_REASON',
      label: 'Motivo custom',
      sortOrder: 9,
    });
    req.flush({ id: 'rule-1' });
    expect(created).toEqual({ id: 'rule-1' });
  });

  it('updates an attention rule (label/threshold/order/active)', () => {
    service
      .updateAttentionRule('rule-1', { label: 'Novo', thresholdDays: 7, sortOrder: 2, active: false })
      .subscribe();
    const req = http.expectOne('/api/workflows/attention-rules/rule-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ label: 'Novo', thresholdDays: 7, sortOrder: 2, active: false });
    req.flush(null);
  });

  it('deletes an attention rule', () => {
    service.deleteAttentionRule('rule-1').subscribe();
    const req = http.expectOne('/api/workflows/attention-rules/rule-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('updates a state (label/order/active)', () => {
    service.updateState('state-1', { label: 'Nova', sortOrder: 3, active: true }).subscribe();
    const req = http.expectOne('/api/workflows/states/state-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ label: 'Nova', sortOrder: 3, active: true });
    req.flush(null);
  });
});
