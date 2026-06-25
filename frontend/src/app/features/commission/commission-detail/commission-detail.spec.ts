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

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionDetailPage', () => {
  const commissions = { detail: vi.fn(), approve: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const auth = { canApproveCommission: vi.fn(), userId: vi.fn() };
  const messages = { add: vi.fn() };

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
    router.navigateByUrl.mockReset();
    messages.add.mockReset();
    // By default the viewer holds the approve scope and is NOT the beneficiary.
    auth.canApproveCommission.mockReset().mockReturnValue(true);
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

    it('shows a self-approval / permission message on 403 and keeps the dialog open', () => {
      commissions.approve.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const comp = build();
      comp.ngOnInit();
      comp['openApprove']();
      comp['submitApprove']();
      expect(comp['approveError']()).toContain('própria comissão');
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

    it('renders the error state with a back button on 403', () => {
      commissions.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.textContent).toContain('Voltar');
    });
  });
});
