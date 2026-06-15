import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts unauthenticated', () => {
    expect(service.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('stores the access token and sends credentials on login', () => {
    service.login('comercial', 'secret').subscribe();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'comercial', password: 'secret' });
    expect(req.request.withCredentials).toBe(true);
    req.flush({ accessToken: 'tok-1', tokenType: 'Bearer', expiresInSeconds: 900 });

    expect(service.accessToken()).toBe('tok-1');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('updates the token on refresh (cookie sent with credentials)', () => {
    service.refresh().subscribe();

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush({ accessToken: 'tok-2', tokenType: 'Bearer', expiresInSeconds: 900 });

    expect(service.accessToken()).toBe('tok-2');
  });

  it('clears the token immediately on logout, then calls the endpoint', () => {
    service.accessToken.set('tok');

    service.logout().subscribe();
    expect(service.accessToken()).toBeNull();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
