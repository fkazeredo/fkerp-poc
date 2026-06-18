import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { UnsavedChangesService } from './unsaved-changes.service';

describe('UnsavedChangesService', () => {
  let captured: { accept?: () => void; reject?: () => void } = {};
  const confirmation = {
    confirm: vi.fn((opts: { accept?: () => void; reject?: () => void }) => {
      captured = opts;
    }),
  };
  let service: UnsavedChangesService;

  beforeEach(() => {
    captured = {};
    confirmation.confirm.mockClear();
    TestBed.configureTestingModule({
      providers: [{ provide: ConfirmationService, useValue: confirmation }],
    });
    service = TestBed.inject(UnsavedChangesService);
  });

  it('tracks the app-wide dirty flag', () => {
    expect(service.dirty()).toBe(false);
    service.set(true);
    expect(service.dirty()).toBe(true);
    service.set(false);
    expect(service.dirty()).toBe(false);
  });

  it('resolves true when the user confirms discarding', async () => {
    const result = service.confirmDiscard();
    expect(confirmation.confirm).toHaveBeenCalled();
    captured.accept!();
    await expect(result).resolves.toBe(true);
  });

  it('resolves false when the user keeps editing', async () => {
    const result = service.confirmDiscard();
    captured.reject!();
    await expect(result).resolves.toBe(false);
  });

  it('does not stack a second confirmation while one is already on screen', async () => {
    const first = service.confirmDiscard();
    const second = service.confirmDiscard(); // e.g. Esc pressed twice

    // Only one dialog is shown; the re-entrant call resolves false (keep editing) without stacking.
    expect(confirmation.confirm).toHaveBeenCalledTimes(1);
    await expect(second).resolves.toBe(false);

    captured.accept!();
    await expect(first).resolves.toBe(true);
  });

  it('shows a fresh dialog again after the previous one is resolved', async () => {
    const first = service.confirmDiscard();
    captured.reject!();
    await expect(first).resolves.toBe(false);

    const second = service.confirmDiscard();
    expect(confirmation.confirm).toHaveBeenCalledTimes(2);
    captured.accept!();
    await expect(second).resolves.toBe(true);
  });
});
