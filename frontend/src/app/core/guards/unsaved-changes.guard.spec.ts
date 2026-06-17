import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { unsavedChangesGuard } from './unsaved-changes.guard';
import { HasUnsavedChanges, UnsavedChangesService } from '../forms/unsaved-changes.service';

describe('unsavedChangesGuard', () => {
  const confirmDiscard = vi.fn();

  function run(component: Partial<HasUnsavedChanges>) {
    TestBed.configureTestingModule({
      providers: [{ provide: UnsavedChangesService, useValue: { confirmDiscard } }],
    });
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(component as HasUnsavedChanges, route, state, state),
    );
  }

  beforeEach(() => confirmDiscard.mockReset());

  it('allows leaving when the component has no unsaved changes', () => {
    expect(run({ hasUnsavedChanges: () => false })).toBe(true);
    expect(confirmDiscard).not.toHaveBeenCalled();
  });

  it('allows leaving when the component does not implement the interface', () => {
    expect(run({})).toBe(true);
    expect(confirmDiscard).not.toHaveBeenCalled();
  });

  it('asks for confirmation when there are unsaved changes', () => {
    confirmDiscard.mockReturnValue(Promise.resolve(false));
    const result = run({ hasUnsavedChanges: () => true });
    expect(confirmDiscard).toHaveBeenCalled();
    expect(result).toBeInstanceOf(Promise);
  });
});
