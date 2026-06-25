import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { commissionReadGuard } from './commission-read.guard';
import { AuthService } from './auth.service';

describe('commissionReadGuard', () => {
  const auth = { canSeeCommissions: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => commissionReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeCommissions.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read commissions', () => {
    auth.canSeeCommissions.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no commission access', () => {
    auth.canSeeCommissions.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
