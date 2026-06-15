import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
  });

  function run() {
    return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
  }

  it('allows the route when authenticated', () => {
    auth.accessToken.set('tok');
    expect(run()).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    const result = run();
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toContain('/login');
  });
});
