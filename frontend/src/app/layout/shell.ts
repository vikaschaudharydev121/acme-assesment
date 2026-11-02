import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  template: `
    <mat-toolbar class="toolbar">
      <a class="brand" routerLink="/employees">
        <mat-icon>payments</mat-icon>
        <span>ACME Salary</span>
      </a>
      <nav class="nav" aria-label="Main">
        <a mat-button routerLink="/employees" routerLinkActive="active" aria-label="Employees">
          <mat-icon>group</mat-icon><span class="nav-label">Employees</span>
        </a>
        <a mat-button routerLink="/insights" routerLinkActive="active" aria-label="Insights">
          <mat-icon>insights</mat-icon><span class="nav-label">Insights</span>
        </a>
      </nav>
      <span class="spacer"></span>
      @if (auth.user(); as user) {
        <span class="user" data-testid="current-user">
          <mat-icon>account_circle</mat-icon>
          <span class="user-name">{{ user.username }}</span>
          <span class="role">{{ user.role === 'HR_MANAGER' ? 'Manager' : 'Viewer' }}</span>
        </span>
      }
      <button mat-icon-button (click)="logout()" matTooltip="Sign out" aria-label="Sign out">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
      gap: 8px;
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      color: inherit;
      text-decoration: none;
      font: var(--mat-sys-title-medium);
      margin-right: 16px;
    }
    .nav a.active {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .spacer {
      flex: 1;
    }
    .user {
      display: flex;
      align-items: center;
      gap: 6px;
      font: var(--mat-sys-body-medium);
    }
    .role {
      font: var(--mat-sys-label-small);
      padding: 2px 8px;
      border-radius: 999px;
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    @media (max-width: 720px) {
      .toolbar {
        padding: 0 8px;
        gap: 4px;
      }
      .brand {
        margin-right: 4px;
      }
      .brand span,
      .user-name,
      .user mat-icon,
      .nav-label {
        display: none;
      }
      .nav a {
        min-width: 48px;
        padding: 0 12px;
      }
      .nav a mat-icon {
        margin: 0;
      }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
  }
}
