import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { safeReturnUrl } from '../../core/auth/auth.guards';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="wrapper">
      <mat-card class="card" appearance="outlined">
        @if (submitting()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <mat-card-header>
          <mat-icon mat-card-avatar class="logo">payments</mat-icon>
          <mat-card-title>ACME Salary</mat-card-title>
          <mat-card-subtitle>Sign in to manage employee pay</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (expired()) {
            <p class="notice" role="status">Your session has ended. Please sign in again.</p>
          }
          @if (error(); as message) {
            <div class="error-banner" role="alert"><mat-icon>error</mat-icon>{{ message }}</div>
          }
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <mat-form-field class="full-width">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" autocomplete="username" required />
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Password</mat-label>
              <input
                matInput
                [type]="showPassword() ? 'text' : 'password'"
                formControlName="password"
                autocomplete="current-password"
                required
              />
              <button
                mat-icon-button
                matSuffix
                type="button"
                (click)="showPassword.set(!showPassword())"
                [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
              >
                <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>
            <button mat-flat-button class="full-width" type="submit" [disabled]="submitting()">
              Sign in
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .wrapper {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 16px;
      box-sizing: border-box;
      background: var(--mat-sys-surface-container-low);
    }
    .card {
      width: 100%;
      max-width: 400px;
      overflow: hidden;
    }
    .logo {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: var(--mat-sys-primary);
    }
    mat-card-content {
      padding-top: 16px;
    }
    .notice {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Bound from query params via withComponentInputBinding. */
  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.form.controls.password.reset();
        this.submitting.set(false);
      },
    });
  }
}
