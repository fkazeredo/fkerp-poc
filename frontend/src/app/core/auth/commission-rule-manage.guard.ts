import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Commission Rules management route only when the user may manage commission rules
 * ({@code commission:rule:manage}). Others are sent back to the home screen.
 */
export const commissionRuleManageGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canManageCommissionRules() ? true : router.createUrlTree(['/']);
};
