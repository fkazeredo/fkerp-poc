import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Financial (Receivable) routes only when the user has some read access to Receivables (own / all).
 * Users without a Receivable read scope are sent back to the home screen.
 */
export const financialReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeReceivables() ? true : router.createUrlTree(['/']);
};
