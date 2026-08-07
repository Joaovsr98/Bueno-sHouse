import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/staff-layout/staff-layout').then((m) => m.StaffLayout),
    children: [
      {
        path: 'catalogo',
        loadComponent: () => import('./features/catalog/catalog').then((m) => m.Catalog),
      },
      { path: '', redirectTo: 'catalogo', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
