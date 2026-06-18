import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { proposalReadGuard } from './proposal-read.guard';
import { AuthService } from './auth.service';

describe('proposalReadGuard', () => {
  const auth = { canSeeProposals: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => proposalReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeProposals.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read Proposals', () => {
    auth.canSeeProposals.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no Proposal access', () => {
    auth.canSeeProposals.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
