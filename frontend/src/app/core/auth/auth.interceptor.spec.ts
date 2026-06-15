import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('attaches the bearer token and credentials to API calls', () => {
    auth.accessToken.set('abc');

    http.get('/api/crm/origins').subscribe();

    const req = httpMock.expectOne('/api/crm/origins');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
  });

  it('never adds a bearer to the auth endpoints themselves', () => {
    auth.accessToken.set('abc');

    http.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('refreshes once on 401 and replays the original request with the new token', () => {
    auth.accessToken.set('old');
    let result: unknown;

    http.get('/api/crm/origins').subscribe((r) => (result = r));

    const first = httpMock.expectOne(
      (r) => r.url === '/api/crm/origins' && r.headers.get('Authorization') === 'Bearer old',
    );
    first.flush('unauthorized', { status: 401, statusText: 'Unauthorized' });

    const refresh = httpMock.expectOne('/api/auth/refresh');
    expect(refresh.request.method).toBe('POST');
    refresh.flush({ accessToken: 'new', tokenType: 'Bearer', expiresInSeconds: 900 });

    const replay = httpMock.expectOne(
      (r) => r.url === '/api/crm/origins' && r.headers.get('Authorization') === 'Bearer new',
    );
    replay.flush([{ id: '1' }]);

    expect(result).toEqual([{ id: '1' }]);
    expect(auth.accessToken()).toBe('new');
  });

  it('clears the token and propagates the error when the refresh also fails', () => {
    auth.accessToken.set('old');
    let errorStatus = 0;

    http.get('/api/crm/origins').subscribe({ error: (e) => (errorStatus = e.status) });

    httpMock
      .expectOne('/api/crm/origins')
      .flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne('/api/auth/refresh')
      .flush('no', { status: 401, statusText: 'Unauthorized' });

    expect(errorStatus).toBe(401);
    expect(auth.accessToken()).toBeNull();
  });
});
