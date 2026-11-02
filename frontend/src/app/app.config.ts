import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { sessionExpiryInterceptor } from './core/http/session-expiry.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      // Echo the server's XSRF-TOKEN cookie in X-XSRF-TOKEN on every mutating same-origin request.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([sessionExpiryInterceptor]),
    ),
    provideAppInitializer(() => {
      // Self-hosted Material Symbols font (no third-party font CDN, keeps the CSP strict).
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
      return inject(AuthService).restoreSession();
    }),
  ],
};
