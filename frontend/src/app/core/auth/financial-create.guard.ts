import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Receivable creation route only when the user may create Receivables
 * ({@code financial:receivable:create}). Users without it are sent to the Receivable list.
 */
export const financialCreateGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canCreateReceivable() ? true : router.createUrlTree(['/financeiro/contas-a-receber']);
};
