import { Component, computed, signal, inject } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { LeadIndicatorsPage } from '../../leads/lead-indicators/lead-indicators';
import { OpportunityIndicatorsPage } from '../../opportunities/opportunity-indicators/opportunity-indicators';
import { ProposalIndicatorsPage } from '../../proposals/proposal-indicators/proposal-indicators';
import { OrderIndicatorsPage } from '../../orders/order-indicators/order-indicators';
import { BookingIndicatorsPage } from '../../bookings/booking-indicators/booking-indicators';
import { ReceivableIndicatorsPage } from '../../financial/receivable-indicators/receivable-indicators';

type IndicadoresTab = 'leads' | 'oportunidades' | 'propostas' | 'pedidos' | 'reservas' | 'financeiro';

interface TabDef {
  key: IndicadoresTab;
  label: string;
}

/**
 * Indicators hub — "one place" for every funnel indicator. Renders the existing area indicator screens
 * (Leads / Oportunidades / Propostas / Pedidos / Reservas / Financeiro) under a single tab bar, showing only the
 * tabs the profile may see. The area components are reused verbatim (each keeps its own header and data loading);
 * the hub only picks which one is shown. The Financeiro tab is the operational received-payments & collection view
 * — operational figures only, never Commission, Accounts Payable or bank-reconciliation data.
 */
@Component({
  selector: 'app-indicadores-hub',
  imports: [
    LeadIndicatorsPage,
    OpportunityIndicatorsPage,
    ProposalIndicatorsPage,
    OrderIndicatorsPage,
    BookingIndicatorsPage,
    ReceivableIndicatorsPage,
  ],
  templateUrl: './indicadores-hub.html',
  styleUrl: './indicadores-hub.css',
})
export class IndicadoresHub {
  private readonly auth = inject(AuthService);

  /** The tabs visible to the user, in funnel order (only the areas they can read). */
  protected readonly tabs = computed<TabDef[]>(() => {
    const tabs: TabDef[] = [];
    if (this.auth.canSeeLeads()) tabs.push({ key: 'leads', label: 'Leads' });
    if (this.auth.canSeeOpportunities()) tabs.push({ key: 'oportunidades', label: 'Oportunidades' });
    if (this.auth.canSeeProposals()) tabs.push({ key: 'propostas', label: 'Propostas' });
    if (this.auth.canSeeOrders()) tabs.push({ key: 'pedidos', label: 'Pedidos' });
    if (this.auth.canSeeBookings()) tabs.push({ key: 'reservas', label: 'Reservas' });
    if (this.auth.canSeeReceivables()) tabs.push({ key: 'financeiro', label: 'Financeiro' });
    return tabs;
  });

  private readonly selected = signal<IndicadoresTab | null>(null);

  /** The active tab — the explicitly selected one, or the first visible by default. */
  protected readonly active = computed<IndicadoresTab | null>(() => {
    const tabs = this.tabs();
    const sel = this.selected();
    if (sel && tabs.some((t) => t.key === sel)) {
      return sel;
    }
    return tabs.length > 0 ? tabs[0].key : null;
  });

  protected select(tab: IndicadoresTab): void {
    this.selected.set(tab);
  }
}
