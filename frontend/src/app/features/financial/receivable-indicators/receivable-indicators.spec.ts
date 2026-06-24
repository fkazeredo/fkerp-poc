import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { ReceivableIndicatorsPage } from './receivable-indicators';
import { ReceivableIndicators, ReceivableService } from '../../../core/api/receivable.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableIndicators', () => {
  const receivables = { indicators: vi.fn() };

  const stub: ReceivableIndicators = {
    openCount: 4,
    partiallyPaidCount: 2,
    overdueCount: 1,
    outstandingAmount: 1300,
    paidReceivablesInPeriod: 3,
    paymentsRegistered: 5,
    receivedAmount: 2500,
    paymentsByMethod: [
      { method: 'PIX', methodLabel: 'Pix', count: 3, amount: 1500 },
      { method: 'CASH', methodLabel: 'Dinheiro', count: 2, amount: 1000 },
    ],
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReceivableIndicatorsPage],
      providers: [providePrimeNG(), { provide: ReceivableService, useValue: receivables }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReceivableIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReceivableIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    receivables.indicators.mockReset();
    receivables.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['paidFrom']?.getDate()).toBe(1);
    expect(receivables.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the snapshot (counts + outstanding) and period KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    const snapshot = comp['snapshotKpis']();
    expect(snapshot.map((k) => k.value)).toEqual([4, 2, 1, undefined]); // open/partial/overdue counts, then money
    expect(snapshot[3].money).toBe(1300); // outstanding R$
    const period = comp['periodKpis']();
    expect(period[0].money).toBe(2500); // received
    expect(period[1].value).toBe(5); // payments registered
    expect(period[2].value).toBe(3); // paid in period
  });

  it('maps the received-by-method breakdown to bars scaled by amount', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byMethod']();
    expect(bars.map((b) => b.label)).toEqual(['Pix', 'Dinheiro']);
    expect(bars[0].ratio).toBe(1); // 1500 is the largest
    expect(bars[1].ratio).toBeCloseTo(1000 / 1500);
    expect(bars[0].amount).toBe(1500);
    expect(bars[0].count).toBe(3);
  });

  it('re-fetches when the period changes and clears to all-time', () => {
    const comp = build();
    comp.ngOnInit();
    comp['paidFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(receivables.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
    comp['clearPeriod']();
    expect(receivables.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    receivables.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the snapshot + period KPI cards and the received-by-method bars', () => {
      receivables.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('Em aberto');
      expect(el.textContent).toContain('Vencidas');
      expect(el.textContent).toContain('Recebido no período');
      expect(el.textContent).toContain('Contas quitadas no período');
      expect(el.textContent).toContain('Recebido por forma');
      expect(el.textContent).toContain('Pix');
      expect(el.textContent).toContain('Dinheiro');
      // Operational view — never Commission / Payables / reconciliation labels.
      expect(el.textContent).not.toContain('Comissão');
      expect(el.textContent).not.toContain('Conta a pagar');
    });

    it('renders an empty state for the by-method breakdown when nothing was received', () => {
      receivables.indicators.mockReturnValue(
        of({ ...stub, receivedAmount: 0, paymentsRegistered: 0, paymentsByMethod: [] } satisfies ReceivableIndicators),
      );
      const el = render();
      expect(el.textContent).toContain('Nenhum pagamento recebido no período.');
    });

    it('renders the error message when the load fails', () => {
      receivables.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
