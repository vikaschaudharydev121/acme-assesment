import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · ACME Salary',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      {
        path: 'employees',
        title: 'Employees · ACME Salary',
        loadComponent: () =>
          import('./features/employees/employee-list').then((m) => m.EmployeeList),
      },
      {
        path: 'insights',
        title: 'Pay insights · ACME Salary',
        loadComponent: () =>
          import('./features/insights/insights-page').then((m) => m.InsightsPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'employees' },
    ],
  },
  { path: '**', redirectTo: '' },
];
