import { DatePipe, PercentPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { catchError, map, of, startWith } from 'rxjs';
import { errorMessage } from '../../core/http/api-error';
import { Employee, SalaryHistoryEntry } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/money.pipe';
import { EmployeeApi } from './employee-api';
import { percentChange } from './employee-form';

type HistoryState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; entries: SalaryHistoryEntry[] };

@Component({
  selector: 'app-salary-history-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule, MoneyPipe, DatePipe, PercentPipe],
  template: `
    <h2 mat-dialog-title>Salary history · {{ employee.firstName }} {{ employee.lastName }}</h2>
    <mat-dialog-content>
      <p class="muted">{{ employee.employeeCode }} · {{ employee.jobTitle }} · {{ employee.countryName }}</p>
      @switch (state().status) {
        @case ('loading') {
          <mat-progress-bar mode="indeterminate" />
        }
        @case ('error') {
          <div class="error-banner" role="alert"><mat-icon>error</mat-icon>{{ errorText() }}</div>
        }
        @case ('loaded') {
          <ol class="timeline">
            @for (entry of entries(); track entry.id) {
              <li>
                <div class="amount">
                  @if (entry.previousSalary !== undefined && entry.previousSalary !== null) {
                    <span class="muted">{{ entry.previousSalary | money: entry.previousCurrency! }} →</span>
                  }
                  <strong>{{ entry.newSalary | money: entry.newCurrency }}</strong>
                  @if (change(entry); as pct) {
                    <span class="delta" [class.down]="pct < 0">{{ pct > 0 ? '+' : '' }}{{ pct | percent: '1.0-1' }}</span>
                  }
                </div>
                <div>{{ entry.reason }}</div>
                <div class="muted meta">{{ entry.changedAt | date: 'medium' }} · {{ entry.changedBy }}</div>
              </li>
            }
          </ol>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .timeline {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .timeline li {
      padding: 12px 0 12px 16px;
      border-left: 2px solid var(--mat-sys-outline-variant);
      margin-left: 4px;
    }
    .amount {
      display: flex;
      gap: 8px;
      align-items: baseline;
      flex-wrap: wrap;
    }
    .delta {
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-primary);
    }
    .delta.down {
      color: var(--mat-sys-error);
    }
    .meta {
      font: var(--mat-sys-body-small);
    }
  `,
})
export class SalaryHistoryDialog {
  protected readonly employee = inject<Employee>(MAT_DIALOG_DATA);

  protected readonly state = toSignal(
    inject(EmployeeApi)
      .salaryHistory(this.employee.id)
      .pipe(
        map((entries): HistoryState => ({ status: 'loaded', entries })),
        startWith<HistoryState>({ status: 'loading' }),
        catchError((err: unknown) => of<HistoryState>({ status: 'error', message: errorMessage(err) })),
      ),
    { requireSync: true },
  );

  protected entries(): SalaryHistoryEntry[] {
    const s = this.state();
    return s.status === 'loaded' ? s.entries : [];
  }

  protected errorText(): string {
    const s = this.state();
    return s.status === 'error' ? s.message : '';
  }

  /** Only meaningful when the currency did not change. */
  protected change(entry: SalaryHistoryEntry): number | null {
    if (entry.previousCurrency !== entry.newCurrency) {
      return null;
    }
    return percentChange(entry.previousSalary, entry.newSalary);
  }
}
