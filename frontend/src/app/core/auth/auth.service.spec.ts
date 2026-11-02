import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('restores an existing session from /me', async () => {
    const restored = auth.restoreSession();
    http.expectOne('/api/v1/auth/me').flush({ username: 'hr.manager', role: 'HR_MANAGER' });
    await restored;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.canManageEmployees()).toBe(true);
  });

  it('treats a 401 from /me as signed out instead of failing app start-up', async () => {
    const restored = auth.restoreSession();
    http.expectOne('/api/v1/auth/me').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await restored;

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('viewers are signed in but cannot manage employees', () => {
    auth.login('hr.viewer', 'secret').subscribe();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'hr.viewer', password: 'secret' });
    req.flush({ username: 'hr.viewer', role: 'HR_VIEWER' });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.canManageEmployees()).toBe(false);
  });

  it('clears the user on logout even if the server call fails', () => {
    auth.login('u', 'p').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ username: 'u', role: 'HR_MANAGER' });

    auth.logout().subscribe();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.user()).toBeNull();
  });
});
