import { TestBed } from '@angular/core/testing';
import { NavigationService } from './navigation';
import { AuthService } from '../auth/auth.service';

describe('NavigationService', () => {
  const auth = {
    canSeeLeads: vi.fn(),
    canCreateLead: vi.fn(),
    canSeeOpportunities: vi.fn(),
    canSeeProposals: vi.fn(),
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
  });

  it('shows only the modules the user can access', () => {
    auth.canSeeProposals.mockReturnValue(true);
    const nav = build();
    const ids = nav.modules().map((m) => m.id);
    // Cadastros is always available; Vendas because proposals are visible; CRM hidden (no lead/opp access).
    expect(ids).toEqual(['vendas', 'cadastros']);
  });

  it('hides every module when the user has no access at all', () => {
    const nav = build();
    // Cadastros has fixed items, so it always survives; CRM and Vendas are gone.
    expect(nav.modules().map((m) => m.id)).toEqual(['cadastros']);
  });

  it('builds the CRM module from the lead and opportunity scopes independently', () => {
    auth.canSeeLeads.mockReturnValue(true);
    const nav = build();
    const crm = nav.module('crm');
    expect(crm).toBeDefined();
    const links = crm!.items.map((i) => i.link);
    expect(links).toContain('/leads');
    expect(links).not.toContain('/oportunidades'); // no opportunity scope yet

    auth.canSeeOpportunities.mockReturnValue(true);
    const nav2 = build();
    expect(nav2.module('crm')!.items.map((i) => i.link)).toContain('/oportunidades');
  });

  it('exposes "Novo lead" only as an action (home tile), not a sidebar item, and only with create scope', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canCreateLead.mockReturnValue(true);
    const crm = build().module('crm')!;
    expect(crm.items.map((i) => i.link)).not.toContain('/leads/new');
    expect(crm.actions.map((a) => a.link)).toContain('/leads/new');

    auth.canCreateLead.mockReturnValue(false);
    expect(build().module('crm')!.actions).toHaveLength(0);
  });

  it('every module points at its own home route', () => {
    auth.canSeeLeads.mockReturnValue(true);
    auth.canSeeProposals.mockReturnValue(true);
    const homes = Object.fromEntries(build().modules().map((m) => [m.id, m.home]));
    expect(homes['crm']).toBe('/crm');
    expect(homes['vendas']).toBe('/vendas');
    expect(homes['cadastros']).toBe('/cadastros');
  });

  it('returns undefined for a module the user cannot see', () => {
    const nav = build(); // no proposal access
    expect(nav.module('vendas')).toBeUndefined();
  });
});
