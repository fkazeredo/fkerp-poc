import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { AuthService } from '../../../core/auth/auth.service';

// Polyfills so PrimeNG components can render under jsdom.
(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('Login', () => {
  const auth = { login: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  beforeEach(() => {
    auth.login.mockReset();
    router.navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        providePrimeNG(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('renders the login screen', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('FKERP');
    expect(text).toContain('Entrar');
  });

  it('does not call the API while the form is invalid', () => {
    const fixture = TestBed.createComponent(Login);
    const comp = fixture.componentInstance;
    comp['submit']();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('navigates home on a successful login', () => {
    auth.login.mockReturnValue(of({ accessToken: 't', tokenType: 'Bearer', expiresInSeconds: 900 }));
    const fixture = TestBed.createComponent(Login);
    const comp = fixture.componentInstance;
    comp['form'].setValue({ username: 'comercial', password: 'comercial123' });

    comp['submit']();

    expect(auth.login).toHaveBeenCalledWith('comercial', 'comercial123');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('shows an error message on the screen when the credentials are rejected (401)', () => {
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = TestBed.createComponent(Login);
    const comp = fixture.componentInstance;
    comp['form'].setValue({ username: 'x', password: 'y' });

    comp['submit']();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('inválidos');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
