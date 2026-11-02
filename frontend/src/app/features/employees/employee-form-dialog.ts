import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, of } from 'rxjs';
import { errorMessage, fieldErrors, toProblem } from '../../core/http/api-error';
import { Employee, EmployeeRequest } from '../../core/models/api.models';
import { formatMoney } from '../../shared/money.pipe';
import { EmployeeApi } from './employee-api';
import { isCompensationChange, percentChange, todayIso } from './employee-form';

export interface EmployeeFormDialogData {
  employee: Employee | null;
}

/** Mirrors the server-side rules so users get instant feedback; the server remains the authority. */
const PERSON_NAME = /^[\p{L}][\p{L}\p{M} .'-]*$/u;
const LABEL = /^[\p{L}\p{N}][\p{L}\p{N} &/.,'()+-]*$/u;

function notInFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > todayIso() ? { future: true } : null;
}

@Component({
  selector: 'app-employee-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './employee-form-dialog.html',
  styles: `
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      column-gap: 16px;
      padding-top: 8px;
    }
    .span-2 {
      grid-column: span 2;
    }
    @media (max-width: 600px) {
      .grid {
        grid-template-columns: 1fr;
      }
      .span-2 {
        grid-column: span 1;
      }
    }
    .change {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
      margin: -8px 0 12px;
    }
  `,
})
export class EmployeeFormDialog {
  private readonly api = inject(EmployeeApi);
  private readonly dialogRef = inject(MatDialogRef<EmployeeFormDialog, Employee>);
  protected readonly original = inject<EmployeeFormDialogData>(MAT_DIALOG_DATA).employee;
  protected readonly isEdit = this.original !== null;
  protected readonly today = todayIso();

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    firstName: [this.original?.firstName ?? '', [Validators.required, Validators.maxLength(100), Validators.pattern(PERSON_NAME)]],
    lastName: [this.original?.lastName ?? '', [Validators.required, Validators.maxLength(100), Validators.pattern(PERSON_NAME)]],
    email: [this.original?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(254)]],
    jobTitle: [this.original?.jobTitle ?? '', [Validators.required, Validators.maxLength(100), Validators.pattern(LABEL)]],
    department: [this.original?.department ?? '', [Validators.required, Validators.maxLength(100), Validators.pattern(LABEL)]],
    countryCode: [this.original?.countryCode ?? '', [Validators.required]],
    annualSalary: [
      this.original?.annualSalary ?? (null as number | null),
      [Validators.required, Validators.min(1), Validators.max(1_000_000_000), Validators.pattern(/^\d+$/)],
    ],
    hireDate: [this.original?.hireDate ?? '', [Validators.required, notInFuture]],
    salaryChangeReason: ['', [Validators.maxLength(500)]],
  });

  protected readonly countries = toSignal(this.api.countries$, { initialValue: [] });
  protected readonly departments = toSignal(this.api.departments().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly jobTitles = toSignal(this.api.jobTitles().pipe(catchError(() => of([]))), { initialValue: [] });

  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly currency = computed(
    () => this.countries().find((c) => c.code === this.values().countryCode)?.currency ?? '',
  );

  protected readonly compensationChanged = computed(() => {
    const v = this.values();
    return isCompensationChange(this.original, Number(v.annualSalary ?? 0), v.countryCode ?? '');
  });

  protected readonly changeSummary = computed(() => {
    const v = this.values();
    if (!this.original || !this.compensationChanged()) {
      return null;
    }
    const from = formatMoney(this.original.annualSalary, this.original.currency);
    const to = formatMoney(Number(v.annualSalary ?? 0), this.currency() || this.original.currency);
    const pct = this.currency() === this.original.currency ? percentChange(this.original.annualSalary, Number(v.annualSalary)) : null;
    return `${from} → ${to}${pct === null ? '' : ` (${pct >= 0 ? '+' : ''}${(pct * 100).toFixed(1)}%)`}`;
  });

  protected readonly filteredDepartments = computed(() => filter(this.departments(), this.values().department));
  protected readonly filteredJobTitles = computed(() => filter(this.jobTitles(), this.values().jobTitle));

  protected save(): void {
    const reason = this.form.controls.salaryChangeReason;
    reason.setValidators(
      this.compensationChanged() ? [Validators.required, Validators.maxLength(500)] : [Validators.maxLength(500)],
    );
    reason.updateValueAndValidity({ emitEvent: false });

    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: EmployeeRequest = {
      firstName: v.firstName!.trim(),
      lastName: v.lastName!.trim(),
      email: v.email!.trim(),
      jobTitle: v.jobTitle!.trim(),
      department: v.department!.trim(),
      countryCode: v.countryCode!,
      annualSalary: Number(v.annualSalary),
      hireDate: v.hireDate!,
    };
    if (this.original) {
      request.version = this.original.version;
      if (this.compensationChanged()) {
        request.salaryChangeReason = v.salaryChangeReason!.trim();
      }
    }

    this.saving.set(true);
    this.error.set(null);
    const call = this.original ? this.api.update(this.original.id, request) : this.api.create(request);
    call.subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (err: unknown) => {
        this.saving.set(false);
        this.applyServerErrors(err);
      },
    });
  }

  protected cancel(): void {
    this.dialogRef.close();
  }

  protected errorFor(name: string): string | null {
    const errors = this.form.get(name)?.errors;
    if (!errors) return null;
    if (errors['server']) return errors['server'];
    if (errors['required']) return 'Required';
    if (errors['email']) return 'Enter a valid email address';
    if (errors['maxlength']) return `At most ${errors['maxlength'].requiredLength} characters`;
    if (errors['min']) return 'Must be greater than zero';
    if (errors['max']) return 'Must not exceed 1,000,000,000';
    if (errors['future']) return 'Cannot be in the future';
    if (errors['pattern']) return name === 'annualSalary' ? 'Whole numbers only' : 'Contains unsupported characters';
    return 'Invalid value';
  }

  private applyServerErrors(err: unknown): void {
    const problem = toProblem(err);
    const perField = fieldErrors(err);
    let mapped = false;
    for (const [field, message] of Object.entries(perField)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        mapped = true;
      }
    }
    if (problem?.code === 'EMAIL_ALREADY_EXISTS') {
      this.form.controls.email.setErrors({ server: problem.detail });
      this.form.controls.email.markAsTouched();
      mapped = true;
    }
    if (!mapped) {
      this.error.set(errorMessage(err));
    }
  }
}

function filter(options: string[], typed: string | null | undefined): string[] {
  const needle = (typed ?? '').toLowerCase();
  return options.filter((o) => o.toLowerCase().includes(needle)).slice(0, 20);
}
