import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { Shell } from './shell';
import { AuthService } from '../auth/auth.service';

describe('Shell keyboard shortcuts', () => {
  const router = { navigateByUrl: vi.fn(), events: of(), url: '/' };
  const auth = {
    logout: vi.fn(() => of(undefined)),
    canSeeLeads: vi.fn(() => false),
    canCreateLead: vi.fn(() => false),
    canSeeOpportunities: vi.fn(() => false),
    canSeeProposals: vi.fn(() => false),
    canSeeOrders: vi.fn(() => false),
    canSeeBookings: vi.fn(() => false),
  };

  function build(): Shell {
    // Reset first so a test can build a second shell (e.g. to verify persisted sidebar state is restored).
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ConfirmationService,
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
      ],
    });
    // Instantiate with DI but without rendering the routerLink template.
    return TestBed.runInInjectionContext(() => new Shell());
  }

  beforeEach(() => {
    router.navigateByUrl.mockReset();
    auth.logout.mockClear();
    localStorage.clear();
  });

  function key(init: KeyboardEventInit): KeyboardEvent {
    return new KeyboardEvent('keydown', init);
  }

  it('opens the command palette on Ctrl/Cmd+K', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'k', ctrlKey: true }));
    expect(shell['paletteOpen']()).toBe(true);
  });

  it('navigates to a new lead on "n"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'n' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/leads/new');
  });

  it('navigates to the lead list on "g" then "l"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'l' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/leads');
  });

  it('navigates to opportunities on "g" then "o" and proposals on "g" then "p"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'o' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/oportunidades');
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'p' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/propostas');
  });

  it('navigates to the Reservas list on "g" then "r"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'r' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/reservas');
  });

  it('navigates to the Cadastros module home on "g" then "c"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'c' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/cadastros');
  });

  it('opens the shortcut help on "?"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: '?' }));
    expect(shell['helpOpen']()).toBe(true);
  });

  it('opens one module sub-menu at a time (accordion); the others stay collapsed', () => {
    const shell = build();
    // No module is active on the system home, so the menu starts compact (nothing open).
    expect(shell['isOpen']('comercial')).toBe(false);
    expect(shell['isOpen']('reservas')).toBe(false);

    shell['toggleSection']('comercial');
    expect(shell['isOpen']('comercial')).toBe(true);

    // Opening another closes the first (only one sub-menu open at a time = short menu).
    shell['toggleSection']('reservas');
    expect(shell['isOpen']('reservas')).toBe(true);
    expect(shell['isOpen']('comercial')).toBe(false);

    // Toggling the open one closes it.
    shell['toggleSection']('reservas');
    expect(shell['isOpen']('reservas')).toBe(false);
  });

  it('auto-opens the module matching the current route so the menu shows where you are', () => {
    auth.canSeeProposals.mockReturnValue(true);
    router.url = '/propostas/p1' as never;
    const shell = build();
    // Propostas now live in the Comercial funnel module.
    expect(shell['isOpen']('comercial')).toBe(true);
    expect(shell['isOpen']('cadastros')).toBe(false);
    router.url = '/' as never;
    auth.canSeeProposals.mockReturnValue(false);
  });

  it('ignores single-letter shortcuts while typing in a field', () => {
    const shell = build();
    const input = document.createElement('input'); // tagName === 'INPUT'
    const event = key({ key: 'n' });
    Object.defineProperty(event, 'target', { value: input });
    shell['onKeydown'](event);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('derives the command palette from the navigation config plus the global actions', () => {
    auth.canSeeProposals.mockReturnValue(true);
    const labels = build()['commands'].map((c) => c.label);
    // Always: Início + the global actions; Comercial (proposals visible) + Cadastros (always).
    expect(labels).toContain('Início');
    expect(labels).toContain('Comercial');
    expect(labels).toContain('Propostas');
    expect(labels).toContain('Cadastros');
    expect(labels).toContain('Sair');
    expect(labels).not.toContain('Leads'); // no lead access in this build
    auth.canSeeProposals.mockReturnValue(false);
  });

  it('lists the Reservas destination in the command palette when bookings are visible', () => {
    auth.canSeeBookings.mockReturnValue(true);
    const labels = build()['commands'].map((c) => c.label);
    expect(labels).toContain('Reservas');
    auth.canSeeBookings.mockReturnValue(false);
  });

  it('runs a command and closes the palette', () => {
    const shell = build();
    const run = vi.fn();
    shell['paletteOpen'].set(true);
    shell['runCommand']({ label: 'X', icon: '', run });
    expect(run).toHaveBeenCalled();
    expect(shell['paletteOpen']()).toBe(false);
  });

  it('opens the palette and toggles the mobile sidebar', () => {
    const shell = build();
    shell['openPalette']();
    expect(shell['paletteOpen']()).toBe(true);
    expect(shell['sidebarOpen']()).toBe(false);
    shell['toggleSidebar']();
    expect(shell['sidebarOpen']()).toBe(true);
  });

  it('logs out and returns to the login screen', () => {
    const shell = build();
    shell['logout']();
    expect(auth.logout).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('blocks tab close only while there is an in-progress edit', () => {
    const shell = build();
    const clean = { preventDefault: vi.fn(), returnValue: undefined } as unknown as BeforeUnloadEvent;
    shell['onBeforeUnload'](clean);
    expect(clean.preventDefault).not.toHaveBeenCalled();

    shell['unsaved'].set(true);
    const dirty = { preventDefault: vi.fn(), returnValue: undefined } as unknown as BeforeUnloadEvent;
    shell['onBeforeUnload'](dirty);
    expect(dirty.preventDefault).toHaveBeenCalled();
    expect(dirty.returnValue).toBe('');
    shell['unsaved'].set(false);
  });

  describe('DOM rendering', () => {
    it('renders the module-oriented sidebar with the accessible modules and the brand', () => {
      auth.canSeeProposals.mockReturnValue(true);
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [Shell],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          ConfirmationService,
          MessageService,
          provideRouter([]),
          { provide: AuthService, useValue: auth },
        ],
      });
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;

      expect(el.querySelector('.sidebar')).not.toBeNull();
      expect(el.textContent).toContain('FKERP');
      expect(el.textContent).toContain('Início');
      // Module HEADERS are always shown (short menu); their sub-menus are collapsed on the system home.
      expect(el.textContent).toContain('Comercial'); // proposals visible → funnel module shown
      expect(el.textContent).toContain('Cadastros'); // always present
      expect(el.querySelectorAll('.nav-section-items.collapsed').length).toBeGreaterThan(0);
      // No lead/opportunity scopes in this build → the Leads destination is not shown.
      expect(el.textContent).not.toContain('Leads');
      auth.canSeeProposals.mockReturnValue(false);
    });
  });
});
