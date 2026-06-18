import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { LeadIndicatorsPage } from './lead-indicators';
import { LeadIndicators, LeadService } from '../../../core/api/lead.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('LeadIndicators', () => {
  const leads = { indicators: vi.fn() };

  const stub: LeadIndicators = {
    total: 7,
    newLeads: 4,
    contacted: 1,
    qualified: 1,
    lost: 1,
    waitingFirstContact: 3,
    byOrigin: [
      { origin: 'Website', count: 5 },
      { origin: 'Instagram', count: 2 },
    ],
    byResponsible: [
      { responsibleName: 'comercial', count: 4 },
      { responsibleName: 'representante', count: 2 },
      { responsibleName: null, count: 1 },
    ],
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [LeadIndicatorsPage],
      providers: [providePrimeNG(), { provide: LeadService, useValue: leads }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(LeadIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(LeadIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    leads.indicators.mockReset();
    leads.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['createdFrom']?.getDate()).toBe(1);
    expect(leads.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the six KPI cards from the indicators (Lost included)', () => {
    const comp = build();
    comp.ngOnInit();
    const kpis = comp['kpis']();
    expect(kpis.map((k) => k.value)).toEqual([7, 4, 1, 1, 1, 3]);
    const lost = kpis.find((k) => k.key === 'lost');
    expect(lost?.value).toBe(1);
    expect(kpis).toHaveLength(6);
  });

  it('maps the null responsible bucket to "Sem responsável" and scales the bars', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byResponsible']();
    expect(bars.map((b) => b.label)).toEqual(['comercial', 'representante', 'Sem responsável']);
    // busiest bucket fills the bar (ratio 1), the others are proportional
    expect(bars[0].ratio).toBe(1);
    expect(bars[1].ratio).toBeCloseTo(0.5);
  });

  it('re-fetches when the period changes', () => {
    const comp = build();
    comp.ngOnInit();
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(leads.indicators).toHaveBeenCalledTimes(2);
    expect(leads.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
  });

  it('clears the period to all-time (null dates)', () => {
    const comp = build();
    comp.ngOnInit();
    comp['clearPeriod']();
    expect(comp['createdFrom']).toBeNull();
    expect(comp['createdTo']).toBeNull();
    expect(leads.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('flags the empty state when there are no leads in the period', () => {
    leads.indicators.mockReturnValue(of({ ...stub, total: 0, byOrigin: [], byResponsible: [] }));
    const comp = build();
    comp.ngOnInit();
    expect(comp['isEmpty']()).toBe(true);
  });

  it('shows an error message when the load fails', () => {
    leads.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the KPI cards and the responsible bars', () => {
      leads.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('7'); // total KPI
      expect(el.textContent).toContain('comercial'); // responsible bar
      expect(el.textContent).toContain('Website'); // origin
    });

    it('renders the empty state for a period with no leads', () => {
      leads.indicators.mockReturnValue(of({ ...stub, total: 0, byOrigin: [], byResponsible: [] }));
      expect(render().textContent).toMatch(/Nenhum lead|sem dados|nenhum/i);
    });

    it('renders the error message when the load fails', () => {
      leads.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
