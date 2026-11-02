import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { errorMessage } from '../../core/http/api-error';
import { CountryInsight, GroupBreakdown, OrgSummary } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/money.pipe';
import { EmployeeApi } from '../employees/employee-api';
import { BreakdownDimension, InsightsApi } from './insights-api';

/** Loading / error / data, so every panel can render each state explicitly. */
type Loadable<T> = { loading: true } | { loading: false; error: string } | { loading: false; data: T };

function loadable<T>() {
  return (source: import('rxjs').Observable<T>) =>
    source.pipe(
      map((data): Loadable<T> => ({ loading: false, data })),
      startWith<Loadable<T>>({ loading: true }),
      catchError((err: unknown) => of<Loadable<T>>({ loading: false, error: errorMessage(err) })),
    );
}

export function dataOf<T>(state: Loadable<T>): T | null {
  return 'data' in state ? state.data : null;
}

export function errorOf<T>(state: Loadable<T>): string | null {
  return 'error' in state ? state.error : null;
}

@Component({
  selector: 'app-insights-page',
  imports: [
    MatTableModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MoneyPipe,
  ],
  templateUrl: './insights-page.html',
  styleUrl: './insights-page.scss',
})
export class InsightsPage {
  private readonly api = inject(InsightsApi);

  protected readonly dataOf = dataOf;
  protected readonly errorOf = errorOf;

  protected readonly summary = toSignal(this.api.summary().pipe(loadable<OrgSummary>()), { requireSync: true });
  protected readonly countries = toSignal(this.api.byCountry().pipe(loadable<CountryInsight[]>()), {
    requireSync: true,
  });
  protected readonly countryOptions = toSignal(inject(EmployeeApi).countries$, { initialValue: [] });

  protected readonly dimension = signal<BreakdownDimension>('department');
  protected readonly breakdownCountry = signal('');
  protected readonly groupFilter = signal('');

  protected readonly breakdown = toSignal(
    combineLatest([toObservable(this.dimension), toObservable(this.breakdownCountry)]).pipe(
      switchMap(([dimension, country]) => this.api.breakdown(dimension, country).pipe(loadable<GroupBreakdown>())),
    ),
    { initialValue: { loading: true } as Loadable<GroupBreakdown> },
  );

  protected readonly visibleGroups = computed(() => {
    const data = dataOf(this.breakdown());
    const needle = this.groupFilter().trim().toLowerCase();
    return (data?.groups ?? []).filter((g) => g.name.toLowerCase().includes(needle));
  });

  protected readonly countryColumns = ['country', 'headcount', 'median', 'range', 'medianUsd', 'payrollUsd'];
  protected readonly groupColumns = ['name', 'headcount', 'p25', 'median', 'p75', 'min', 'max', 'mean'];
}
