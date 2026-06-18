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
});
