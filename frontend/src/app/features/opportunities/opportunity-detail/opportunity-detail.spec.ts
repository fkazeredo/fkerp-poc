import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { OpportunityDetailPage } from './opportunity-detail';
import { OpportunityDetail, OpportunityService } from '../../../core/api/opportunity.service';
import { ReferenceService } from '../../../core/api/reference.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('OpportunityDetailPage', () => {
  const opportunities = {
    detail: vi.fn(),
    lose: vi.fn(),
    changeStage: vi.fn(),
    registerActivity: vi.fn(),
  };
  const references = { list: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };
  const auth = { canOperateOpportunity: vi.fn() };

  const sample = (over: Partial<OpportunityDetail> = {}): OpportunityDetail => ({
    id: 'o1',
    leadId: 'l1',
    name: 'Aurora',
    stage: 'NEW_OPPORTUNITY',
    responsibleId: 'u1',
    responsibleName: 'comercial',
    unassigned: false,
    origin: 'Website',
    mainInterest: 'Pacote corporativo',
    productType: null,
    estimatedValue: 5000,
    expectedCloseDate: null,
    notes: null,
    createdAt: '2026-06-15T10:00:00Z',
    updatedAt: '2026-06-15T10:00:00Z',
    sourceLead: {
      id: 'l1',
      name: 'Lead Aurora',
      phone: '11999990000',
      whatsapp: null,
      email: 'aurora@example.com',
      status: 'NEW',
    },
    loss: null,
    activities: [],
    stageHistory: [],
    nextActionDate: null,
    ...over,
  });

  const lost = () =>
    sample({
      stage: 'LOST',
      loss: { reason: 'Sem resposta', lostAt: '2026-06-16T10:00:00Z', lostBy: 'comercial', note: 'sumiu' },
    });

  function build() {
    TestBed.configureTestingModule({
      imports: [OpportunityDetailPage],
      providers: [
        providePrimeNG(),
        { provide: OpportunityService, useValue: opportunities },
        { provide: ReferenceService, useValue: references },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: messages },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o1' } } } },
      ],
    });
    return TestBed.createComponent(OpportunityDetailPage).componentInstance;
  }

  beforeEach(() => {
    [
      opportunities.detail,
      opportunities.lose,
      opportunities.changeStage,
      opportunities.registerActivity,
      references.list,
      router.navigateByUrl,
      messages.add,
      auth.canOperateOpportunity,
    ].forEach((fn) => fn.mockReset());
    opportunities.detail.mockReturnValue(of(sample()));
    references.list.mockReturnValue(
      of([{ id: 'lr1', code: 'NO_RESPONSE', label: 'Sem resposta', active: true, sortOrder: 1 }]),
    );
    auth.canOperateOpportunity.mockReturnValue(true);
  });

  it('loads the detail on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(opportunities.detail).toHaveBeenCalledWith('o1');
    expect(comp['opportunity']()?.name).toBe('Aurora');
    expect(comp['opportunity']()?.sourceLead.email).toBe('aurora@example.com');
    expect(comp['loading']()).toBe(false);
  });

  it('shows a permission message on 403 and no opportunity', () => {
    opportunities.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
    expect(comp['opportunity']()).toBeNull();
  });

  it('shows a not-found message on 404', () => {
    opportunities.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  it('allows losing only when operable and not already lost', () => {
    const comp = build();
    comp.ngOnInit(); // NEW_OPPORTUNITY
    expect(comp['canLose']()).toBe(true);
    comp['opportunity'].set(lost());
    expect(comp['canLose']()).toBe(false);
  });

  it('hides the lose action for consultation-only users', () => {
    auth.canOperateOpportunity.mockReturnValue(false);
    const comp = build();
    comp.ngOnInit();
    expect(comp['canLose']()).toBe(false);
  });

  it('loads loss reasons when opening the lose dialog', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openLose']();
    expect(references.list).toHaveBeenCalledWith('loss-reasons');
    expect(comp['loseOpen']()).toBe(true);
    expect(comp['lossReasons']().length).toBe(1);
  });

  it('confirmLose marks as lost, refreshes the detail and closes the dialog', () => {
    opportunities.lose.mockReturnValue(of(lost()));
    const comp = build();
    comp.ngOnInit();
    comp['lossReasonId'] = 'lr1';
    comp['lossNote'] = 'sumiu';

    comp['confirmLose']();

    expect(opportunities.lose).toHaveBeenCalledWith('o1', 'lr1', 'sumiu');
    expect(comp['opportunity']()?.stage).toBe('LOST');
    expect(comp['loseOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalled();
  });

  it('confirmLose does nothing without a reason', () => {
    const comp = build();
    comp.ngOnInit();
    comp['lossReasonId'] = null;
    comp['confirmLose']();
    expect(opportunities.lose).not.toHaveBeenCalled();
  });

  it('stageOptions offers only the next stage in the funnel', () => {
    const comp = build();
    comp.ngOnInit(); // NEW_OPPORTUNITY → only DISCOVERY
    expect(comp['stageOptions']().map((o) => o.value)).toEqual(['DISCOVERY']);
    comp['opportunity'].set(sample({ stage: 'PRODUCT_FIT' }));
    expect(comp['stageOptions']().map((o) => o.value)).toEqual(['READY_FOR_PROPOSAL']);
  });

  it('allows advancing only when operable and a next stage exists', () => {
    const comp = build();
    comp.ngOnInit(); // NEW → has a forward step
    expect(comp['canChangeStage']()).toBe(true);
    comp['opportunity'].set(sample({ stage: 'READY_FOR_PROPOSAL' }));
    expect(comp['canChangeStage']()).toBe(false); // end of the funnel
    comp['opportunity'].set(lost());
    expect(comp['canChangeStage']()).toBe(false);
  });

  it('openStage pre-selects the next stage', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openStage']();
    expect(comp['targetStage']).toBe('DISCOVERY');
    expect(comp['stageOpen']()).toBe(true);
  });

  it('confirmStage advances the opportunity, refreshes the detail and closes the dialog', () => {
    const moved = sample({
      stage: 'DISCOVERY',
      stageHistory: [{ from: 'NEW_OPPORTUNITY', to: 'DISCOVERY', at: '2026-06-16T10:00:00Z', by: 'comercial' }],
    });
    opportunities.changeStage.mockReturnValue(of(moved));
    const comp = build();
    comp.ngOnInit();
    comp['openStage'](); // pre-selects DISCOVERY

    comp['confirmStage']();

    expect(opportunities.changeStage).toHaveBeenCalledWith('o1', 'DISCOVERY');
    expect(comp['opportunity']()?.stage).toBe('DISCOVERY');
    expect(comp['opportunity']()?.stageHistory.length).toBe(1);
    expect(comp['stageOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalled();
  });

  it('confirmStage does nothing without a target stage', () => {
    const comp = build();
    comp.ngOnInit();
    comp['targetStage'] = null;
    comp['confirmStage']();
    expect(opportunities.changeStage).not.toHaveBeenCalled();
  });

  it('maps stages to pt-BR labels', () => {
    const comp = build();
    expect(comp['stageLabel']('NEW_OPPORTUNITY')).toBe('Nova');
    expect(comp['stageLabel']('LOST')).toBe('Perdida');
  });

  it('allows registering an activity only when operable', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['canRegisterActivity']()).toBe(true);
    auth.canOperateOpportunity.mockReturnValue(false);
    expect(comp['canRegisterActivity']()).toBe(false);
  });

  it('openActivity resets the form and opens the dialog', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openActivity']();
    expect(comp['activityOpen']()).toBe(true);
    expect(comp['activityType']).toBeNull();
    expect(comp['activityResult']).toBeNull();
    expect(comp['activityDescription']).toBe('');
  });

  it('confirmActivity registers the activity, refreshes the detail and closes the dialog', () => {
    const withActivity = sample({
      activities: [
        {
          id: 'a1',
          type: 'PHONE_CALL',
          result: 'CLIENT_ENGAGED',
          description: 'ligação',
          occurredAt: '2026-06-10T10:00:00Z',
          nextActionDate: '2026-06-20',
          registeredBy: 'comercial',
        },
      ],
      nextActionDate: '2026-06-20',
    });
    opportunities.registerActivity.mockReturnValue(of(withActivity));
    const comp = build();
    comp.ngOnInit();
    comp['openActivity']();
    comp['activityType'] = 'PHONE_CALL';
    comp['activityResult'] = 'CLIENT_ENGAGED';
    comp['activityDescription'] = 'ligação';

    comp['confirmActivity']();

    expect(opportunities.registerActivity).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ type: 'PHONE_CALL', result: 'CLIENT_ENGAGED', description: 'ligação' }),
    );
    expect(comp['opportunity']()?.activities.length).toBe(1);
    expect(comp['activityOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalled();
  });

  it('confirmActivity does nothing when required fields are missing', () => {
    const comp = build();
    comp.ngOnInit();
    comp['activityType'] = null;
    comp['confirmActivity']();
    expect(opportunities.registerActivity).not.toHaveBeenCalled();
  });

  it('maps activity type and result to pt-BR labels', () => {
    const comp = build();
    expect(comp['activityTypeLabel']('MEETING')).toBe('Reunião');
    expect(comp['activityResultLabel']('READY_FOR_PROPOSAL')).toBe('Pronta para proposta');
  });

  it('back navigates to the opportunity list', () => {
    const comp = build();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/oportunidades');
  });
});
