import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Lead routes only when the user has some read access to Leads (own / pool / all).
 * Users from unrelated departments (no Lead read scope) are sent back to the home screen.
 */
export const crmReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeLeads() ? true : router.createUrlTree(['/']);
};
