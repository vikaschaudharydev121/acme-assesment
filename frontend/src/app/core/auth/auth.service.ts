import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';
import { CurrentUser } from '../models/api.models';

/**
 * Holds the signed-in user. The session token itself is an HttpOnly cookie the app can never read;
 * the UI only knows *who* is signed in, as reported by the server.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<CurrentUser | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly canManageEmployees = computed(() => this.currentUser()?.role === 'HR_MANAGER');

  /** Restores an existing session on page load. Also primes the XSRF-TOKEN cookie for the first POST. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.http.get<CurrentUser>('/api/v1/auth/me').pipe(
        tap((user) => this.currentUser.set(user)),
        map(() => undefined),
        catchError(() => {
          this.currentUser.set(null);
          return of(undefined);
        }),
      ),
    );
  }

  login(username: string, password: string): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>('/api/v1/auth/login', { username, password })
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', null).pipe(
      catchError(() => of(undefined)),
      tap(() => this.currentUser.set(null)),
    );
  }

  /** Called when the server reports the session is no longer valid. */
  sessionExpired(): void {
    this.currentUser.set(null);
  }
}
