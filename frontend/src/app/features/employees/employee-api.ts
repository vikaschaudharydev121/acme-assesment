import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import {
  Country,
  Employee,
  EmployeeQuery,
  EmployeeRequest,
  Page,
  SalaryHistoryEntry,
} from '../../core/models/api.models';

const BASE = '/api/v1/employees';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);

  /** Countries only change with a deployment, so one request per session is enough. */
  readonly countries$: Observable<Country[]> = this.http
    .get<Country[]>('/api/v1/reference/countries')
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));

  search(query: EmployeeQuery): Observable<Page<Employee>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const key of ['q', 'country', 'department', 'sort'] as const) {
      const value = query[key]?.trim();
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<Page<Employee>>(BASE, { params });
  }

  create(request: EmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(BASE, request);
  }

  update(id: number, request: EmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${BASE}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`);
  }

  salaryHistory(id: number): Observable<SalaryHistoryEntry[]> {
    return this.http.get<SalaryHistoryEntry[]>(`${BASE}/${id}/salary-history`);
  }

  departments(): Observable<string[]> {
    return this.http.get<string[]>('/api/v1/reference/departments');
  }

  jobTitles(): Observable<string[]> {
    return this.http.get<string[]>('/api/v1/reference/job-titles');
  }
}
