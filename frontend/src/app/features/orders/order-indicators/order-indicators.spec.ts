import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { OrderIndicatorsPage } from './order-indicators';
import { OrderIndicators, OrderService } from '../../../core/api/order.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('OrderIndicators', () => {
  const orders = { indicators: vi.fn() };

  const stub: OrderIndicators = {
    total: 6,
    totalAmount: 17000,
    byResponsible: [
      { responsibleName: 'comercial', count: 4 },
      { responsibleName: null, count: 1 },
    ],
    pendingBooking: 4,
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OrderIndicatorsPage],
      providers: [providePrimeNG(), { provide: OrderService, useValue: orders }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(OrderIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(OrderIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    orders.indicators.mockReset();
    orders.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['createdFrom']?.getDate()).toBe(1);
    expect(orders.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period (volume) and operational (snapshot) KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['periodKpis']().map((k) => k.value)).toEqual([6, 17000]); // total, total amount
    expect(comp['snapshotKpis']().map((k) => k.value)).toEqual([4]); // pending booking
  });

  it('maps the null responsible bucket to "Sem responsável" and scales the bars', () => {
    const comp = build();
    comp.ngOnInit();
    const bars = comp['byResponsible']();
    expect(bars.map((b) => b.label)).toEqual(['comercial', 'Sem responsável']);
    expect(bars[0].ratio).toBe(1); // busiest (count 4)
  });

  it('re-fetches when the period changes', () => {
    const comp = build();
    comp.ngOnInit();
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(orders.indicators).toHaveBeenCalledTimes(2);
    expect(orders.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
  });

  it('clears the period to all-time (null dates)', () => {
    const comp = build();
    comp.ngOnInit();
    comp['clearPeriod']();
    expect(comp['createdFrom']).toBeNull();
    expect(comp['createdTo']).toBeNull();
    expect(orders.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    orders.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('DOM rendering', () => {
    it('renders the volume and operational KPI cards and the responsible bars', () => {
      orders.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('comercial');
      expect(el.textContent).toContain('Pendentes de reserva');
      expect(el.textContent).toMatch(/17[.,]000/); // total amount (BRL)
    });

    it('renders the error message when the load fails', () => {
      orders.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
