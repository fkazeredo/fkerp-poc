import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Proposal (Sales & Proposals) routes only when the user has some read access to Proposals
 * (own / pool / all). Users without a Proposal read scope are sent back to the home screen.
 */
export const proposalReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeProposals() ? true : router.createUrlTree(['/']);
};
