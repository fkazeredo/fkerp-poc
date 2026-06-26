import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { CommissionIndicatorsPage } from './commission-indicators';
import { CommissionIndicators, CommissionService } from '../../../core/api/commission.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionIndicators', () => {
  const commissions = { indicators: vi.fn() };

  const stub: CommissionIndicators = {
    byStatus: [
      { status: 'EXPECTED', count: 2, totalAmount: 50 },
      { status: 'ELIGIBLE', count: 1, totalAmount: 25 },
      { status: 'PAID', count: 1, totalAmount: 25 },
    ],
    byBeneficiary: [
      { beneficiaryUserId: 'u1', beneficiaryName: 'comercial', count: 3, totalAmount: 75 },
      { beneficiaryUserId: 'u2', beneficiaryName: 'representante', count: 1, totalAmount: 25 },
    ],
    pendingApprovalCount: 1,
    pendingApprovalAmount: 25,
    pendingPaymentCount: 0,
    pendingPaymentAmount: 0,
    paidInPeriodCount: 1,
    paidInPeriodAmount: 25,
    avgEligibilityToApprovalSeconds: 3600,
    avgApprovalToPaymentSeconds: 90000,
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommissionIndicatorsPage],
      providers: [providePrimeNG(), { provide: CommissionService, useValue: commissions }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(CommissionIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(CommissionIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    commissions.indicators.mockReset();
    commissions.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['from']?.getDate()).toBe(1);
    expect(commissions.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period (paid) and snapshot (pending) KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['periodKpis']().map((k) => k.value)).toEqual([1, 25]); // paid count, paid amount
    expect(comp['snapshotKpis']().map((k) => k.value)).toEqual([25, 0]); // pending approval, pending payment amounts
  });

  it('maps the by-status and by-beneficiary bars and scales them', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['byStatus']().map((b) => b.label)).toEqual(['Prevista', 'Pendente de aprovação', 'Paga']);
    expect(comp['byStatus']()[0].ratio).toBe(1); // EXPECTED is the busiest (count 2)
    expect(comp['byBeneficiary']().map((b) => b.label)).toEqual(['comercial', 'representante']);
  });

  it('formats the latency averages as human durations', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['avgEligibilityToApproval']()).toBe('1 h'); // 3600 s
    expect(comp['avgApprovalToPayment']()).toBe('1 d 1 h'); // 90000 s = 25 h
  });

  it('shows "—" for a null average (no commission crossed the step yet)', () => {
    commissions.indicators.mockReturnValue(
      of({ ...stub, avgEligibilityToApprovalSeconds: null, avgApprovalToPaymentSeconds: null }),
    );
    const comp = build();
    comp.ngOnInit();
    expect(comp['avgEligibilityToApproval']()).toBe('—');
    expect(comp['avgApprovalToPayment']()).toBe('—');
  });

  it('re-fetches when the period changes and clears to all-time', () => {
    const comp = build();
    comp.ngOnInit();
    comp['from'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(commissions.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
    comp['clearPeriod']();
    expect(comp['from']).toBeNull();
    expect(commissions.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    commissions.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the KPI cards, breakdowns and latency averages', () => {
      const el = render();
      expect(el.textContent).toContain('Pendente de aprovação');
      expect(el.textContent).toContain('Pagas no período');
      expect(el.textContent).toContain('Por situação');
      expect(el.textContent).toContain('Por beneficiário');
      expect(el.textContent).toContain('comercial');
      expect(el.textContent).toContain('Tempo médio elegível → aprovada');
      expect(el.textContent).toContain('1 h'); // formatted average
    });

    it('renders the error message when the load fails', () => {
      commissions.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
