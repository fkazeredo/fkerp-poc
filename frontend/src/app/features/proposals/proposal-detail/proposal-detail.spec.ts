import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { ProposalDetailPage } from './proposal-detail';
import { ProposalDetail, ProposalItem, ProposalService } from '../../../core/api/proposal.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ProposalDetailPage', () => {
  const proposals = {
    detail: vi.fn(),
    addItem: vi.fn(),
    updateItem: vi.fn(),
    removeItem: vi.fn(),
    updateDetails: vi.fn(),
    submitForReview: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn() };
  const auth = { canOperateProposal: vi.fn() };

  const sample: ProposalDetail = {
    id: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    status: 'DRAFT',
    responsibleId: 'u1',
    responsibleName: 'comercial',
    unassigned: false,
    title: 'Proposta corporativa',
    notes: 'detalhes',
    validUntil: '2026-12-31',
    commercialTerms: 'termos',
    paymentNotes: null,
    items: [],
    subtotal: 0,
    discountType: null,
    discountValue: null,
    total: 0,
    createdAt: '2026-06-17T10:00:00Z',
    updatedAt: '2026-06-17T10:00:00Z',
    sourceOpportunity: { id: 'o1', name: 'Aurora', stage: 'READY_FOR_PROPOSAL' },
  };

  const item: ProposalItem = {
    id: 'i1',
    type: 'TRAVEL_PACKAGE',
    description: 'Pacote Caribe',
    quantity: 2,
    unitValue: 1000,
    discountType: null,
    discountValue: null,
    lineTotal: 2000,
  };

  const withItem: ProposalDetail = { ...sample, items: [item], subtotal: 2000, total: 2000 };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProposalDetailPage],
      providers: [
        providePrimeNG(),
        MessageService,
        ConfirmationService,
        { provide: ProposalService, useValue: proposals },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ProposalDetailPage).componentInstance;
  }

  /** Renders the component to the DOM after init and returns the host element. */
  function render() {
    configure();
    const fixture = TestBed.createComponent(ProposalDetailPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    proposals.detail.mockReset();
    proposals.updateDetails.mockReset();
    proposals.submitForReview.mockReset();
    proposals.addItem.mockReset();
    proposals.updateItem.mockReset();
    proposals.removeItem.mockReset();
    auth.canOperateProposal.mockReset();
    auth.canOperateProposal.mockReturnValue(true);
    proposals.detail.mockReturnValue(of(sample));
  });

  it('loads the proposal on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(proposals.detail).toHaveBeenCalledWith('p1');
    expect(comp['proposal']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('DRAFT')).toBe('Rascunho');
    expect(comp['statusLabel']('ACCEPTED')).toBe('Aceita');
    expect(comp['statusSeverity']('DRAFT')).toBe('secondary');
    expect(comp['stageLabel']('READY_FOR_PROPOSAL')).toBe('Pronta p/ proposta');
    expect(comp['itemTypeLabel']('TRAVEL_PACKAGE')).toBe('Pacote de viagem');
  });

  it('shows a permission message on 403', () => {
    proposals.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    proposals.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  it('gates item management by the operate scope and the Draft status', () => {
    const comp = build();
    comp.ngOnInit();

    auth.canOperateProposal.mockReturnValue(true);
    expect(comp['canManageItems']()).toBe(true);

    auth.canOperateProposal.mockReturnValue(false);
    expect(comp['canManageItems']()).toBe(false);

    auth.canOperateProposal.mockReturnValue(true);
    comp['proposal'].set({ ...sample, status: 'SENT' });
    expect(comp['canManageItems']()).toBe(false);
  });

  it('adds an item, refreshing the detail and closing the dialog', () => {
    proposals.addItem.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();

    comp['openAddItem']();
    expect(comp['itemOpen']()).toBe(true);
    comp['itemType'] = 'TRAVEL_PACKAGE';
    comp['itemDescription'] = 'Pacote Caribe';
    comp['itemQuantity'] = 2;
    comp['itemUnitValue'] = 1000;
    expect(comp['canSaveItem']()).toBe(true);

    comp['confirmItem']();

    expect(proposals.addItem).toHaveBeenCalledWith('p1', {
      type: 'TRAVEL_PACKAGE',
      description: 'Pacote Caribe',
      quantity: 2,
      unitValue: 1000,
      discountType: null,
      discountValue: null,
    });
    expect(comp['proposal']()).toEqual(withItem);
    expect(comp['proposal']()!.total).toBe(2000);
    expect(comp['itemOpen']()).toBe(false);
  });

  it('sends a percent discount when that mode is selected', () => {
    proposals.addItem.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();

    comp['openAddItem']();
    comp['itemDescription'] = 'Com desconto';
    comp['itemUnitValue'] = 1000;
    comp['itemDiscountMode'] = 'PERCENT';
    comp['itemDiscountValue'] = 10;
    comp['confirmItem']();

    expect(proposals.addItem).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ discountType: 'PERCENT', discountValue: 10 }),
    );
  });

  it('requires a discount value once a discount mode is chosen', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openAddItem']();
    comp['itemDescription'] = 'X';
    comp['itemUnitValue'] = 100;
    comp['itemDiscountMode'] = 'AMOUNT';
    comp['itemDiscountValue'] = null;
    expect(comp['canSaveItem']()).toBe(false);
    comp['itemDiscountValue'] = 20;
    expect(comp['canSaveItem']()).toBe(true);
  });

  it('edits an item through updateItem', () => {
    proposals.detail.mockReturnValue(of(withItem));
    proposals.updateItem.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();

    comp['openEditItem'](item);
    expect(comp['editingItemId']).toBe('i1');
    expect(comp['itemDescription']).toBe('Pacote Caribe');
    comp['itemQuantity'] = 3;
    comp['confirmItem']();

    expect(proposals.updateItem).toHaveBeenCalledWith(
      'p1',
      'i1',
      expect.objectContaining({ quantity: 3 }),
    );
    expect(comp['itemOpen']()).toBe(false);
  });

  it('removes an item through removeItem', () => {
    proposals.detail.mockReturnValue(of(withItem));
    proposals.removeItem.mockReturnValue(of(sample));
    const comp = build();
    comp.ngOnInit();

    comp['removeItem'](item);

    expect(proposals.removeItem).toHaveBeenCalledWith('p1', 'i1');
    expect(comp['proposal']()).toEqual(sample);
  });

  it('edits the commercial details, sending the validity and a proposal discount', () => {
    const discounted: ProposalDetail = {
      ...withItem,
      discountType: 'AMOUNT',
      discountValue: 200,
      total: 1800,
      paymentNotes: '50% na reserva',
    };
    proposals.detail.mockReturnValue(of(withItem));
    proposals.updateDetails.mockReturnValue(of(discounted));
    const comp = build();
    comp.ngOnInit();

    comp['openEditDetails']();
    expect(comp['detailsOpen']()).toBe(true);
    comp['detailsValidUntil'] = new Date('2026-12-31T00:00:00');
    comp['detailsPaymentNotes'] = '50% na reserva';
    comp['detailsDiscountMode'] = 'AMOUNT';
    comp['detailsDiscountValue'] = 200;
    comp['confirmEditDetails']();

    expect(proposals.updateDetails).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({
        validUntil: '2026-12-31',
        paymentNotes: '50% na reserva',
        discountType: 'AMOUNT',
        discountValue: 200,
      }),
    );
    expect(comp['proposal']()!.total).toBe(1800);
    expect(comp['detailsOpen']()).toBe(false);
  });

  it('only allows submitting for review with items, a positive total, the scope and Draft status', () => {
    auth.canOperateProposal.mockReturnValue(true);
    proposals.detail.mockReturnValue(of(sample)); // no items, total 0
    const comp = build();
    comp.ngOnInit();
    expect(comp['canSubmit']()).toBe(false); // no items

    comp['proposal'].set(withItem); // items + total 2000
    expect(comp['canSubmit']()).toBe(true);

    comp['proposal'].set({ ...withItem, status: 'READY_FOR_REVIEW' });
    expect(comp['canSubmit']()).toBe(false); // not a Draft anymore

    auth.canOperateProposal.mockReturnValue(false);
    comp['proposal'].set(withItem);
    expect(comp['canSubmit']()).toBe(false); // missing scope
  });

  it('submits for review and reflects the returned status', () => {
    const reviewing: ProposalDetail = { ...withItem, status: 'READY_FOR_REVIEW' };
    proposals.detail.mockReturnValue(of(withItem));
    proposals.submitForReview.mockReturnValue(of(reviewing));
    const comp = build();
    comp.ngOnInit();

    comp['submitForReview']();

    expect(proposals.submitForReview).toHaveBeenCalledWith('p1');
    expect(comp['proposal']()!.status).toBe('READY_FOR_REVIEW');
  });

  it('reports unsaved changes only after a field is actually changed in an open dialog', () => {
    proposals.detail.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();
    expect(comp.hasUnsavedChanges()).toBe(false);

    comp['openAddItem'](); // dialog open, nothing typed yet
    expect(comp.hasUnsavedChanges()).toBe(false);

    comp['itemDescription'] = 'Algo novo'; // user types
    expect(comp.hasUnsavedChanges()).toBe(true);
  });

  it('asks to confirm before closing a dirty dialog, and closes immediately when clean', async () => {
    proposals.detail.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();

    // Clean dialog -> closes without asking.
    comp['openAddItem']();
    await comp['requestCloseItem']();
    expect(comp['itemOpen']()).toBe(false);

    // Dirty dialog -> asks; if the user keeps editing (false), it stays open.
    comp['openAddItem']();
    comp['itemDescription'] = 'mudou';
    const confirm = vi
      .spyOn(TestBed.inject(ConfirmationService), 'confirm')
      .mockImplementation((opts) => {
        opts.reject?.();
        return TestBed.inject(ConfirmationService);
      });
    await comp['requestCloseItem']();
    expect(confirm).toHaveBeenCalled();
    expect(comp['itemOpen']()).toBe(true);
  });

  it('closes the open dialog with the Escape shortcut (no edits → no confirm)', () => {
    proposals.detail.mockReturnValue(of(withItem));
    const comp = build();
    comp.ngOnInit();
    comp['openAddItem']();
    expect(comp['itemOpen']()).toBe(true);

    comp['onShortcut'](new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(comp['itemOpen']()).toBe(false);
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      proposals.detail.mockReturnValue(NEVER); // never emits -> loading stays true
      const el = render();
      expect(el.textContent).toContain('Carregando');
    });

    it('renders the error state with a back button on 403', () => {
      proposals.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.querySelector('p-message')).not.toBeNull();
      expect(el.textContent).toContain('Voltar');
    });

    it('renders the loaded proposal: title, status, items table, totals and the operate actions', () => {
      proposals.detail.mockReturnValue(of(withItem));
      auth.canOperateProposal.mockReturnValue(true);
      const el = render();

      expect(el.querySelector('h1')?.textContent).toContain('Proposta corporativa');
      expect(el.textContent).toContain('Rascunho'); // status tag
      expect(el.textContent).toContain('Pacote Caribe'); // item row
      expect(el.textContent).toContain('Subtotal');
      expect(el.textContent).toContain('Total');
      // DRAFT + operate scope -> the management actions are present.
      expect(el.textContent).toContain('Editar dados comerciais');
      expect(el.textContent).toContain('Enviar para revisão');
      expect(el.textContent).toContain('Adicionar item');
    });

    it('renders the empty-items state and hides the actions for a consultation-only user', () => {
      proposals.detail.mockReturnValue(of(sample)); // no items
      auth.canOperateProposal.mockReturnValue(false);
      const el = render();

      expect(el.textContent).toContain('Nenhum item ainda.');
      expect(el.textContent).not.toContain('Adicionar item');
      expect(el.textContent).not.toContain('Enviar para revisão');
    });
  });
});
