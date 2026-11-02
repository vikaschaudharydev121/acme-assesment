import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** A 401 on any data call means the cookie expired: drop local state and return to the login page. */
export const sessionExpiryInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      const isAuthCall = request.url.startsWith('/api/v1/auth/');
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthCall) {
        auth.sessionExpired();
        void router.navigate(['/login'], {
          queryParams: { returnUrl: router.url, expired: true },
        });
      }
      return throwError(() => error);
    }),
  );
};
