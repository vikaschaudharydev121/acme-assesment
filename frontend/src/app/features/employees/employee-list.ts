import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';
import { Employee, EmployeeQuery, Page } from '../../core/models/api.models';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog';
import { MoneyPipe } from '../../shared/money.pipe';
import { EmployeeApi } from './employee-api';
import { EmployeeFormDialog, EmployeeFormDialogData } from './employee-form-dialog';
import { SalaryHistoryDialog } from './salary-history-dialog';

const EMPTY_PAGE: Page<Employee> = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

@Component({
  selector: 'app-employee-list',
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    MoneyPipe,
  ],
  templateUrl: './employee-list.html',
  styleUrl: './employee-list.scss',
})
export class EmployeeList {
  private readonly api = inject(EmployeeApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly query = signal<EmployeeQuery>({ page: 0, size: 20, sort: 'lastName,asc' });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly countries = toSignal(this.api.countries$, { initialValue: [] });
  protected readonly departments = signal<string[]>([]);

  /** Re-runs whenever the query changes; switchMap cancels stale in-flight requests. */
  protected readonly page = toSignal(
    toObservable(this.query).pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((query) =>
        this.api.search(query).pipe(
          catchError((err: unknown) => {
            this.error.set(errorMessage(err));
            return of(EMPTY_PAGE);
          }),
        ),
      ),
      tap(() => this.loading.set(false)),
    ),
    { initialValue: EMPTY_PAGE },
  );

  protected readonly hasFilters = computed(() => {
    const q = this.query();
    return !!(q.q || q.country || q.department);
  });

  protected readonly columns = [
    'employeeCode', 'name', 'jobTitle', 'department', 'countryCode', 'annualSalary', 'usd', 'actions',
  ];

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        debounceTime(300),
        map((value) => value.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((q) => this.patchQuery({ q }));
    this.refreshDepartments();
  }

  protected filterCountry(country: string): void {
    this.patchQuery({ country });
  }

  protected filterDepartment(department: string): void {
    this.patchQuery({ department });
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.query.update((q) => ({ ...q, q: '', country: '', department: '', page: 0 }));
  }

  protected onSort(sort: Sort): void {
    const field = sort.active === 'name' ? 'lastName' : sort.active;
    this.patchQuery({ sort: sort.direction ? `${field},${sort.direction}` : 'lastName,asc' });
  }

  protected onPage(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  protected openCreate(): void {
    this.openForm({ employee: null });
  }

  protected openEdit(employee: Employee): void {
    this.openForm({ employee });
  }

  protected openHistory(employee: Employee): void {
    this.dialog.open(SalaryHistoryDialog, { data: employee, width: '720px', maxWidth: '95vw' });
  }

  protected confirmDelete(employee: Employee): void {
    const data: ConfirmDialogData = {
      title: 'Delete employee?',
      message: `${employee.firstName} ${employee.lastName} (${employee.employeeCode}) and their salary history will be permanently removed.`,
      confirmLabel: 'Delete',
    };
    this.dialog
      .open(ConfirmDialog, { data })
      .afterClosed()
      .pipe(
        switchMap((confirmed) => (confirmed ? this.api.delete(employee.id).pipe(map(() => true)) : of(false))),
      )
      .subscribe({
        next: (deleted) => {
          if (deleted) {
            this.snackBar.open(`Deleted ${employee.firstName} ${employee.lastName}`, 'Dismiss', { duration: 4000 });
            this.reload();
          }
        },
        error: (err: unknown) => this.snackBar.open(errorMessage(err), 'Dismiss', { duration: 6000 }),
      });
  }

  private openForm(data: EmployeeFormDialogData): void {
    this.dialog
      .open(EmployeeFormDialog, { data, width: '720px', maxWidth: '95vw', disableClose: true })
      .afterClosed()
      .subscribe((saved: Employee | undefined) => {
        if (saved) {
          const verb = data.employee ? 'Updated' : 'Added';
          this.snackBar.open(`${verb} ${saved.firstName} ${saved.lastName} (${saved.employeeCode})`, 'Dismiss', {
            duration: 4000,
          });
          this.refreshDepartments();
          this.reload();
        }
      });
  }

  private patchQuery(patch: Partial<EmployeeQuery>): void {
    // Any filter or sort change starts from the first page.
    this.query.update((q) => ({ ...q, ...patch, page: 0 }));
  }

  private reload(): void {
    this.query.update((q) => ({ ...q }));
  }

  private refreshDepartments(): void {
    this.api
      .departments()
      .pipe(catchError(() => of([] as string[])))
      .subscribe((list) => this.departments.set(list));
  }
}
