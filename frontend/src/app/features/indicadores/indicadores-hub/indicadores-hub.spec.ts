import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { IndicadoresHub } from './indicadores-hub';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('IndicadoresHub', () => {
  const auth = {
    canSeeLeads: vi.fn(() => false),
    canSeeOpportunities: vi.fn(() => false),
    canSeeProposals: vi.fn(() => false),
    canSeeOrders: vi.fn(() => false),
    canSeeBookings: vi.fn(() => false),
    canSeeReceivables: vi.fn(() => false),
    canSeeCommissions: vi.fn(() => false),
  };

  function instance(): IndicadoresHub {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
    return TestBed.runInInjectionContext(() => new IndicadoresHub());
  }

  function render() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [IndicadoresHub],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
    const fixture = TestBed.createComponent(IndicadoresHub);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    auth.canSeeLeads.mockReset().mockReturnValue(false);
    auth.canSeeOpportunities.mockReset().mockReturnValue(false);
    auth.canSeeProposals.mockReset().mockReturnValue(false);
    auth.canSeeOrders.mockReset().mockReturnValue(false);
    auth.canSeeBookings.mockReset().mockReturnValue(false);
    auth.canSeeReceivables.mockReset().mockReturnValue(false);
    auth.canSeeCommissions.mockReset().mockReturnValue(false);
  });

  it('shows only the tabs the profile can see, in funnel order', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeOpportunities.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['leads', 'oportunidades']);

    auth.canSeeProposals.mockReturnValue(true);
    auth.canSeeOrders.mockReturnValue(true);
    auth.canSeeBookings.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual([
      'leads',
      'oportunidades',
      'propostas',
      'pedidos',
      'reservas',
    ]);
  });

  it('shows only the Reservas tab for a booking-operations-only profile', () => {
    auth.canSeeBookings.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['reservas']);
  });

  it('shows the Financeiro tab (last) for a profile that can see receivables', () => {
    auth.canSeeOrders.mockReturnValue(true);
    auth.canSeeReceivables.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['pedidos', 'financeiro']);
  });

  it('shows only the Financeiro tab for a finance-only profile', () => {
    auth.canSeeReceivables.mockReturnValue(true);
    const tabs = instance()['tabs']();
    expect(tabs.map((t) => t.key)).toEqual(['financeiro']);
    expect(tabs[0].label).toBe('Financeiro');
  });

  it('shows the Comissões tab (after Financeiro) for a profile that can see commissions', () => {
    auth.canSeeReceivables.mockReturnValue(true);
    auth.canSeeCommissions.mockReturnValue(true);
    const tabs = instance()['tabs']();
    expect(tabs.map((t) => t.key)).toEqual(['financeiro', 'comissoes']);
    expect(tabs[1].label).toBe('Comissões');
  });

  it('shows only the Comissões tab for a commission-read-only profile', () => {
    auth.canSeeCommissions.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['comissoes']);
  });

  it('defaults the active tab to the first visible one', () => {
    auth.canSeeProposals.mockReturnValue(true);
    auth.canSeeOrders.mockReturnValue(true);
    // Leads/opportunities hidden → propostas is the first visible.
    expect(instance()['active']()).toBe('propostas');
  });

  it('switches the active tab when one is selected', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeOrders.mockReturnValue(true);
    const hub = instance();
    expect(hub['active']()).toBe('leads');
    hub['select']('pedidos');
    expect(hub['active']()).toBe('pedidos');
  });

  it('falls back to the first visible tab when no tab is selected', () => {
    auth.canSeeOpportunities.mockReturnValue(true);
    expect(instance()['active']()).toBe('oportunidades');
  });

  it('renders the visible tab buttons with the first active (DOM)', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeOpportunities.mockReturnValue(true);
    const el = render();
    const tabs = Array.from(el.querySelectorAll('.hub-tab'));
    expect(tabs.map((t) => t.textContent?.trim())).toEqual(['Leads', 'Oportunidades']);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
  });

  it('renders a no-access notice when the profile can see no indicators (DOM)', () => {
    const el = render(); // all canSee* false
    expect(el.querySelectorAll('.hub-tab')).toHaveLength(0);
    expect(el.textContent).toContain('não tem acesso');
  });
});
