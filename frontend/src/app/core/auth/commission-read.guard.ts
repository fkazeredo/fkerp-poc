import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Commission list route only when the user has some read access to commissions (own / all). Users
 * without a commission read scope are sent back to the home screen.
 */
export const commissionReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeCommissions() ? true : router.createUrlTree(['/']);
};
