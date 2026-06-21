import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Allows the Booking Operations (reservation) routes only when the user has some read access to Booking
 * Requests (own / pool / all). Users without a Booking read scope are sent back to the home screen.
 */
export const bookingReadGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.canSeeBookings() ? true : router.createUrlTree(['/']);
};
