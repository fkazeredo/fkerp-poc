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

  it('decodes scopes and subject from the access-token JWT', () => {
    service.accessToken.set(
      jwt({ sub: '00000000-0000-0000-0000-000000000002', scope: 'crm:lead:read crm:lead:update' }),
    );

    expect(service.scopes()).toEqual(['crm:lead:read', 'crm:lead:update']);
    expect(service.userId()).toBe('00000000-0000-0000-0000-000000000002');
    expect(service.hasScope('crm:lead:update')).toBe(true);
    expect(service.hasScope('crm:lead:assign')).toBe(false);
    expect(service.canSeeLeads()).toBe(true);
    expect(service.canOperateLead()).toBe(true);
    expect(service.canCreateLead()).toBe(false);
  });

  it('grants Lead read access through any read tier', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'crm:lead:read:all' }));
    expect(service.canSeeLeads()).toBe(true);
    expect(service.canOperateLead()).toBe(false); // consult-only

    service.accessToken.set(jwt({ sub: 'u', scope: 'something:else' }));
    expect(service.canSeeLeads()).toBe(false);
  });

  it('grants Proposal approval only with the approve authority (not update)', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'sales:proposal:approve' }));
    expect(service.canApproveProposal()).toBe(true);

    service.accessToken.set(jwt({ sub: 'u', scope: 'sales:proposal:update' }));
    expect(service.canApproveProposal()).toBe(false); // update is not approve
  });

  it('grants Receivable read through any read tier and create only with the create scope', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'financial:receivable:read:all' }));
    expect(service.canSeeReceivables()).toBe(true);
    expect(service.canCreateReceivable()).toBe(false); // consult-only

    service.accessToken.set(
      jwt({ sub: 'u', scope: 'financial:receivable:read financial:receivable:create' }),
    );
    expect(service.canSeeReceivables()).toBe(true);
    expect(service.canCreateReceivable()).toBe(true);

    service.accessToken.set(jwt({ sub: 'u', scope: 'sales:order:read:all' }));
    expect(service.canSeeReceivables()).toBe(false);
    expect(service.canCreateReceivable()).toBe(false);
  });

  it('grants commission generation only with the commission:create scope', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:create commission:read' }));
    expect(service.canCreateCommission()).toBe(true);

    // Read/rule-manage alone does not grant generation.
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:read commission:rule:manage' }));
    expect(service.canCreateCommission()).toBe(false);
  });

  it('grants reading commissions through any read tier (own or all)', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:read' }));
    expect(service.canSeeCommissions()).toBe(true);

    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:read:all' }));
    expect(service.canSeeCommissions()).toBe(true);

    // Create alone does not grant read access.
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:create' }));
    expect(service.canSeeCommissions()).toBe(false);
  });

  it('grants approving a commission only with the commission:approve scope', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:approve commission:read:all' }));
    expect(service.canApproveCommission()).toBe(true);

    // A read tier alone does not grant approval.
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:read:all' }));
    expect(service.canApproveCommission()).toBe(false);
  });

  it('grants rejecting/cancelling a commission only with their granular scopes', () => {
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:reject commission:cancel' }));
    expect(service.canRejectCommission()).toBe(true);
    expect(service.canCancelCommission()).toBe(true);

    // Approve alone grants neither reject nor cancel (separate authorities).
    service.accessToken.set(jwt({ sub: 'u', scope: 'commission:approve' }));
    expect(service.canRejectCommission()).toBe(false);
    expect(service.canCancelCommission()).toBe(false);
  });

  it('exposes no scopes and a null subject when there is no token', () => {
    expect(service.scopes()).toEqual([]);
    expect(service.userId()).toBeNull();
    expect(service.hasScope('crm:lead:assign')).toBe(false);
  });

  it('degrades gracefully on a malformed token', () => {
    service.accessToken.set('not-a-jwt');

    expect(service.scopes()).toEqual([]);
    expect(service.userId()).toBeNull();
  });
});

/** Builds a syntactically valid (unsigned) JWT carrying the given claims for decode tests. */
function jwt(claims: Record<string, unknown>): string {
  const b64url = (obj: Record<string, unknown>) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(claims)}.sig`;
}
