import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { financialReadGuard } from './financial-read.guard';
import { AuthService } from './auth.service';

describe('financialReadGuard', () => {
  const auth = { canSeeReceivables: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => financialReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeReceivables.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read Receivables', () => {
    auth.canSeeReceivables.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no Receivable access', () => {
    auth.canSeeReceivables.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
