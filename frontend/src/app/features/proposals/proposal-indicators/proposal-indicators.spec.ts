import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { ProposalIndicatorsPage } from './proposal-indicators';
import { ProposalIndicators, ProposalService } from '../../../core/api/proposal.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ProposalIndicators', () => {
  const proposals = { indicators: vi.fn() };

  const stub: ProposalIndicators = {
    total: 8,
    byStatus: [
      { status: 'DRAFT', count: 3 },
      { status: 'SENT', count: 3 },
      { status: 'ACCEPTED', count: 2 },
    ],
    byResponsible: [
      { responsibleName: 'comercial', count: 6 },
      { responsibleName: null, count: 1 },
    ],
    proposedAmount: 21500,
    acceptedAmount: 6500,
    rejectedCount: 1,
    waitingForReview: 1,
    waitingForCustomerDecision: 3,
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProposalIndicatorsPage],
      providers: [providePrimeNG(), { provide: ProposalService, useValue: proposals }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ProposalIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ProposalIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    proposals.indicators.mockReset();
    proposals.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['createdFrom']?.getDate()).toBe(1);
    expect(proposals.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period (volume) and operational (snapshot) KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    // total, proposed amount, accepted amount, rejected count.
    expect(comp['periodKpis']().map((k) => k.value)).toEqual([8, 21500, 6500, 1]);
    // waiting for review, waiting for customer decision.
    expect(comp['snapshotKpis']().map((k) => k.value)).toEqual([1, 3]);
  });

  it('maps status labels to pt-BR and scales the status bars', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byStatus']();
    expect(bars.map((b) => b.label)).toEqual(['Rascunho', 'Enviada', 'Aceita']);
    expect(bars[0].ratio).toBe(1); // busiest (count 3)
  });

  it('maps the null responsible bucket to "Sem responsável"', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['byResponsible']().map((b) => b.label)).toEqual(['comercial', 'Sem responsável']);
  });

  it('re-fetches when the period changes', () => {
    const comp = build();
    comp.ngOnInit();
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(proposals.indicators).toHaveBeenCalledTimes(2);
    expect(proposals.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
  });

  it('clears the period to all-time (null dates)', () => {
    const comp = build();
    comp.ngOnInit();
    comp['clearPeriod']();
    expect(comp['createdFrom']).toBeNull();
    expect(comp['createdTo']).toBeNull();
    expect(proposals.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    proposals.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the volume and operational KPI cards and the responsible bars', () => {
      proposals.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('comercial');
      expect(el.textContent).toContain('Aguardando cliente');
      expect(el.textContent).toMatch(/21[.,]500/); // proposed amount (BRL)
    });

    it('renders the error message when the load fails', () => {
      proposals.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
