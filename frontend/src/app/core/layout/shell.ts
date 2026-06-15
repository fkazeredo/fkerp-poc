import { Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ListboxModule } from 'primeng/listbox';
import { MenubarModule } from 'primeng/menubar';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../auth/auth.service';

interface Command {
  label: string;
  icon: string;
  run: () => void;
}

/** Authenticated shell: top menubar, a Ctrl/Cmd+K command palette and keyboard accelerators. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, MenubarModule, DialogModule, ListboxModule, ButtonModule, ToastModule],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly paletteOpen = signal(false);
  private goPending = false;

  protected readonly menu: MenuItem[] = [
    { label: 'Início', icon: 'pi pi-home', command: () => this.go('/') },
    { label: 'Novo Lead', icon: 'pi pi-user-plus', command: () => this.go('/leads/new') },
    {
      label: 'Cadastros',
      icon: 'pi pi-database',
      items: [
        { label: 'Origens', command: () => this.go('/cadastros/origens') },
        { label: 'Motivos de perda', command: () => this.go('/cadastros/motivos-perda') },
        { label: 'Tipos de interação', command: () => this.go('/cadastros/tipos-interacao') },
        {
          label: 'Resultados de interação',
          command: () => this.go('/cadastros/resultados-interacao'),
        },
      ],
    },
  ];

  protected readonly commands: Command[] = [
    { label: 'Novo Lead', icon: 'pi pi-user-plus', run: () => this.go('/leads/new') },
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
    { label: 'Sair', icon: 'pi pi-sign-out', run: () => this.logout() },
  ];

  protected openPalette(): void {
    this.paletteOpen.set(true);
  }

  protected runCommand(command: Command): void {
    this.paletteOpen.set(false);
    command.run();
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }

  private go(url: string): void {
    this.paletteOpen.set(false);
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
      else if (event.key === 'n') this.go('/leads/new');
      else if (event.key === 'o') this.go('/cadastros/origens');
      return;
    }
    if (event.key === 'g') {
      this.goPending = true;
    } else if (event.key === 'n') {
      this.go('/leads/new');
    }
  }
}
