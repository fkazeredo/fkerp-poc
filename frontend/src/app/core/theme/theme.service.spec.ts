import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
  });

  it('toggles dark mode, reflects it on the document root and persists it', () => {
    const service = TestBed.inject(ThemeService);
    const start = service.dark();

    service.toggle();
    expect(service.dark()).toBe(!start);
    expect(document.documentElement.classList.contains('app-dark')).toBe(!start);

    service.set('dark');
    expect(service.dark()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
    expect(localStorage.getItem('fkerp-theme')).toBe('dark');

    service.set('light');
    expect(service.dark()).toBe(false);
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
    expect(localStorage.getItem('fkerp-theme')).toBe('light');
  });
});
