import { Injectable, signal } from '@angular/core';

type Theme = 'light' | 'dark';
const STORAGE_KEY = 'fkerp-theme';

/**
 * Light/dark theme state. Toggling adds/removes the `.app-dark` class on the document root, which
 * both PrimeNG (darkModeSelector) and the app's own design tokens react to. The choice is persisted
 * in localStorage and falls back to the OS preference on first visit.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly darkSignal = signal(false);
  readonly dark = this.darkSignal.asReadonly();

  constructor() {
    this.apply(this.initialPreference());
  }

  toggle(): void {
    this.set(this.darkSignal() ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.apply(theme === 'dark');
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // localStorage may be unavailable (private mode); the theme still applies for the session.
    }
  }

  private apply(dark: boolean): void {
    this.darkSignal.set(dark);
    document.documentElement.classList.toggle('app-dark', dark);
  }

  private initialPreference(): boolean {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'dark') {
        return true;
      }
      if (saved === 'light') {
        return false;
      }
    } catch {
      // ignore and fall back to the OS preference
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
