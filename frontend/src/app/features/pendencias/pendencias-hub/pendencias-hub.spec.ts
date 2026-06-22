import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { PendenciasHub } from './pendencias-hub';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('PendenciasHub', () => {
  const auth = {
    canSeeLeads: vi.fn(() => false),
    canSeeOpportunities: vi.fn(() => false),
    canSeeBookings: vi.fn(() => false),
  };

  function instance(): PendenciasHub {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
    return TestBed.runInInjectionContext(() => new PendenciasHub());
  }

  function render() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PendenciasHub],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
    const fixture = TestBed.createComponent(PendenciasHub);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    auth.canSeeLeads.mockReset().mockReturnValue(false);
    auth.canSeeOpportunities.mockReset().mockReturnValue(false);
    auth.canSeeBookings.mockReset().mockReturnValue(false);
  });

  it('shows only the tabs the profile can see, in funnel order', () => {
    auth.canSeeOpportunities.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['oportunidades']);

    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeBookings.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['leads', 'oportunidades', 'reservas']);
  });

  it('shows only the Reservas tab for a booking-operations-only profile', () => {
    auth.canSeeBookings.mockReturnValue(true);
    expect(instance()['tabs']().map((t) => t.key)).toEqual(['reservas']);
  });

  it('defaults the active tab to the first visible one and switches on select', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeOpportunities.mockReturnValue(true);
    const hub = instance();
    expect(hub['active']()).toBe('leads');
    hub['select']('oportunidades');
    expect(hub['active']()).toBe('oportunidades');
  });

  it('renders the visible tab buttons with the first active (DOM)', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeOpportunities.mockReturnValue(true);
    const el = render();
    const tabs = Array.from(el.querySelectorAll('.hub-tab'));
    expect(tabs.map((t) => t.textContent?.trim())).toEqual(['Leads', 'Oportunidades']);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
  });

  it('renders a no-access notice when the profile can see no pending lists (DOM)', () => {
    const el = render(); // both canSee* false
    expect(el.querySelectorAll('.hub-tab')).toHaveLength(0);
    expect(el.textContent).toContain('não tem acesso');
  });
});
