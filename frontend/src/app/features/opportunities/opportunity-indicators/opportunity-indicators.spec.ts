import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { OpportunityIndicatorsPage } from './opportunity-indicators';
import { OpportunityIndicators, OpportunityService } from '../../../core/api/opportunity.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('OpportunityIndicators', () => {
  const opportunities = { indicators: vi.fn() };

  const stub: OpportunityIndicators = {
    total: 8,
    lost: 1,
    byStage: [
      { stage: 'NEW_OPPORTUNITY', count: 2 },
      { stage: 'READY_FOR_PROPOSAL', count: 2 },
      { stage: 'LOST', count: 1 },
    ],
    byOrigin: [
      { origin: 'Website', count: 6 },
      { origin: 'Instagram', count: 2 },
    ],
    byResponsible: [
      { responsibleName: 'comercial', count: 5 },
      { responsibleName: null, count: 1 },
    ],
    active: 7,
    readyForProposal: 2,
    overdueClose: 2,
    activePipelineValue: 18000,
    valueByResponsible: [
      { responsibleName: 'comercial', value: 14500 },
      { responsibleName: null, value: 500 },
    ],
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OpportunityIndicatorsPage],
      providers: [providePrimeNG(), { provide: OpportunityService, useValue: opportunities }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OpportunityIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(OpportunityIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    opportunities.indicators.mockReset();
    opportunities.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['createdFrom']?.getDate()).toBe(1);
    expect(opportunities.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period (volume) and pipeline (snapshot) KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['periodKpis']().map((k) => k.value)).toEqual([8, 1]);
    expect(comp['pipelineKpis']().map((k) => k.value)).toEqual([7, 2, 2]); // active, ready, overdue
  });

  it('maps stage labels to pt-BR and scales the stage bars', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byStage']();
    expect(bars.map((b) => b.label)).toEqual(['Nova', 'Pronta p/ proposta', 'Perdida']);
    expect(bars[0].ratio).toBe(1); // busiest (count 2)
  });

  it('maps the null responsible bucket to "Sem responsável" for both count and value', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['byResponsible']().map((b) => b.label)).toEqual(['comercial', 'Sem responsável']);
    const values = comp['valueByResponsible']();
    expect(values.map((b) => b.label)).toEqual(['comercial', 'Sem responsável']);
    expect(values[0].value).toBe(14500);
    expect(values[0].ratio).toBe(1); // highest value fills the bar
  });

  it('re-fetches when the period changes', () => {
    const comp = build();
    comp.ngOnInit();
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(opportunities.indicators).toHaveBeenCalledTimes(2);
    expect(opportunities.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
  });

  it('clears the period to all-time (null dates)', () => {
    const comp = build();
    comp.ngOnInit();
    comp['clearPeriod']();
    expect(comp['createdFrom']).toBeNull();
    expect(comp['createdTo']).toBeNull();
    expect(opportunities.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    opportunities.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the volume and pipeline KPI cards and the responsible bars', () => {
      opportunities.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('comercial');
      expect(el.textContent).toMatch(/18[.,]000/); // active pipeline value (BRL)
    });

    it('renders the error message when the load fails', () => {
      opportunities.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
