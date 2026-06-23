import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { financialCreateGuard } from './financial-create.guard';
import { AuthService } from './auth.service';

describe('financialCreateGuard', () => {
  const auth = { canCreateReceivable: vi.fn() };
  const router = { createUrlTree: vi.fn(() => ({}) as UrlTree) };

  function run() {
    return TestBed.runInInjectionContext(() => financialCreateGuard({} as never, {} as never));
  }

  beforeEach(() => {
    auth.canCreateReceivable.mockReset();
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows the route when the user may create a Receivable', () => {
    auth.canCreateReceivable.mockReturnValue(true);
    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to the receivable list when the user cannot create one', () => {
    auth.canCreateReceivable.mockReturnValue(false);
    run();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/financeiro/contas-a-receber']);
  });
});
