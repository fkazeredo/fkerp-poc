import { Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
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
  private static readonly COLLAPSE_KEY = 'fkerp.sidebar.collapsed';

  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly version = inject(VersionService);
  protected readonly unsaved = inject(UnsavedChangesService);
  protected readonly nav = inject(NavigationService);

  protected readonly paletteOpen = signal(false);
  protected readonly helpOpen = signal(false);
  protected readonly sidebarOpen = signal(false);
  // Ids of the sidebar modules whose section is collapsed (persisted across sessions).
  protected readonly collapsed = signal<Set<string>>(this.loadCollapsed());
  private goPending = false;

  /** The modules the user can navigate (single source of truth, shared with the homes). */
  protected get modules() {
    return this.nav.modules();
  }

  protected isCollapsed(id: string): boolean {
    return this.collapsed().has(id);
  }

  /** Expands/collapses a sidebar module section and persists the choice. */
  protected toggleSection(id: string): void {
    const next = new Set(this.collapsed());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.collapsed.set(next);
    try {
      localStorage.setItem(Shell.COLLAPSE_KEY, JSON.stringify([...next]));
    } catch {
      // Ignore storage failures (private mode); the in-memory state still works for the session.
    }
  }

  private loadCollapsed(): Set<string> {
    try {
      const raw = localStorage.getItem(Shell.COLLAPSE_KEY);
      return new Set(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      return new Set();
    }
  }

  /** The command palette, derived from the navigation config plus the global actions. */
  protected get commands(): Command[] {
    const navCommands: Command[] = [{ label: 'Início', icon: 'pi pi-home', run: () => this.go('/') }];
    for (const m of this.modules) {
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
  }

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
