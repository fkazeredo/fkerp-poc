import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { opportunityReadGuard } from './opportunity-read.guard';
import { AuthService } from './auth.service';

describe('opportunityReadGuard', () => {
  const auth = { canSeeOpportunities: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => opportunityReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeOpportunities.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read Opportunities', () => {
    auth.canSeeOpportunities.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no Opportunity access', () => {
    auth.canSeeOpportunities.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
