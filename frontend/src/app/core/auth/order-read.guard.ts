import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Commercial Order (Sales & Proposals) routes only when the user has some read access to Orders
 * (own / pool / all). Users without an Order read scope are sent back to the home screen.
 */
export const orderReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeOrders() ? true : router.createUrlTree(['/']);
};
