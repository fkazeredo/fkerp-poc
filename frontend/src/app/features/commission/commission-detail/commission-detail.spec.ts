import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { CommissionDetailPage } from './commission-detail';
import { CommissionDetail, CommissionService } from '../../../core/api/commission.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ReferenceService } from '../../../core/api/reference.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionDetailPage', () => {
  const commissions = {
    detail: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    cancel: vi.fn(),
    pay: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn() };
  const auth = {
    canApproveCommission: vi.fn(),
    canRejectCommission: vi.fn(),
    canCancelCommission: vi.fn(),
    canPayCommission: vi.fn(),
    userId: vi.fn(),
  };
  const messages = { add: vi.fn() };
  const references = { list: vi.fn() };

  const sample: CommissionDetail = {
    id: 'c1',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    proposalId: 'p1',
    proposalReference: 'Proposta Aurora',
    opportunityId: 'o1',
    opportunityReference: 'Aurora',
    leadId: 'l1',
    beneficiaryUserId: 'u1',
    beneficiaryName: 'vendedor',
    ruleId: 'r1',
    ruleName: 'Comissão padrão',
    rulePercentage: 5,
    basisType: 'COMMERCIAL_AMOUNT',
    baseAmount: 3000,
    amount: 150,
    status: 'ELIGIBLE',
    receivableId: 'rec1',
    receivableStatus: 'PAID',
    eligibleAt: '2026-06-25T12:00:00Z',
    approvedAt: null,
    approvedByName: null,
    approvalNotes: null,
    paidAt: null,
    paidAmount: null,
    paymentDate: null,
    paymentMethod: null,
    paymentNote: null,
    paidByName: null,
    resolutionReason: null,
    resolutionNote: null,
    resolvedByName: null,
    resolvedAt: null,
    createdByName: 'comercial',
    createdAt: '2026-06-20T10:00:00Z',
  };

  const approved: CommissionDetail = {
    ...sample,
    status: 'APPROVED',
    approvedAt: '2026-06-26T09:00:00Z',
    approvedByName: 'financeiro',
    approvalNotes: 'Conferido',
  };

  const paid: CommissionDetail = {
    ...approved,
    status: 'PAID',
    paidAt: '2026-06-28T09:00:00Z',
    paidAmount: 150,
    paymentDate: '2026-06-28',
    paymentMethod: 'Pix',
    paymentNote: 'OP 9',
    paidByName: 'financeiro',
  };

  const rejected: CommissionDetail = {
    ...sample,
    status: 'REJECTED',
    resolutionReason: 'Comissão duplicada',
    resolutionNote: 'Lançada duas vezes',
    resolvedByName: 'financeiro',
    resolvedAt: '2026-06-27T09:00:00Z',
  };

  const reasons = [
    { id: 'rsn1', code: 'DUPLICATE_COMMISSION', label: 'Comissão duplicada', active: true, sortOrder: 1 },
  ];

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommissionDetailPage],
      providers: [
        providePrimeNG(),
        provideRouter([]),
        { provide: CommissionService, useValue: commissions },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: ReferenceService, useValue: references },
        { provide: MessageService, useValue: messages },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'c1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(CommissionDetailPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(CommissionDetailPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    commissions.detail.mockReset().mockReturnValue(of(sample));
    commissions.approve.mockReset().mockReturnValue(of(approved));
    commissions.reject.mockReset().mockReturnValue(of(rejected));
    commissions.cancel.mockReset().mockReturnValue(of({ ...sample, status: 'CANCELLED' }));
    commissions.pay.mockReset().mockReturnValue(of(paid));
    router.navigateByUrl.mockReset();
    messages.add.mockReset();
    references.list.mockReset().mockReturnValue(of(reasons));
    // By default the viewer holds the approve/reject/cancel/pay scopes and is NOT the beneficiary.
    auth.canApproveCommission.mockReset().mockReturnValue(true);
    auth.canRejectCommission.mockReset().mockReturnValue(true);
    auth.canCancelCommission.mockReset().mockReturnValue(true);
    auth.canPayCommission.mockReset().mockReturnValue(true);
    auth.userId.mockReset().mockReturnValue('approver');
  });

  it('loads the commission on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(commissions.detail).toHaveBeenCalledWith('c1');
    expect(comp['commission']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status, basis and receivable labels', () => {
    const comp = build();
    expect(comp['statusLabel']('ELIGIBLE')).toBe('Pendente de aprovação');
    expect(comp['statusSeverity']('ELIGIBLE')).toBe('warn');
    expect(comp['basisLabel']('COMMERCIAL_AMOUNT')).toBe('Valor comercial (previsão)');
    expect(comp['receivableLabel']('PAID')).toBe('Paga');
    expect(comp['receivableLabel'](null)).toBe('—');
    expect(comp['orderCode'](7)).toBe('PC-0007');
  });

  it('builds the lifecycle timeline from the stamps present (generated + eligible)', () => {
    const comp = build();
    comp.ngOnInit();
    const timeline = comp['timeline']();
    expect(timeline.map((e) => e.label)).toEqual(['Gerada', 'Elegível (conta a receber paga)']);
    expect(timeline[0].by).toBe('comercial');
  });

  it('includes the approval timeline entry (with the approver) once approved', () => {
    commissions.detail.mockReturnValue(of(approved));
    const comp = build();
    comp.ngOnInit();
    const timeline = comp['timeline']();
    const approval = timeline.find((e) => e.label === 'Aprovada');
    expect(approval).toBeTruthy();
    expect(approval!.by).toBe('financeiro');
  });

  it('omits approval/payment timeline entries until their stamps exist', () => {
    commissions.detail.mockReturnValue(of({ ...sample, eligibleAt: null } satisfies CommissionDetail));
    const comp = build();
    comp.ngOnInit();
    expect(comp['timeline']().map((e) => e.label)).toEqual(['Gerada']);
  });

  it('navigates back to the commission list', () => {
    const comp = build();
    comp.ngOnInit();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/comissoes');
  });

  it('shows a permission message on 403', () => {
    commissions.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    commissions.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  describe('approval', () => {
    it('canApprove is true for an eligible commission the approver (not the beneficiary) holds the scope for', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp['canApprove']()).toBe(true);
    });

    it('canApprove is false without the approve scope', () => {
      auth.canApproveCommission.mockReturnValue(false);
      const comp = build();
      comp.ngOnInit();
      expect(comp['canApprove']()).toBe(false);
    });

    it('canApprove is false when the viewer is the beneficiary (self-approval blocked)', () => {
      auth.userId.mockReturnValue('u1'); // == beneficiaryUserId
      const comp = build();
      comp.ngOnInit();
      expect(comp['canApprove']()).toBe(false);
    });

    it('canApprove is false when the commission is not eligible (e.g. already approved)', () => {
      commissions.detail.mockReturnValue(of(approved));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canApprove']()).toBe(false);
    });

    it('approves the commission with the notes and refreshes the detail', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openApprove']();
      comp['approveForm'].setValue({ notes: '  Conferido  ' });
      comp['submitApprove']();
      expect(commissions.approve).toHaveBeenCalledWith('c1', 'Conferido'); // trimmed
      expect(comp['commission']()!.status).toBe('APPROVED');
      expect(comp['approveDialogOpen']()).toBe(false);
      expect(messages.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success' }),
      );
    });

    it('approves with null notes when the field is left blank', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openApprove']();
      comp['submitApprove']();
      expect(commissions.approve).toHaveBeenCalledWith('c1', null);
    });

    it('shows a permission message on 403 and keeps the dialog open', () => {
      commissions.approve.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const comp = build();
      comp.ngOnInit();
      comp['openApprove']();
      comp['submitApprove']();
      expect(comp['approveError']()).toContain('permissão');
      expect(comp['approveDialogOpen']()).toBe(true);
    });

    it('shows a not-eligible message on 422', () => {
      commissions.approve.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'Apenas elegíveis' } })),
      );
      const comp = build();
      comp.ngOnInit();
      comp['openApprove']();
      comp['submitApprove']();
      expect(comp['approveError']()).toBe('Apenas elegíveis');
    });

    it('flags unsaved changes while the dialog is open with a dirty form', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp.hasUnsavedChanges()).toBe(false);
      comp['openApprove']();
      comp['approveForm'].markAsDirty();
      expect(comp.hasUnsavedChanges()).toBe(true);
    });
  });

  describe('reject and cancel', () => {
    it('canReject is true only for an eligible commission with the scope', () => {
      const comp = build();
      comp.ngOnInit();
      expect(comp['canReject']()).toBe(true);

      auth.canRejectCommission.mockReturnValue(false);
      const noScope = build();
      noScope.ngOnInit();
      expect(noScope['canReject']()).toBe(false);
    });

    it('canReject is false when the commission is not eligible', () => {
      commissions.detail.mockReturnValue(of(approved));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canReject']()).toBe(false);
    });

    it('canCancel is true for an unpaid Expected/Approved commission with the scope, false for Eligible', () => {
      commissions.detail.mockReturnValue(of(approved)); // APPROVED
      const comp = build();
      comp.ngOnInit();
      expect(comp['canCancel']()).toBe(true);

      commissions.detail.mockReturnValue(of(sample)); // ELIGIBLE → reject, not cancel
      const eligible = build();
      eligible.ngOnInit();
      expect(eligible['canCancel']()).toBe(false);
    });

    it('loads the shared resolution reasons on init when the user can void', () => {
      const comp = build();
      comp.ngOnInit();
      expect(references.list).toHaveBeenCalledWith('resolution-reasons', false, 'commission');
      expect(comp['reasonOptions']()).toEqual(reasons);
    });

    it('rejects the commission with the reason and note and refreshes the detail', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openReject']();
      comp['rejectForm'].setValue({ reasonId: 'rsn1', note: '  Duplicada  ' });
      comp['submitReject']();
      expect(commissions.reject).toHaveBeenCalledWith('c1', 'rsn1', 'Duplicada'); // trimmed
      expect(comp['commission']()!.status).toBe('REJECTED');
      expect(comp['rejectDialogOpen']()).toBe(false);
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('requires a reason to submit a rejection (the form is invalid without it)', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openReject']();
      comp['submitReject'](); // no reason chosen
      expect(commissions.reject).not.toHaveBeenCalled();
    });

    it('cancels an unpaid commission with the reason and refreshes the detail', () => {
      commissions.detail.mockReturnValue(of(approved)); // APPROVED (unpaid)
      const comp = build();
      comp.ngOnInit();
      comp['openCancel']();
      comp['cancelForm'].setValue({ reasonId: 'rsn1', note: '' });
      comp['submitCancel']();
      expect(commissions.cancel).toHaveBeenCalledWith('c1', 'rsn1', null);
      expect(comp['commission']()!.status).toBe('CANCELLED');
      expect(comp['cancelDialogOpen']()).toBe(false);
    });

    it('shows a permission message on 403 and a state message on 422 when rejecting', () => {
      commissions.reject.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const denied = build();
      denied.ngOnInit();
      denied['openReject']();
      denied['rejectForm'].setValue({ reasonId: 'rsn1', note: '' });
      denied['submitReject']();
      expect(denied['rejectError']()).toContain('permissão');
      expect(denied['rejectDialogOpen']()).toBe(true);

      commissions.reject.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'Apenas elegíveis' } })),
      );
      const bad = build();
      bad.ngOnInit();
      bad['openReject']();
      bad['rejectForm'].setValue({ reasonId: 'rsn1', note: '' });
      bad['submitReject']();
      expect(bad['rejectError']()).toBe('Apenas elegíveis');
    });

    it('flags unsaved changes while the reject dialog is open with a dirty form', () => {
      const comp = build();
      comp.ngOnInit();
      comp['openReject']();
      comp['rejectForm'].markAsDirty();
      expect(comp.hasUnsavedChanges()).toBe(true);
    });
  });

  describe('payment', () => {
    it('canPay is true only for an approved commission with the scope', () => {
      commissions.detail.mockReturnValue(of(approved));
      const comp = build();
      comp.ngOnInit();
      expect(comp['canPay']()).toBe(true);

      auth.canPayCommission.mockReturnValue(false);
      const noScope = build();
      noScope.ngOnInit();
      expect(noScope['canPay']()).toBe(false);
    });

    it('canPay is false for a non-approved commission (e.g. eligible)', () => {
      const comp = build(); // sample is ELIGIBLE
      comp.ngOnInit();
      expect(comp['canPay']()).toBe(false);
    });

    it('loads the payment methods on init when the user can pay', () => {
      const comp = build();
      comp.ngOnInit();
      expect(references.list).toHaveBeenCalledWith('payment-methods', false, 'financial');
    });

    it('defaults the amount to the commission amount and pays, refreshing the detail', () => {
      commissions.detail.mockReturnValue(of(approved)); // amount 150
      const comp = build();
      comp.ngOnInit();
      comp['openPay']();
      expect(comp['payForm'].getRawValue().amount).toBe(150); // pre-filled = commission amount
      comp['payForm'].patchValue({ paymentMethodId: 'm1', paymentDate: new Date('2026-06-28T00:00:00') });
      comp['submitPay']();
      expect(commissions.pay).toHaveBeenCalledWith('c1', {
        paymentMethodId: 'm1',
        amount: 150,
        paymentDate: '2026-06-28',
        note: null,
      });
      expect(comp['commission']()!.status).toBe('PAID');
      expect(comp['payDialogOpen']()).toBe(false);
      expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('shows the backend message on a 422 (e.g. amount mismatch) and keeps the dialog open', () => {
      commissions.detail.mockReturnValue(of(approved));
      commissions.pay.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 422, error: { message: 'O valor deve ser igual' } }),
        ),
      );
      const comp = build();
      comp.ngOnInit();
      comp['openPay']();
      comp['payForm'].patchValue({ paymentMethodId: 'm1', paymentDate: new Date('2026-06-28T00:00:00') });
      comp['submitPay']();
      expect(comp['payError']()).toBe('O valor deve ser igual');
      expect(comp['payDialogOpen']()).toBe(true);
    });

    it('flags unsaved changes while the pay dialog is open with a dirty form', () => {
      commissions.detail.mockReturnValue(of(approved));
      const comp = build();
      comp.ngOnInit();
      comp['openPay']();
      comp['payForm'].markAsDirty();
      expect(comp.hasUnsavedChanges()).toBe(true);
    });
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      commissions.detail.mockReturnValue(NEVER);
      expect(render().textContent).toContain('Carregando');
    });

    it('renders the summary, origin, calculation, receivable and timeline sections', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Comissão');
      // Summary + status.
      expect(el.textContent).toContain('vendedor'); // beneficiary
      expect(el.textContent).toContain('Pendente de aprovação'); // ELIGIBLE label
      // Origin (traceable).
      expect(el.textContent).toContain('PC-0007');
      expect(el.textContent).toContain('Proposta Aurora');
      expect(el.textContent).toContain('Aurora');
      // Calculation.
      expect(el.textContent).toContain('Valor comercial (previsão)');
      expect(el.textContent).toContain('Comissão padrão');
      // Related receivable.
      expect(el.textContent).toContain('Conta a receber');
      expect(el.textContent).toContain('Paga');
      // History timeline.
      expect(el.textContent).toContain('Histórico');
      expect(el.textContent).toContain('Gerada');
      expect(el.textContent).toContain('Elegível');
    });

    it('shows the "Aprovar comissão" action for an eligible commission the user may approve', () => {
      const el = render();
      expect(el.textContent).toContain('Aprovar comissão');
    });

    it('hides the approve action when the user lacks the scope', () => {
      auth.canApproveCommission.mockReturnValue(false);
      const el = render();
      expect(el.textContent).not.toContain('Aprovar comissão');
    });

    it('hides the approve action for the beneficiary (self-approval)', () => {
      auth.userId.mockReturnValue('u1');
      const el = render();
      expect(el.textContent).not.toContain('Aprovar comissão');
    });

    it('renders the approver and approval notes once approved', () => {
      commissions.detail.mockReturnValue(of(approved));
      const el = render();
      expect(el.textContent).toContain('Aprovada por');
      expect(el.textContent).toContain('financeiro');
      expect(el.textContent).toContain('Conferido');
      // No approve action on an already-approved commission.
      expect(el.textContent).not.toContain('Aprovar comissão');
    });

    it('shows the Rejeitar action for an eligible commission the user may reject', () => {
      const el = render();
      expect(el.textContent).toContain('Rejeitar');
    });

    it('shows the Cancelar action for an unpaid approved commission and hides reject', () => {
      commissions.detail.mockReturnValue(of(approved)); // APPROVED
      const el = render();
      expect(el.textContent).toContain('Cancelar comissão');
      // Reject is only for an eligible commission.
      expect(el.textContent).not.toContain('Rejeitar');
    });

    it('hides reject/cancel actions without the scopes', () => {
      auth.canRejectCommission.mockReturnValue(false);
      auth.canCancelCommission.mockReturnValue(false);
      const el = render();
      expect(el.textContent).not.toContain('Rejeitar');
      expect(el.textContent).not.toContain('Cancelar comissão');
    });

    it('renders the resolution (who/reason/note) once rejected', () => {
      commissions.detail.mockReturnValue(of(rejected));
      const el = render();
      expect(el.textContent).toContain('Rejeitada por');
      expect(el.textContent).toContain('financeiro');
      expect(el.textContent).toContain('Comissão duplicada');
      expect(el.textContent).toContain('Lançada duas vezes');
      // The timeline shows the Rejeitada entry; no action buttons on a terminal commission.
      expect(el.textContent).not.toContain('Aprovar comissão');
    });

    it('shows the Registrar pagamento action for an approved commission the user may pay', () => {
      commissions.detail.mockReturnValue(of(approved));
      const el = render();
      expect(el.textContent).toContain('Registrar pagamento');
    });

    it('hides the pay action without the scope', () => {
      commissions.detail.mockReturnValue(of(approved));
      auth.canPayCommission.mockReturnValue(false);
      const el = render();
      expect(el.textContent).not.toContain('Registrar pagamento');
    });

    it('renders the payment (who/amount/method) once paid', () => {
      commissions.detail.mockReturnValue(of(paid));
      const el = render();
      expect(el.textContent).toContain('Paga por');
      expect(el.textContent).toContain('financeiro');
      expect(el.textContent).toContain('Pix');
      expect(el.textContent).toContain('OP 9');
      // No action buttons on a settled commission.
      expect(el.textContent).not.toContain('Registrar pagamento');
    });

    it('renders the error state with a back button on 403', () => {
      commissions.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.textContent).toContain('Voltar');
    });
  });
});
