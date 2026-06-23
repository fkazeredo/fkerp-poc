import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { ListboxModule } from 'primeng/listbox';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../auth/auth.service';
import { ThemeService } from '../theme/theme.service';
import { VersionService } from '../api/version.service';
import { UnsavedChangesService } from '../forms/unsaved-changes.service';
import { NavigationService } from '../navigation/navigation';

interface Command {
  label: string;
  icon: string;
  run: () => void;
}

/**
 * Authenticated shell: a module-oriented sidebar (collapsible accordion sections, persisted), a top bar with
 * the command palette and the light/dark toggle, and global keyboard accelerators (Ctrl/Cmd+K, g-then-key,
 * n, ?). The sidebar, command palette and home pages are all driven by the single {@link NavigationService}.
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
    ConfirmDialogModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly version = inject(VersionService);
  protected readonly unsaved = inject(UnsavedChangesService);
  protected readonly nav = inject(NavigationService);

  protected readonly paletteOpen = signal(false);
  protected readonly helpOpen = signal(false);
  protected readonly sidebarOpen = signal(false);
  // The module whose section is open in the sidebar. Defaults to the one matching the current route, so the
  // menu stays short (only the active module's sub-menu is shown); clicking another module switches it.
  protected readonly openModule = signal<string | null>(null);
  private goPending = false;

  constructor() {
    this.openModule.set(this.activeModuleId());
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.openModule.set(this.activeModuleId()));
  }

  /**
   * The modules the user can navigate (single source of truth, shared with the homes). A memoized signal: it
   * recomputes only when the auth scopes change (login/logout), so the array keeps a stable identity between
   * change-detection cycles. That stability is essential — fed a fresh array every cycle, the command
   * palette's p-listbox re-renders and detaches its option DOM mid-click, silently dropping the selection.
   */
  protected readonly modules = computed(() => this.nav.modules());

  /** Whether a module's sub-menu is open (only one at a time — the selected/active module). */
  protected isOpen(id: string): boolean {
    return this.openModule() === id;
  }

  /** Opens a module's sub-menu (accordion: opening one closes the others); toggles the active one shut. */
  protected toggleSection(id: string): void {
    this.openModule.update((cur) => (cur === id ? null : id));
  }

  /** The module whose home/section matches the current route, or null on the system home. */
  private activeModuleId(): string | null {
    const url = this.router.url.split('?')[0].split('#')[0];
    const matches = (link: string) => url === link || url.startsWith(link + '/');
    for (const m of this.nav.modules()) {
      if (matches(m.home) || m.items.some((i) => matches(i.link)) || m.actions.some((a) => matches(a.link))) {
        return m.id;
      }
    }
    return null;
  }

  /**
   * The command palette, derived from the navigation config plus the global actions. A memoized signal (see
   * {@link modules}) so the option list keeps a stable identity across change-detection cycles; otherwise the
   * p-listbox re-renders every cycle and its option elements detach mid-click, silently dropping the
   * selection (the click never lands).
   */
  protected readonly commands = computed<Command[]>(() => {
    const navCommands: Command[] = [{ label: 'Início', icon: 'pi pi-home', run: () => this.go('/') }];
    for (const m of this.modules()) {
      navCommands.push({ label: m.title, icon: m.icon, run: () => this.go(m.home) });
      for (const item of [...m.items, ...m.actions]) {
        navCommands.push({ label: item.label, icon: item.icon, run: () => this.go(item.link) });
      }
    }
    return [
      ...navCommands,
      { label: 'Alternar tema claro/escuro', icon: 'pi pi-moon', run: () => this.theme.toggle() },
      { label: 'Atalhos do teclado', icon: 'pi pi-question-circle', run: () => this.helpOpen.set(true) },
      { label: 'Sair', icon: 'pi pi-sign-out', run: () => this.logout() },
    ];
  });

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

  /** Warns before closing the tab / reloading while there is an in-progress edit. */
  @HostListener('window:beforeunload', ['$event'])
  protected onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.unsaved.dirty()) {
      event.preventDefault();
      event.returnValue = '';
    }
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
      else if (event.key === 'o') this.go('/oportunidades');
      else if (event.key === 'p') this.go('/propostas');
      else if (event.key === 'd') this.go('/pedidos');
      else if (event.key === 'r') this.go('/reservas');
      else if (event.key === 'f') this.go('/financeiro/contas-a-receber');
      else if (event.key === 'c') this.go('/cadastros');
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
