import { TestBed } from '@angular/core/testing';
import { NavigationService } from './navigation';
import { AuthService } from '../auth/auth.service';

describe('NavigationService', () => {
  const auth = {
    canSeeLeads: vi.fn(),
    canCreateLead: vi.fn(),
    canSeeOpportunities: vi.fn(),
    canSeeProposals: vi.fn(),
    canSeeOrders: vi.fn(),
    canSeeBookings: vi.fn(),
    canManageWorkflows: vi.fn(() => false),
  };

  function build(): NavigationService {
    // The service reads the auth scopes lazily on each modules()/module() call, so a test can re-build
    // after changing the mock; reset the module first to allow reconfiguring within a single test.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
    return TestBed.inject(NavigationService);
  }

  beforeEach(() => {
    auth.canSeeLeads.mockReset().mockReturnValue(false);
    auth.canCreateLead.mockReset().mockReturnValue(false);
    auth.canSeeOpportunities.mockReset().mockReturnValue(false);
    auth.canSeeProposals.mockReset().mockReturnValue(false);
    auth.canSeeOrders.mockReset().mockReturnValue(false);
    auth.canSeeBookings.mockReset().mockReturnValue(false);
    auth.canManageWorkflows.mockReset().mockReturnValue(false);
  });

  it('shows only the modules the user can access', () => {
    auth.canSeeProposals.mockReturnValue(true);
    const nav = build();
    const ids = nav.modules().map((m) => m.id);
    // Cadastros is always available; Comercial because proposals are visible; Acompanhamento because the
    // indicators hub spans proposals; Reservas hidden (no booking access).
    expect(ids).toEqual(['comercial', 'acompanhamento', 'cadastros']);
  });

  it('hides every funnel module when the user has no access at all', () => {
    const nav = build();
    // Cadastros has fixed items, so it always survives; the funnel modules are gone.
    expect(nav.modules().map((m) => m.id)).toEqual(['cadastros']);
  });

  it('builds the Comercial module funnel from each read scope independently, in order', () => {
    auth.canSeeLeads.mockReturnValue(true);
    let comercial = build().module('comercial')!;
    expect(comercial.items.map((i) => i.link)).toEqual(['/leads']);

    auth.canSeeOpportunities.mockReturnValue(true);
    auth.canSeeProposals.mockReturnValue(true);
    auth.canSeeOrders.mockReturnValue(true);
    comercial = build().module('comercial')!;
    expect(comercial.items.map((i) => i.link)).toEqual([
      '/leads',
      '/oportunidades',
      '/propostas',
      '/pedidos',
    ]);
  });

  it('exposes "Novo lead" only as an action (home tile), not a sidebar item, and only with create scope', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canCreateLead.mockReturnValue(true);
    const comercial = build().module('comercial')!;
    expect(comercial.items.map((i) => i.link)).not.toContain('/leads/new');
    expect(comercial.actions.map((a) => a.link)).toContain('/leads/new');

    auth.canCreateLead.mockReturnValue(false);
    expect(build().module('comercial')!.actions).toHaveLength(0);
  });

  it('exposes a standalone "Fluxos de trabalho" module that expands into the 6 workflows, only with the scope', () => {
    // Without the scope the module is absent (and never lived under Cadastros).
    expect(build().module('workflows')).toBeUndefined();

    auth.canManageWorkflows.mockReturnValue(true);
    const wf = build().module('workflows');
    expect(wf?.title).toBe('Fluxos de trabalho');
    expect(wf?.home).toBe('/fluxos');
    expect(wf?.items.map((i) => i.link)).toEqual([
      '/fluxos/lead',
      '/fluxos/opportunity',
      '/fluxos/proposal',
      '/fluxos/order',
      '/fluxos/booking_request',
      '/fluxos/booking_item',
    ]);
    // Not under Cadastros.
    expect(build().module('cadastros')!.items.map((i) => i.link)).not.toContain('/fluxos');
  });

  it('shows the Reservas module only when the user can see bookings', () => {
    auth.canSeeBookings.mockReturnValue(false);
    expect(build().module('reservas')).toBeUndefined();

    auth.canSeeBookings.mockReturnValue(true);
    const reservas = build().module('reservas')!;
    expect(reservas.home).toBe('/reservas');
    expect(reservas.items.map((i) => i.link)).toEqual(['/reservas']);
  });

  it('builds the Acompanhamento hubs: pendencias from CRM scopes, indicadores from any funnel scope', () => {
    // Only orders visible → no pendencias (CRM-only), but indicadores appears (spans orders).
    auth.canSeeOrders.mockReturnValue(true);
    let acomp = build().module('acompanhamento')!;
    expect(acomp.items.map((i) => i.link)).toEqual(['/indicadores']);

    // Leads visible → pendencias appears too.
    auth.canSeeLeads.mockReturnValue(true);
    acomp = build().module('acompanhamento')!;
    expect(acomp.items.map((i) => i.link)).toEqual(['/pendencias', '/indicadores']);
  });

  it('every module points at its own home route', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeBookings.mockReturnValue(true);
    const homes = Object.fromEntries(build().modules().map((m) => [m.id, m.home]));
    expect(homes['comercial']).toBe('/comercial');
    expect(homes['reservas']).toBe('/reservas');
    expect(homes['acompanhamento']).toBe('/acompanhamento');
    expect(homes['cadastros']).toBe('/cadastros');
  });

  it('returns undefined for a module the user cannot see', () => {
    const nav = build(); // no funnel access
    expect(nav.module('comercial')).toBeUndefined();
    expect(nav.module('reservas')).toBeUndefined();
  });
});
