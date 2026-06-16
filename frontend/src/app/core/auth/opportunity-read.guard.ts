import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Opportunity routes only when the user has some read access to Opportunities
 * (own / pool / all). Users without an Opportunity read scope are sent back to the home screen.
 */
export const opportunityReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeOpportunities() ? true : router.createUrlTree(['/']);
};
