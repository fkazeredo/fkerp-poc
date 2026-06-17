import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { HasUnsavedChanges, UnsavedChangesService } from '../forms/unsaved-changes.service';

/**
 * Route guard that protects against losing in-progress edits. If the leaving component reports unsaved
 * changes, the user is asked to confirm before navigation proceeds. Because every router navigation goes
 * through this guard, it covers links, the command palette and the keyboard shortcuts alike.
 */
export const unsavedChangesGuard: CanDeactivateFn<Partial<HasUnsavedChanges>> = (component) => {
  if (!component?.hasUnsavedChanges?.()) {
    return true;
  }
  return inject(UnsavedChangesService).confirmDiscard();
};
