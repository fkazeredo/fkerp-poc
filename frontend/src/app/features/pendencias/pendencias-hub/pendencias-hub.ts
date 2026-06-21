import { Component, computed, signal, inject } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { LeadPending } from '../../leads/lead-pending/lead-pending';
import { OpportunityPending } from '../../opportunities/opportunity-pending/opportunity-pending';

type PendenciasTab = 'leads' | 'oportunidades';

interface TabDef {
  key: PendenciasTab;
  label: string;
}

/**
 * Pending-items hub — "one place" for every funnel worklist. Renders the existing pending screens (Leads /
 * Oportunidades) under a single tab bar, showing only the tabs the profile may see. The area components are
 * reused verbatim (each keeps its own header and data loading); the hub only picks which one is shown.
 * Reservation pending-items arrive in a later slice.
 */
@Component({
  selector: 'app-pendencias-hub',
  imports: [LeadPending, OpportunityPending],
  templateUrl: './pendencias-hub.html',
  styleUrl: './pendencias-hub.css',
})
export class PendenciasHub {
  private readonly auth = inject(AuthService);

  /** The tabs visible to the user, in funnel order (only the areas they can read). */
  protected readonly tabs = computed<TabDef[]>(() => {
    const tabs: TabDef[] = [];
    if (this.auth.canSeeLeads()) tabs.push({ key: 'leads', label: 'Leads' });
    if (this.auth.canSeeOpportunities()) tabs.push({ key: 'oportunidades', label: 'Oportunidades' });
    return tabs;
  });

  private readonly selected = signal<PendenciasTab | null>(null);

  /** The active tab — the explicitly selected one, or the first visible by default. */
  protected readonly active = computed<PendenciasTab | null>(() => {
    const tabs = this.tabs();
    const sel = this.selected();
    if (sel && tabs.some((t) => t.key === sel)) {
      return sel;
    }
    return tabs.length > 0 ? tabs[0].key : null;
  });

  protected select(tab: PendenciasTab): void {
    this.selected.set(tab);
  }
}
