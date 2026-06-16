import { Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ListboxModule } from 'primeng/listbox';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../auth/auth.service';
import { ThemeService } from '../theme/theme.service';

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

/**
 * Authenticated shell: a sidebar for navigation, a top bar with the command palette and the
 * light/dark toggle, and global keyboard accelerators (Ctrl/Cmd+K, g-then-key, n, ?).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DialogModule, ListboxModule, ButtonModule, ToastModule],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);

  protected readonly paletteOpen = signal(false);
  protected readonly helpOpen = signal(false);
  protected readonly sidebarOpen = signal(false);
  private goPending = false;

  /** Início is always available; Leads + Pendências only when the user can read Leads. */
  protected get nav(): NavLink[] {
    const items: NavLink[] = [{ label: 'Início', icon: 'pi pi-home', link: '/', exact: true }];
    if (this.auth.canSeeLeads()) {
      items.push({ label: 'Leads', icon: 'pi pi-list', link: '/leads', exact: false });
      items.push({ label: 'Pendências', icon: 'pi pi-flag', link: '/pendencias', exact: true });
    }
    return items;
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
    { label: 'Início', icon: 'pi pi-home', run: () => this.go('/') },
    { label: 'Cadastro: Origens', icon: 'pi pi-database', run: () => this.go('/cadastros/origens') },
    { label: 'Cadastro: Motivos de perda', icon: 'pi pi-database', run: () => this.go('/cadastros/motivos-perda') },
    { label: 'Cadastro: Tipos de interação', icon: 'pi pi-database', run: () => this.go('/cadastros/tipos-interacao') },
    {
      label: 'Cadastro: Resultados de interação',
      icon: 'pi pi-database',
      run: () => this.go('/cadastros/resultados-interacao'),
    },
    { label: 'Alternar tema claro/escuro', icon: 'pi pi-moon', run: () => this.theme.toggle() },
    { label: 'Atalhos do teclado', icon: 'pi pi-question-circle', run: () => this.helpOpen.set(true) },
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
      !!target && (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable);
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
