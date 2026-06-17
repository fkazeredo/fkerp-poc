import { Injectable, inject, signal } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

/**
 * Implemented by any routed component that holds an in-progress edit (a form or an open edit dialog). The
 * route guard ({@link unsavedChangesGuard}) calls {@link hasUnsavedChanges} before allowing navigation away
 * — including navigation triggered by keyboard shortcuts and the command palette.
 */
export interface HasUnsavedChanges {
  /** Whether the component has unsaved changes the user would lose by leaving now. */
  hasUnsavedChanges(): boolean;
}

/**
 * Centralizes the "you have unsaved changes" confirmation so every form/action behaves the same way. Backed
 * by PrimeNG's {@link ConfirmationService} (the `<p-confirmDialog>` lives in the app shell). Used by the
 * route guard and by edit dialogs when the user tries to close them mid-edit.
 */
@Injectable({ providedIn: 'root' })
export class UnsavedChangesService {
  private readonly confirmation = inject(ConfirmationService);

  /**
   * A coarse, app-wide "there is an in-progress edit" flag, kept in sync by the active form/dialog. The app
   * shell reads it to warn on tab close / reload ({@code beforeunload}). The route guard uses the leaving
   * component's own {@link HasUnsavedChanges.hasUnsavedChanges} for a precise, per-page decision.
   */
  readonly dirty = signal(false);

  /** Updates the app-wide unsaved-edits flag. */
  set(value: boolean): void {
    this.dirty.set(value);
  }

  /**
   * Asks the user to confirm discarding unsaved changes. Resolves to {@code true} when the user chooses to
   * discard and leave, {@code false} when they choose to keep editing.
   */
  confirmDiscard(): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmation.confirm({
        header: 'Descartar alterações?',
        message: 'Você tem alterações não salvas que serão perdidas. Deseja sair mesmo assim?',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Descartar',
        rejectLabel: 'Continuar editando',
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text',
        defaultFocus: 'reject',
        accept: () => resolve(true),
        reject: () => resolve(false),
      });
    });
  }
}
