/** TypeScript mirror of the backend API contract (see docs/02-architecture.md). */

export type Role = 'HR_MANAGER' | 'HR_VIEWER';

export interface CurrentUser {
  username: string;
  role: Role;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string;
  department: string;
  countryCode: string;
  countryName: string;
  currency: string;
  annualSalary: number;
  annualSalaryUsd: number;
  hireDate: string;
  version: number;
  updatedAt: string;
  updatedBy: string;
}

export interface EmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string;
  department: string;
  countryCode: string;
  annualSalary: number;
  hireDate: string;
  version?: number;
  salaryChangeReason?: string;
}

export interface EmployeeQuery {
  q?: string;
  country?: string;
  department?: string;
  page: number;
  size: number;
  sort?: string;
}

export interface SalaryHistoryEntry {
  id: number;
  previousSalary?: number;
  previousCurrency?: string;
  newSalary: number;
  newCurrency: string;
  reason: string;
  changedAt: string;
  changedBy: string;
}

export interface Country {
  code: string;
  name: string;
  currency: string;
  usdRate: number;
}

export interface SalaryStats {
  count: number;
  total: number;
  min: number;
  p25: number;
  median: number;
  p75: number;
  max: number;
  mean: number;
}

export interface OrgSummary {
  headcount: number;
  countries: number;
  departments: number;
  jobTitles: number;
  totalPayrollUsd: number;
  salaryUsd: SalaryStats;
}

export interface CountryInsight {
  countryCode: string;
  countryName: string;
  currency: string;
  usdRate: number;
  salaryLocal: SalaryStats;
  medianUsd: number;
  meanUsd: number;
  totalPayrollUsd: number;
}

export interface GroupInsight {
  name: string;
  salary: SalaryStats;
}

export interface GroupBreakdown {
  countryCode?: string;
  currency: string;
  groups: GroupInsight[];
}

/** RFC 7807 problem body returned by the API for every error. */
export interface ApiProblem {
  status: number;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}
