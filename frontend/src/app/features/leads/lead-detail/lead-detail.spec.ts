import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { LeadDetailPage } from './lead-detail';
import { LeadService } from '../../../core/api/lead.service';
import { ReferenceService } from '../../../core/api/reference.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('LeadDetailPage', () => {
  const leads = {
    detail: vi.fn(),
    qualify: vi.fn(),
    lose: vi.fn(),
    reassign: vi.fn(),
    recordInteraction: vi.fn(),
    responsibles: vi.fn(),
  };
  const references = { list: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };
  const auth = { hasScope: vi.fn(), userId: vi.fn() };

  const sample = (over: Record<string, unknown> = {}) => ({
    id: 'l1',
    name: 'Alpha',
    phone: '11999990000',
    whatsapp: null,
    email: null,
    origin: 'Website',
    status: 'NEW',
    responsibleId: null,
    responsibleName: null,
    unassigned: true,
    createdAt: '2026-06-15T10:00:00Z',
    updatedAt: '2026-06-15T10:00:00Z',
    nextContactAt: null,
    interactions: [],
    assignments: [],
    qualification: null,
    loss: null,
    ...over,
  });

  function build() {
    TestBed.configureTestingModule({
      imports: [LeadDetailPage],
      providers: [
        providePrimeNG(),
        { provide: LeadService, useValue: leads },
        { provide: ReferenceService, useValue: references },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: messages },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'l1' } } } },
      ],
    });
    return TestBed.createComponent(LeadDetailPage).componentInstance;
  }

  beforeEach(() => {
    [
      leads.detail,
      leads.qualify,
      leads.lose,
      leads.reassign,
      leads.recordInteraction,
      leads.responsibles,
      references.list,
      router.navigateByUrl,
      messages.add,
      auth.hasScope,
      auth.userId,
    ].forEach((fn) => fn.mockReset());
    leads.detail.mockReturnValue(of(sample()));
    leads.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
    references.list.mockImplementation((path: string) => {
      if (path === 'interaction-types') {
        return of([{ id: 't1', code: 'PHONE_CALL', label: 'Ligação', active: true, sortOrder: 1 }]);
      }
      if (path === 'interaction-results') {
        return of([{ id: 'r1', code: 'CONTACT_MADE', label: 'Contato realizado', active: true, sortOrder: 1 }]);
      }
      return of([{ id: 'lr1', code: 'NO_RESPONSE', label: 'Sem resposta', active: true, sortOrder: 1 }]);
    });
    auth.hasScope.mockReturnValue(false);
    auth.userId.mockReturnValue('rep-1');
  });

  it('loads the detail on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(leads.detail).toHaveBeenCalledWith('l1');
    expect(comp['lead']()?.name).toBe('Alpha');
    expect(comp['loading']()).toBe(false);
  });

  it('shows a permission message on 403 and no lead', () => {
    leads.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
    expect(comp['lead']()).toBeNull();
  });

  it('enables actions only for the appropriate status', () => {
    const comp = build();
    comp.ngOnInit(); // NEW + unassigned
    expect(comp['canQualify']()).toBe(false); // qualify needs CONTACTED + responsible
    expect(comp['canLose']()).toBe(true);
    expect(comp['canReassign']()).toBe(true);

    leads.detail.mockReturnValue(
      of(sample({ status: 'CONTACTED', responsibleId: 'u1', responsibleName: 'comercial', unassigned: false })),
    );
    comp.ngOnInit();
    expect(comp['canQualify']()).toBe(true);

    leads.detail.mockReturnValue(of(sample({ status: 'CONTACTED' }))); // still unassigned
    comp.ngOnInit();
    expect(comp['canQualify']()).toBe(false);

    leads.detail.mockReturnValue(of(sample({ status: 'LOST' })));
    comp.ngOnInit();
    expect(comp['canQualify']()).toBe(false);
    expect(comp['canLose']()).toBe(false);
    expect(comp['canReassign']()).toBe(false);
  });

  it('qualifies with the main interest, refreshes the lead and toasts', () => {
    leads.qualify.mockReturnValue(of(sample({ status: 'QUALIFIED' })));
    const comp = build();
    comp.ngOnInit();
    comp['qualifyMainInterest'] = 'Pacote corporativo';
    comp['qualifyNote'] = 'bom perfil';

    comp['confirmQualify']();

    expect(leads.qualify).toHaveBeenCalledWith('l1', 'Pacote corporativo', 'bom perfil');
    expect(comp['lead']()?.status).toBe('QUALIFIED');
    expect(comp['qualifyOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('does not qualify without a main interest', () => {
    const comp = build();
    comp.ngOnInit();
    comp['qualifyMainInterest'] = '   ';

    comp['confirmQualify']();

    expect(leads.qualify).not.toHaveBeenCalled();
  });

  it('does not mark lost without a reason', () => {
    const comp = build();
    comp.ngOnInit();
    comp['lossReasonId'] = null;

    comp['confirmLose']();

    expect(leads.lose).not.toHaveBeenCalled();
  });

  it('marks lost with the selected reason', () => {
    leads.lose.mockReturnValue(of(sample({ status: 'LOST' })));
    const comp = build();
    comp.ngOnInit();
    comp['openLose']();
    comp['lossReasonId'] = 'r1';

    comp['confirmLose']();

    expect(leads.lose).toHaveBeenCalledWith('l1', 'r1', null);
    expect(comp['lead']()?.status).toBe('LOST');
  });

  it('reassigns the responsible', () => {
    leads.reassign.mockReturnValue(
      of(sample({ responsibleId: 'u1', responsibleName: 'comercial', unassigned: false })),
    );
    const comp = build();
    comp.ngOnInit();
    comp['reassignTo'] = 'u1';

    comp['confirmReassign']();

    expect(leads.reassign).toHaveBeenCalledWith('l1', 'u1');
    expect(comp['lead']()?.unassigned).toBe(false);
  });

  it('offers Reatribuir (not Assumir) to a manager with crm:lead:assign', () => {
    auth.hasScope.mockImplementation((s: string) => s === 'crm:lead:assign');
    const comp = build();
    comp.ngOnInit();

    expect(comp['canAssign']()).toBe(true);
    expect(comp['canClaim']()).toBe(false);
  });

  it('offers Assumir (not Reatribuir) to a rep on an unassigned lead', () => {
    const comp = build(); // hasScope=false by default, lead is unassigned/NEW
    comp.ngOnInit();

    expect(comp['canAssign']()).toBe(false);
    expect(comp['canClaim']()).toBe(true);
  });

  it('does not offer Assumir to a rep on an already-assigned lead', () => {
    leads.detail.mockReturnValue(
      of(sample({ responsibleId: 'u9', responsibleName: 'outro', unassigned: false })),
    );
    const comp = build();
    comp.ngOnInit();

    expect(comp['canClaim']()).toBe(false);
  });

  it('does not offer Assumir to a rep on a lost lead', () => {
    leads.detail.mockReturnValue(of(sample({ status: 'LOST' })));
    const comp = build();
    comp.ngOnInit();

    expect(comp['canClaim']()).toBe(false);
  });

  it('claims the lead for the current user (self-assign) and toasts', () => {
    leads.reassign.mockReturnValue(
      of(sample({ responsibleId: 'rep-1', responsibleName: 'vendedor', unassigned: false })),
    );
    const comp = build();
    comp.ngOnInit();

    comp['claim']();

    expect(leads.reassign).toHaveBeenCalledWith('l1', 'rep-1');
    expect(comp['lead']()?.unassigned).toBe(false);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('does not claim when the current user id is unavailable', () => {
    auth.userId.mockReturnValue(null);
    const comp = build();
    comp.ngOnInit();

    comp['claim']();

    expect(leads.reassign).not.toHaveBeenCalled();
  });

  it('loads interaction types and results when opening the dialog', () => {
    const comp = build();
    comp.ngOnInit();

    comp['openInteraction']();

    expect(comp['interactionOpen']()).toBe(true);
    expect(references.list).toHaveBeenCalledWith('interaction-types');
    expect(references.list).toHaveBeenCalledWith('interaction-results');
    expect(comp['interactionTypes']().length).toBe(1);
    expect(comp['interactionResults']().length).toBe(1);
  });

  it('does not register an interaction until type, result and description are filled', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openInteraction']();
    comp['interactionTypeId'] = 't1';
    comp['interactionResultId'] = 'r1';
    comp['interactionDescription'] = '   ';

    expect(comp['canSaveInteraction']()).toBe(false);
    comp['confirmInteraction']();

    expect(leads.recordInteraction).not.toHaveBeenCalled();
  });

  it('registers an interaction and refreshes to the resulting status', () => {
    leads.recordInteraction.mockReturnValue(of(sample({ status: 'CONTACTED' })));
    const comp = build();
    comp.ngOnInit();
    comp['openInteraction']();
    comp['interactionTypeId'] = 't1';
    comp['interactionResultId'] = 'r1';
    comp['interactionDescription'] = 'Conversamos';
    comp['interactionOccurredAt'] = new Date('2026-06-15T13:00:00Z');
    comp['interactionNextContactAt'] = null;

    comp['confirmInteraction']();

    expect(leads.recordInteraction).toHaveBeenCalledWith(
      'l1',
      expect.objectContaining({
        typeId: 't1',
        resultId: 'r1',
        description: 'Conversamos',
        occurredAt: '2026-06-15T13:00:00.000Z',
        nextContactAt: null,
      }),
    );
    expect(comp['lead']()?.status).toBe('CONTACTED');
    expect(comp['interactionOpen']()).toBe(false);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('opens the interaction dialog with the "i" shortcut', () => {
    const comp = build();
    comp.ngOnInit();

    comp['onShortcut'](new KeyboardEvent('keydown', { key: 'i' }));

    expect(comp['interactionOpen']()).toBe(true);
  });

  it('ignores contextual shortcuts while a dialog is already open', () => {
    leads.detail.mockReturnValue(
      of(sample({ status: 'CONTACTED', responsibleId: 'u1', responsibleName: 'comercial', unassigned: false })),
    );
    const comp = build();
    comp.ngOnInit();
    comp['interactionOpen'].set(true);

    comp['onShortcut'](new KeyboardEvent('keydown', { key: 'q' }));

    expect(comp['qualifyOpen']()).toBe(false);
  });
});
