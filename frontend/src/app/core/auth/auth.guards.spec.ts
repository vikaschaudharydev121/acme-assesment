import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, safeReturnUrl } from './auth.guards';
import { AuthService } from './auth.service';

describe('safeReturnUrl', () => {
  it.each([
    ['/insights', '/insights'],
    ['/employees?country=IN', '/employees?country=IN'],
  ])('keeps in-app path %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });

  it.each([
    [undefined],
    [''],
    ['https://evil.example'],
    ['//evil.example'],
    ['/\\evil.example'],
    ['javascript:alert(1)'],
    ['/login?returnUrl=/x'],
  ])('rejects unsafe or pointless target %s', (input) => {
    expect(safeReturnUrl(input)).toBe('/employees');
  });
});

describe('authGuard', () => {
  const state = { url: '/insights' } as RouterStateSnapshot;

  function setup(signedIn: boolean) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    if (signedIn) {
      TestBed.inject(AuthService).login('u', 'p').subscribe();
      TestBed.inject(HttpTestingController)
        .expectOne('/api/v1/auth/login')
        .flush({ username: 'u', role: 'HR_VIEWER' });
    }
  }

  it('lets signed-in users through', () => {
    setup(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state));
    expect(result).toBe(true);
  });

  it('sends anonymous users to login and remembers where they were going', () => {
    setup(false);
    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state));
    const router = TestBed.inject(Router);
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Finsights');
  });
});
