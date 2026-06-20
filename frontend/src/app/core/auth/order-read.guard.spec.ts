import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { orderReadGuard } from './order-read.guard';
import { AuthService } from './auth.service';

describe('orderReadGuard', () => {
  const auth = { canSeeOrders: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => orderReadGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canSeeOrders.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user can read Orders', () => {
    auth.canSeeOrders.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to home when the user has no Order access', () => {
    auth.canSeeOrders.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });
});
