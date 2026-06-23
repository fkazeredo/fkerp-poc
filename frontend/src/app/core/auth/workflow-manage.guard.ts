import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the workflow administration routes (list + visual editor) only when the user holds the
 * {@code workflow:manage} scope. Everyone else is sent back to the home screen (the backend is the final
 * authority — {@code /api/workflows/**} also requires the scope).
 */
export const workflowManageGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canManageWorkflows() ? true : router.createUrlTree(['/']);
};
