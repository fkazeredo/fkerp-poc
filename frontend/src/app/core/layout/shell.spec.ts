import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { Shell } from './shell';
import { AuthService } from '../auth/auth.service';

describe('Shell keyboard shortcuts', () => {
  const router = { navigateByUrl: vi.fn() };
  const auth = { logout: vi.fn(() => of(undefined)) };

  function build(): Shell {
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
      ],
    });
    // Instantiate with DI but without rendering the routerLink template.
    return TestBed.runInInjectionContext(() => new Shell());
  }

  beforeEach(() => {
    router.navigateByUrl.mockReset();
    auth.logout.mockClear();
  });

  function key(init: KeyboardEventInit): KeyboardEvent {
    return new KeyboardEvent('keydown', init);
  }

  it('opens the command palette on Ctrl/Cmd+K', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'k', ctrlKey: true }));
    expect(shell['paletteOpen']()).toBe(true);
  });

  it('navigates to a new lead on "n"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'n' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/leads/new');
  });

  it('navigates to the lead list on "g" then "l"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: 'g' }));
    shell['onKeydown'](key({ key: 'l' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/leads');
  });

  it('opens the shortcut help on "?"', () => {
    const shell = build();
    shell['onKeydown'](key({ key: '?' }));
    expect(shell['helpOpen']()).toBe(true);
  });

  it('ignores single-letter shortcuts while typing in a field', () => {
    const shell = build();
    const input = document.createElement('input'); // tagName === 'INPUT'
    const event = key({ key: 'n' });
    Object.defineProperty(event, 'target', { value: input });
    shell['onKeydown'](event);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
