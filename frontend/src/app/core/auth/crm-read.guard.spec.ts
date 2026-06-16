import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { crmReadGuard } from './crm-read.guard';
import { AuthService } from './auth.service';

describe('crmReadGuard', () => {
  const auth = { canSeeLeads: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => crmReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeLeads.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read Leads', () => {
    auth.canSeeLeads.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no Lead access', () => {
    auth.canSeeLeads.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
