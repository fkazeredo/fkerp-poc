import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { ReceivableIndicatorsPage, formatDays } from './receivable-indicators';
import { ReceivableIndicators, ReceivableService } from '../../../core/api/receivable.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableIndicators', () => {
  const receivables = { indicators: vi.fn() };

  const stub: ReceivableIndicators = {
    totalReceivablesInPeriod: 8,
    totalToReceive: 4000,
    receivedAmount: 2500,
    paymentsRegistered: 5,
    paymentsByMethod: [
      { method: 'PIX', methodLabel: 'Pix', count: 3, amount: 1500 },
      { method: 'CASH', methodLabel: 'Dinheiro', count: 2, amount: 1000 },
    ],
    paidReceivablesInPeriod: 3,
    avgDaysToPayment: 12,
    byStatus: [
      { status: 'OPEN', count: 4 },
      { status: 'PARTIALLY_PAID', count: 2 },
      { status: 'OVERDUE', count: 1 },
      { status: 'PAID', count: 3 },
    ],
    outstandingAmount: 1300,
    overdueAmount: 500,
    readyForCommission: 3,
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
    expect(comp['from']?.getDate()).toBe(1);
    expect(receivables.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period KPI cards (count / amounts / settled / avg days)', () => {
    const comp = build();
    comp.ngOnInit();
    const period = comp['periodKpis']();
    expect(period.find((k) => k.key === 'total')?.value).toBe(8);
    expect(period.find((k) => k.key === 'toReceive')?.money).toBe(4000);
    expect(period.find((k) => k.key === 'received')?.money).toBe(2500);
    expect(period.find((k) => k.key === 'payments')?.value).toBe(5);
    expect(period.find((k) => k.key === 'settled')?.value).toBe(3);
    expect(period.find((k) => k.key === 'avg')?.text).toBe('12 dias');
  });

  it('builds the snapshot KPI cards (outstanding / overdue / ready for commission)', () => {
    const comp = build();
    comp.ngOnInit();
    const snapshot = comp['snapshotKpis']();
    expect(snapshot.find((k) => k.key === 'outstanding')?.money).toBe(1300);
    expect(snapshot.find((k) => k.key === 'overdue')?.money).toBe(500);
    expect(snapshot.find((k) => k.key === 'commission')?.value).toBe(3);
  });

  it('maps the by-status breakdown to pt-BR bars scaled by count', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byStatus']();
    expect(bars.map((b) => b.label)).toEqual(['Em aberto', 'Parcialmente paga', 'Vencida', 'Paga']);
    expect(bars[0].ratio).toBe(1); // OPEN count 4 is the largest
  });

  it('maps the received-by-method breakdown to bars scaled by amount', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byMethod']();
    expect(bars.map((b) => b.label)).toEqual(['Pix', 'Dinheiro']);
    expect(bars[0].ratio).toBe(1);
    expect(bars[1].ratio).toBeCloseTo(1000 / 1500);
  });

  it('re-fetches when the period changes and clears to all-time', () => {
    const comp = build();
    comp.ngOnInit();
    comp['from'] = new Date(2026, 0, 1);
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

  describe('formatDays', () => {
    it('formats null as a dash, and a day count with pluralization', () => {
      expect(formatDays(null)).toBe('—');
      expect(formatDays(0)).toBe('0 dias');
      expect(formatDays(1)).toBe('1 dia');
      expect(formatDays(12)).toBe('12 dias');
    });
  });

  describe('DOM rendering', () => {
    it('renders the period + snapshot KPI cards and the by-status / by-method bars', () => {
      receivables.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('Contas no período');
      expect(el.textContent).toContain('A receber (período)');
      expect(el.textContent).toContain('Tempo médio até pagamento');
      expect(el.textContent).toContain('Em aberto (R$)');
      expect(el.textContent).toContain('Vencido (R$)');
      expect(el.textContent).toContain('Prontas p/ comissão');
      expect(el.textContent).toContain('Por status');
      expect(el.textContent).toContain('Parcialmente paga');
      expect(el.textContent).toContain('Recebido por forma');
      expect(el.textContent).toContain('Pix');
      // Operational view — never bank-reconciliation or accounts-payable labels.
      expect(el.textContent).not.toContain('Conciliação');
      expect(el.textContent).not.toContain('Conta a pagar');
    });

    it('renders an empty state for the by-method breakdown when nothing was received', () => {
      receivables.indicators.mockReturnValue(
        of({
          ...stub,
          receivedAmount: 0,
          paymentsRegistered: 0,
          paymentsByMethod: [],
        } satisfies ReceivableIndicators),
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
