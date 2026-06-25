import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { CommissionDetailPage } from './commission-detail';
import { CommissionDetail, CommissionService } from '../../../core/api/commission.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionDetailPage', () => {
  const commissions = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

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
    paidAt: null,
    createdByName: 'comercial',
    createdAt: '2026-06-20T10:00:00Z',
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
    router.navigateByUrl.mockReset();
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

    it('renders the error state with a back button on 403', () => {
      commissions.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.textContent).toContain('Voltar');
    });
  });
});
