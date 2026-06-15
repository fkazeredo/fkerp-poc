import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the access token, sends credentials (refresh cookie), and on a 401 tries one silent
 * refresh and replays the request. Auth endpoints are passed through untouched.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthEndpoint = req.url.includes('/api/auth/');
  const token = auth.accessToken();

  const withAuth =
    token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` }, withCredentials: true })
      : req.clone({ withCredentials: true });

  return next(withAuth).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || isAuthEndpoint) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((res) =>
          next(withAuth.clone({ setHeaders: { Authorization: `Bearer ${res.accessToken}` } })),
        ),
        catchError(() => {
          auth.accessToken.set(null);
          return throwError(() => err);
        }),
      );
    }),
  );
};
