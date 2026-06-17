import { Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ListboxModule } from 'primeng/listbox';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../auth/auth.service';
import { ThemeService } from '../theme/theme.service';
import { VersionService } from '../api/version.service';

interface Command {
  label: string;
  icon: string;
  run: () => void;
}

interface NavLink {
  label: string;
  icon: string;
  link: string;
  exact: boolean;
}

/** A navigation module: a titled group of links (the title is omitted for the top-level items). */
interface NavGroup {
  title?: string;
  items: NavLink[];
}

/**
 * Authenticated shell: a sidebar for navigation, a top bar with the command palette and the
 * light/dark toggle, and global keyboard accelerators (Ctrl/Cmd+K, g-then-key, n, ?).
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    DialogModule,
    ListboxModule,
    ButtonModule,
    ToastModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly version = inject(VersionService);

  protected readonly paletteOpen = signal(false);
  protected readonly helpOpen = signal(false);
  protected readonly sidebarOpen = signal(false);
  private goPending = false;

  /**
   * The navigation, split into clear **modules** (bounded contexts): Início at the top; the **Comercial /
   * CRM** module (Leads + Opportunities) when the user can read either; the **Vendas** module (Proposals)
   * when the user can read Proposals. Each group only appears when it has at least one visible item.
   */
  protected get navGroups(): NavGroup[] {
    const groups: NavGroup[] = [
      { items: [{ label: 'Início', icon: 'pi pi-home', link: '/', exact: true }] },
    ];

    const crm: NavLink[] = [];
    if (this.auth.canSeeLeads()) {
      crm.push({ label: 'Leads', icon: 'pi pi-list', link: '/leads', exact: false });
      crm.push({ label: 'Pendências', icon: 'pi pi-flag', link: '/pendencias', exact: true });
      crm.push({ label: 'Indicadores', icon: 'pi pi-chart-bar', link: '/indicadores', exact: true });
    }
    if (this.auth.canSeeOpportunities()) {
      crm.push({ label: 'Oportunidades', icon: 'pi pi-briefcase', link: '/oportunidades', exact: false });
      crm.push({
        label: 'Oportunidades pendentes',
        icon: 'pi pi-flag',
        link: '/oportunidades/pendencias',
        exact: true,
      });
      crm.push({
        label: 'Indicadores de oportunidades',
        icon: 'pi pi-chart-bar',
        link: '/oportunidades/indicadores',
        exact: true,
      });
    }
    if (crm.length > 0) {
      groups.push({ title: 'Comercial / CRM', items: crm });
    }

    const sales: NavLink[] = [];
    if (this.auth.canSeeProposals()) {
      sales.push({ label: 'Propostas', icon: 'pi pi-file-edit', link: '/propostas', exact: false });
    }
    if (sales.length > 0) {
      groups.push({ title: 'Vendas', items: sales });
    }

    return groups;
  }

  protected readonly cadastros: { label: string; link: string }[] = [
    { label: 'Origens', link: '/cadastros/origens' },
    { label: 'Motivos de perda', link: '/cadastros/motivos-perda' },
    { label: 'Tipos de interação', link: '/cadastros/tipos-interacao' },
    { label: 'Resultados de interação', link: '/cadastros/resultados-interacao' },
  ];

  protected readonly commands: Command[] = [
    { label: 'Leads', icon: 'pi pi-list', run: () => this.go('/leads') },
    { label: 'Novo Lead', icon: 'pi pi-user-plus', run: () => this.go('/leads/new') },
    { label: 'Pendências', icon: 'pi pi-flag', run: () => this.go('/pendencias') },
    { label: 'Indicadores', icon: 'pi pi-chart-bar', run: () => this.go('/indicadores') },
    { label: 'Oportunidades', icon: 'pi pi-briefcase', run: () => this.go('/oportunidades') },
    {
      label: 'Oportunidades pendentes',
      icon: 'pi pi-flag',
      run: () => this.go('/oportunidades/pendencias'),
    },
    {
      label: 'Indicadores de oportunidades',
      icon: 'pi pi-chart-bar',
      run: () => this.go('/oportunidades/indicadores'),
    },
    { label: 'Propostas', icon: 'pi pi-file-edit', run: () => this.go('/propostas') },
    { label: 'Início', icon: 'pi pi-home', run: () => this.go('/') },
    {
      label: 'Cadastro: Origens',
      icon: 'pi pi-database',
      run: () => this.go('/cadastros/origens'),
    },
    {
      label: 'Cadastro: Motivos de perda',
      icon: 'pi pi-database',
      run: () => this.go('/cadastros/motivos-perda'),
    },
    {
      label: 'Cadastro: Tipos de interação',
      icon: 'pi pi-database',
      run: () => this.go('/cadastros/tipos-interacao'),
    },
    {
      label: 'Cadastro: Resultados de interação',
      icon: 'pi pi-database',
      run: () => this.go('/cadastros/resultados-interacao'),
    },
    { label: 'Alternar tema claro/escuro', icon: 'pi pi-moon', run: () => this.theme.toggle() },
    {
      label: 'Atalhos do teclado',
      icon: 'pi pi-question-circle',
      run: () => this.helpOpen.set(true),
    },
    { label: 'Sair', icon: 'pi pi-sign-out', run: () => this.logout() },
  ];

  protected openPalette(): void {
    this.paletteOpen.set(true);
  }

  protected runCommand(command: Command): void {
    this.paletteOpen.set(false);
    command.run();
  }

  protected toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }

  private go(url: string): void {
    this.paletteOpen.set(false);
    this.sidebarOpen.set(false);
    this.router.navigateByUrl(url);
  }

  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.set(true);
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing =
      !!target &&
      (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
    if (typing || event.ctrlKey || event.metaKey || event.altKey) {
      this.goPending = false;
      return;
    }
    if (this.goPending) {
      this.goPending = false;
      if (event.key === 'i') this.go('/');
      else if (event.key === 'l') this.go('/leads');
      else if (event.key === 'n') this.go('/leads/new');
      else if (event.key === 'o') this.go('/cadastros/origens');
      return;
    }
    if (event.key === 'g') {
      this.goPending = true;
    } else if (event.key === 'n') {
      this.go('/leads/new');
    } else if (event.key === '?') {
      this.helpOpen.set(true);
    }
  }
}
