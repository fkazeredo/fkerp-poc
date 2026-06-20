import { Injectable, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/** A navigable destination: a sidebar link and/or a tile on a module home. */
export interface NavLink {
  label: string;
  icon: string;
  link: string;
  /** Exact router-link matching for the active state. */
  exact: boolean;
  /** Short description shown on the home tile. */
  desc: string;
  /** Accent key for the tile icon colour (see module-home.css / home.css). */
  accent?: string;
}

/**
 * A business module (bounded context) the user can navigate: its own home page plus its destinations. This
 * is the single source of truth for the sidebar (accordion sections), the system home (module cards) and
 * each module's home (tiles), so the three never drift apart.
 */
export interface NavModule {
  id: string;
  title: string;
  icon: string;
  accent: string;
  /** The module's home route. */
  home: string;
  desc: string;
  /** Navigational destinations (shown both in the sidebar and as home tiles). */
  items: NavLink[];
  /** Action-style entries (shown only as home tiles, e.g. "Novo lead"), not in the sidebar. */
  actions: NavLink[];
}

/**
 * Builds the module-oriented navigation from the user's access. One place defines the modules and their
 * destinations; the shell renders them as collapsible sections, the system home as module cards, and each
 * module home as tiles. Visibility mirrors the backend authority (the backend is still the only guard).
 */
@Injectable({ providedIn: 'root' })
export class NavigationService {
  private readonly auth = inject(AuthService);

  /** The modules visible to the current user, in display order. */
  modules(): NavModule[] {
    return this.allModules().filter((m) => m.items.length > 0 || m.actions.length > 0);
  }

  /** A single module by id (or undefined when not visible). */
  module(id: string): NavModule | undefined {
    return this.modules().find((m) => m.id === id);
  }

  private allModules(): NavModule[] {
    const crm: NavLink[] = [];
    const crmActions: NavLink[] = [];
    if (this.auth.canSeeLeads()) {
      crm.push(
        link('Leads', 'pi pi-list', '/leads', false, 'Veja, busque e filtre os leads que você pode trabalhar.', 'leads'),
        link('Pendências', 'pi pi-flag', '/pendencias', true, 'Leads que precisam de ação para não perder oportunidades.', 'pending'),
        link('Indicadores', 'pi pi-chart-bar', '/indicadores', true, 'O topo do funil: total, por status, origem e responsável.', 'indicators'),
      );
    }
    if (this.auth.canCreateLead()) {
      crmActions.push(
        link('Novo lead', 'pi pi-user-plus', '/leads/new', false, 'Registre um novo interessado e a primeira anotação.', 'new'),
      );
    }
    if (this.auth.canSeeOpportunities()) {
      crm.push(
        link('Oportunidades', 'pi pi-briefcase', '/oportunidades', false, 'O funil comercial: negociações em andamento.', 'leads'),
        link('Oportunidades pendentes', 'pi pi-flag', '/oportunidades/pendencias', true, 'Oportunidades que precisam de ação.', 'pending'),
        link('Indicadores de oportunidades', 'pi pi-chart-bar', '/oportunidades/indicadores', true, 'Volume e pipeline comercial.', 'indicators'),
      );
    }

    const sales: NavLink[] = [];
    if (this.auth.canSeeProposals()) {
      sales.push(
        link('Propostas', 'pi pi-file-edit', '/propostas', false, 'As propostas comerciais — formalize a oferta ao cliente.', 'sales'),
        link('Indicadores de propostas', 'pi pi-chart-bar', '/propostas/indicadores', true, 'Volume e fluxo das propostas.', 'indicators'),
      );
    }
    if (this.auth.canSeeOrders()) {
      sales.push(
        link('Pedidos', 'pi pi-shopping-bag', '/pedidos', false, 'Os pedidos comerciais — acompanhe os negócios fechados.', 'sales'),
        link('Indicadores de pedidos', 'pi pi-chart-bar', '/pedidos/indicadores', true, 'Pedidos fechados e pendentes de reserva.', 'indicators'),
      );
    }

    const cadastros: NavLink[] = [
      link('Origens', 'pi pi-database', '/cadastros/origens', false, 'De onde vêm os leads.', 'ref'),
      link('Motivos de perda', 'pi pi-database', '/cadastros/motivos-perda', false, 'Por que um lead é perdido.', 'ref'),
      link('Tipos de interação', 'pi pi-database', '/cadastros/tipos-interacao', false, 'Tipos de contato registrados.', 'ref'),
      link('Resultados de interação', 'pi pi-database', '/cadastros/resultados-interacao', false, 'Resultados possíveis de um contato.', 'ref'),
    ];

    return [
      module('crm', 'Comercial / CRM', 'pi pi-briefcase', 'leads', '/crm', 'Leads e oportunidades da equipe comercial.', crm, crmActions),
      module('vendas', 'Vendas', 'pi pi-shopping-cart', 'sales', '/vendas', 'Propostas comerciais.', sales, []),
      module('cadastros', 'Cadastros', 'pi pi-database', 'ref', '/cadastros', 'As listas que alimentam os fluxos.', cadastros, []),
    ];
  }
}

function link(
  label: string,
  icon: string,
  url: string,
  exact: boolean,
  desc: string,
  accent: string,
): NavLink {
  return { label, icon, link: url, exact, desc, accent };
}

function module(
  id: string,
  title: string,
  icon: string,
  accent: string,
  home: string,
  desc: string,
  items: NavLink[],
  actions: NavLink[],
): NavModule {
  return { id, title, icon, accent, home, desc, items, actions };
}
