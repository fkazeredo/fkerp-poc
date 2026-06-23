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
    // Comercial: the whole sales funnel in order (Lead → Oportunidade → Proposta → Pedido), each step gated.
    const comercial: NavLink[] = [];
    const comercialActions: NavLink[] = [];
    if (this.auth.canSeeLeads()) {
      comercial.push(
        link('Leads', 'pi pi-list', '/leads', false, 'O topo do funil: interessados a trabalhar.', 'leads'),
      );
    }
    if (this.auth.canSeeOpportunities()) {
      comercial.push(
        link('Oportunidades', 'pi pi-briefcase', '/oportunidades', false, 'As negociações em andamento.', 'leads'),
      );
    }
    if (this.auth.canSeeProposals()) {
      comercial.push(
        link('Propostas', 'pi pi-file-edit', '/propostas', false, 'A oferta formal ao cliente.', 'sales'),
      );
    }
    if (this.auth.canSeeOrders()) {
      comercial.push(
        link('Pedidos', 'pi pi-shopping-bag', '/pedidos', false, 'Os negócios fechados.', 'sales'),
      );
    }
    if (this.auth.canCreateLead()) {
      comercialActions.push(
        link('Novo lead', 'pi pi-user-plus', '/leads/new', false, 'Registre um novo interessado e a primeira anotação.', 'new'),
      );
    }

    // Reservas: the operational reservation worklist. A one-item module today (home is the list itself); it
    // grows with the booking detail/attempt slices.
    const reservas: NavLink[] = [];
    if (this.auth.canSeeBookings()) {
      reservas.push(
        link('Reservas', 'pi pi-bookmark', '/reservas', false, 'As solicitações de reserva a operar.', 'sales'),
      );
    }

    // Acompanhamento: the cross-funnel monitoring hubs — one place for all the pending-items lists and one for
    // all the indicators (each is a tabbed hub gated per profile).
    const acompanhamento: NavLink[] = [];
    if (this.auth.canSeeLeads() || this.auth.canSeeOpportunities() || this.auth.canSeeBookings()) {
      acompanhamento.push(
        link('Pendências', 'pi pi-flag', '/pendencias', false, 'O que precisa de ação para não perder negócios.', 'pending'),
      );
    }
    if (
      this.auth.canSeeLeads() ||
      this.auth.canSeeOpportunities() ||
      this.auth.canSeeProposals() ||
      this.auth.canSeeOrders() ||
      this.auth.canSeeBookings()
    ) {
      acompanhamento.push(
        link('Indicadores', 'pi pi-chart-bar', '/indicadores', false, 'Os números do funil, num lugar só.', 'indicators'),
      );
    }

    const cadastros: NavLink[] = [
      link('Origens', 'pi pi-database', '/cadastros/origens', false, 'De onde vêm os leads.', 'ref'),
      link('Motivos de perda', 'pi pi-database', '/cadastros/motivos-perda', false, 'Por que um lead é perdido.', 'ref'),
      link('Tipos de interação', 'pi pi-database', '/cadastros/tipos-interacao', false, 'Tipos de contato registrados.', 'ref'),
      link('Resultados de interação', 'pi pi-database', '/cadastros/resultados-interacao', false, 'Resultados possíveis de um contato.', 'ref'),
      link('Tipos de atividade', 'pi pi-database', '/cadastros/tipos-atividade', false, 'Tipos de atividade comercial da oportunidade.', 'ref'),
      link('Resultados de atividade', 'pi pi-database', '/cadastros/resultados-atividade', false, 'Resultados de uma atividade comercial.', 'ref'),
      link('Motivos de perda (oportunidade)', 'pi pi-database', '/cadastros/motivos-perda-oportunidade', false, 'Por que uma oportunidade é perdida.', 'ref'),
      link('Motivos de rejeição', 'pi pi-database', '/cadastros/motivos-rejeicao', false, 'Motivos de rejeição da proposta na revisão.', 'ref'),
      link('Motivos de recusa do cliente', 'pi pi-database', '/cadastros/motivos-recusa-cliente', false, 'Por que o cliente recusa uma proposta enviada.', 'ref'),
      link('Canais de envio', 'pi pi-database', '/cadastros/canais-envio', false, 'Canais de envio/apresentação da proposta.', 'ref'),
      link('Tipos de item', 'pi pi-database', '/cadastros/tipos-item', false, 'Tipos de item da oferta comercial.', 'ref'),
      link('Tipos de tentativa', 'pi pi-database', '/cadastros/tipos-tentativa', false, 'Tipos de tentativa manual de reserva.', 'ref'),
      link('Resultados de tentativa', 'pi pi-database', '/cadastros/resultados-tentativa', false, 'Resultados de uma tentativa de reserva.', 'ref'),
      link('Motivos de falha', 'pi pi-database', '/cadastros/motivos-falha', false, 'Por que um item de reserva falhou.', 'ref'),
    ];

    // Fluxos de trabalho: a standalone admin-only module (its home is the workflows list itself). Only the
    // administrator (workflow:manage) sees it — a module with no items is filtered out by modules().
    const fluxos: NavLink[] = [];
    if (this.auth.canManageWorkflows()) {
      fluxos.push(
        link('Fluxos de trabalho', 'pi pi-sitemap', '/fluxos', false, 'Os fluxos configuráveis: estados, transições e regras de atenção.', 'ref'),
      );
    }

    return [
      module('comercial', 'Comercial', 'pi pi-briefcase', 'leads', '/comercial', 'O funil comercial: leads, oportunidades, propostas e pedidos.', comercial, comercialActions),
      module('reservas', 'Reservas', 'pi pi-bookmark', 'sales', '/reservas', 'As reservas a operar a partir dos pedidos fechados.', reservas, []),
      module('acompanhamento', 'Acompanhamento', 'pi pi-chart-bar', 'indicators', '/acompanhamento', 'Pendências e indicadores de todo o funil.', acompanhamento, []),
      module('cadastros', 'Cadastros', 'pi pi-database', 'ref', '/cadastros', 'As listas que alimentam os fluxos.', cadastros, []),
      module('workflows', 'Fluxos de trabalho', 'pi pi-sitemap', 'ref', '/fluxos', 'Os fluxos configuráveis do sistema: estados, transições e regras de atenção.', fluxos, []),
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
