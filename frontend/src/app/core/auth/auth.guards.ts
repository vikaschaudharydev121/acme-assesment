import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Only signed-in users; others go to the login page and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The login page is pointless once signed in. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/employees']) : true;

/**
 * Accepts only in-app paths as a post-login redirect, preventing open redirects such as
 * `?returnUrl=//evil.example` or `?returnUrl=https://evil.example`.
 */
export function safeReturnUrl(candidate: string | null | undefined, fallback = '/employees'): string {
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//') || candidate.includes('\\')) {
    return fallback;
  }
  return candidate.startsWith('/login') ? fallback : candidate;
}
