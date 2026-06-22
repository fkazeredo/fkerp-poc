import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { BookingIndicatorsPage, formatDuration } from './booking-indicators';
import { BookingIndicators, BookingService } from '../../../core/api/booking.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('BookingIndicators', () => {
  const bookings = { indicators: vi.fn() };

  const stub: BookingIndicators = {
    total: 5,
    byStatus: [
      { status: 'PENDING', count: 2 },
      { status: 'CONFIRMED', count: 1 },
      { status: 'FAILED', count: 1 },
      { status: 'IN_PROGRESS', count: 1 },
    ],
    itemsByType: [
      { type: 'TRAVEL_PACKAGE', count: 4 },
      { type: 'CAR_RENTAL', count: 2 },
    ],
    failedItems: 1,
    readyForFinance: 3,
    avgConfirmationSeconds: 172800, // 2 days
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BookingIndicatorsPage],
      providers: [providePrimeNG(), { provide: BookingService, useValue: bookings }],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(BookingIndicatorsPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(BookingIndicatorsPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    bookings.indicators.mockReset();
    bookings.indicators.mockReturnValue(of(stub));
  });

  it('defaults the period to month-to-date and loads on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['createdFrom']?.getDate()).toBe(1);
    expect(bookings.indicators).toHaveBeenCalledTimes(1);
    expect(comp['data']()).toEqual(stub);
  });

  it('builds the period (volume) and operational (snapshot) KPI cards', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['periodKpis']().map((k) => k.value)).toEqual([5, 1]); // total, failed items
    const snapshot = comp['snapshotKpis']();
    expect(snapshot[0].value).toBe(3); // ready for finance
    expect(snapshot[1].text).toBe('2.0 dias'); // avg confirmation time
  });

  it('maps the by-status and by-item-type breakdowns to pt-BR bars and scales them', () => {
    const comp = build();
    comp.ngOnInit();
    const status = comp['byStatus']();
    expect(status.map((b) => b.label)).toEqual([
      'Pendente',
      'Confirmada',
      'Falhou',
      'Em andamento',
    ]);
    expect(status[0].ratio).toBe(1); // busiest (count 2)
    const types = comp['byItemType']();
    expect(types.map((b) => b.label)).toEqual(['Pacote de viagem', 'Locação de veículo']);
    expect(types[0].ratio).toBe(1);
  });

  it('re-fetches when the period changes and clears to all-time', () => {
    const comp = build();
    comp.ngOnInit();
    comp['createdFrom'] = new Date(2026, 0, 1);
    comp['applyPeriod']();
    expect(bookings.indicators).toHaveBeenLastCalledWith('2026-01-01', expect.any(String));
    comp['clearPeriod']();
    expect(bookings.indicators).toHaveBeenLastCalledWith(null, null);
  });

  it('shows an error message when the load fails', () => {
    bookings.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('indicadores');
  });

  describe('formatDuration', () => {
    it('formats null as a dash, and seconds as days / hours / minutes', () => {
      expect(formatDuration(null)).toBe('—');
      expect(formatDuration(172800)).toBe('2.0 dias');
      expect(formatDuration(7200)).toBe('2.0 h');
      expect(formatDuration(1800)).toBe('30 min');
    });
  });

  describe('DOM rendering', () => {
    it('renders the KPI cards and the by-status / by-type bars', () => {
      bookings.indicators.mockReturnValue(of(stub));
      const el = render();
      expect(el.textContent).toContain('Total no período');
      expect(el.textContent).toContain('Prontas p/ Financeiro');
      expect(el.textContent).toContain('Tempo médio até confirmação');
      expect(el.textContent).toContain('2.0 dias'); // avg confirmation
      expect(el.textContent).toContain('Por status');
      expect(el.textContent).toContain('Pendente');
      expect(el.textContent).toContain('Pacote de viagem');
    });

    it('renders the error message when the load fails', () => {
      bookings.indicators.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      expect(render().textContent).toContain('indicadores');
    });
  });
});
