import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { OpportunityDetailPage } from './opportunity-detail';
import { OpportunityDetail, OpportunityService } from '../../../core/api/opportunity.service';
import { ProposalService } from '../../../core/api/proposal.service';
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
    updateDetails: vi.fn(),
    responsibles: vi.fn(),
  };
  const proposalService = { create: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };
  const auth = { canOperateOpportunity: vi.fn(), canCreateProposal: vi.fn() };

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
      loss: { reason: 'NO_RESPONSE', lostAt: '2026-06-16T10:00:00Z', lostBy: 'comercial', note: 'sumiu' },
    });

  function build() {
    TestBed.configureTestingModule({
      imports: [OpportunityDetailPage],
      providers: [
        providePrimeNG(),
        ConfirmationService,
        { provide: OpportunityService, useValue: opportunities },
        { provide: ProposalService, useValue: proposalService },
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
      opportunities.updateDetails,
      opportunities.responsibles,
      proposalService.create,
      router.navigateByUrl,
      messages.add,
      auth.canOperateOpportunity,
      auth.canCreateProposal,
    ].forEach((fn) => fn.mockReset());
    opportunities.detail.mockReturnValue(of(sample()));
    opportunities.responsibles.mockReturnValue(of([]));
    auth.canOperateOpportunity.mockReturnValue(true);
    auth.canCreateProposal.mockReturnValue(true);
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

  it('openLose opens the dialog with the loss-reason options', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openLose']();
    expect(comp['loseOpen']()).toBe(true);
    expect(comp['lossReason']).toBeNull();
    expect(comp['lossReasonOptions'].map((o) => o.value)).toContain('NO_BUDGET');
  });

  it('confirmLose marks as lost, refreshes the detail and closes the dialog', () => {
    opportunities.lose.mockReturnValue(of(lost()));
    const comp = build();
    comp.ngOnInit();
    comp['lossReason'] = 'COMPETITOR_CHOSEN';
    comp['lossNote'] = 'sumiu';

    comp['confirmLose']();

    expect(opportunities.lose).toHaveBeenCalledWith('o1', 'COMPETITOR_CHOSEN', 'sumiu');
    expect(comp['opportunity']()?.stage).toBe('LOST');
    expect(comp['loseOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalled();
  });

  it('confirmLose does nothing without a reason', () => {
    const comp = build();
    comp.ngOnInit();
    comp['lossReason'] = null;
    comp['confirmLose']();
    expect(opportunities.lose).not.toHaveBeenCalled();
  });

  it('maps loss reasons to pt-BR labels', () => {
    const comp = build();
    expect(comp['lossReasonLabel']('NO_BUDGET')).toBe('Sem orçamento');
    expect(comp['lossReasonLabel']('TRAVEL_CANCELLED')).toBe('Viagem cancelada');
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

  it('allows editing commercial details only when operable', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['canEditDetails']()).toBe(true);
    auth.canOperateOpportunity.mockReturnValue(false);
    expect(comp['canEditDetails']()).toBe(false);
  });

  it('openEdit pre-fills the form from the current opportunity', () => {
    opportunities.detail.mockReturnValue(
      of(
        sample({
          estimatedValue: 5000,
          expectedCloseDate: '2026-09-30',
          productType: 'Pacote',
          notes: 'nota',
        }),
      ),
    );
    const comp = build();
    comp.ngOnInit();
    comp['openEdit']();
    expect(comp['editOpen']()).toBe(true);
    expect(comp['editEstimatedValue']).toBe(5000);
    expect(comp['editProductType']).toBe('Pacote');
    expect(comp['editNotes']).toBe('nota');
    expect(comp['editExpectedCloseDate']).toBeInstanceOf(Date);
  });

  it('confirmEdit updates the details, refreshes the detail and closes the dialog', () => {
    opportunities.updateDetails.mockReturnValue(of(sample({ estimatedValue: 8000, productType: 'Novo' })));
    const comp = build();
    comp.ngOnInit();
    comp['openEdit']();
    comp['editEstimatedValue'] = 8000;
    comp['editProductType'] = 'Novo';

    comp['confirmEdit']();

    expect(opportunities.updateDetails).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ estimatedValue: 8000, productType: 'Novo' }),
    );
    expect(comp['opportunity']()?.estimatedValue).toBe(8000);
    expect(comp['editOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalled();
  });

  it('back navigates to the opportunity list', () => {
    const comp = build();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/oportunidades');
  });

  it('offers "Criar proposta" only on a ready opportunity and with the scope', () => {
    const comp = build();
    comp.ngOnInit(); // NEW_OPPORTUNITY
    expect(comp['canCreateProposal']()).toBe(false);
    comp['opportunity'].set(sample({ stage: 'READY_FOR_PROPOSAL' }));
    expect(comp['canCreateProposal']()).toBe(true);
    auth.canCreateProposal.mockReturnValue(false);
    expect(comp['canCreateProposal']()).toBe(false);
  });

  it('confirmProposal creates the proposal and opens it', () => {
    proposalService.create.mockReturnValue(of({ id: 'p1', status: 'DRAFT' }));
    const comp = build();
    comp.ngOnInit();
    comp['opportunity'].set(sample({ stage: 'READY_FOR_PROPOSAL' }));
    comp['openProposal'](); // pre-fills the title from the opportunity name
    expect(comp['proposalTitle']).toBe('Aurora');

    comp['confirmProposal']();

    expect(proposalService.create).toHaveBeenCalledWith(
      expect.objectContaining({ opportunityId: 'o1', title: 'Aurora' }),
    );
    expect(comp['proposalOpen']()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/propostas/p1');
    expect(messages.add).toHaveBeenCalled();
  });

  it('confirmProposal does nothing without a title', () => {
    const comp = build();
    comp.ngOnInit();
    comp['proposalTitle'] = '   ';
    comp['confirmProposal']();
    expect(proposalService.create).not.toHaveBeenCalled();
  });
});
