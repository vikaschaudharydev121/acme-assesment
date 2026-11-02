import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CountryInsight, GroupBreakdown, OrgSummary } from '../../core/models/api.models';

export type BreakdownDimension = 'department' | 'job-title';

const BASE = '/api/v1/insights';

@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<OrgSummary> {
    return this.http.get<OrgSummary>(`${BASE}/summary`);
  }

  byCountry(): Observable<CountryInsight[]> {
    return this.http.get<CountryInsight[]>(`${BASE}/by-country`);
  }

  breakdown(dimension: BreakdownDimension, country: string): Observable<GroupBreakdown> {
    const params = country ? new HttpParams().set('country', country) : undefined;
    return this.http.get<GroupBreakdown>(`${BASE}/by-${dimension}`, { params });
  }
}
