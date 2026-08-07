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
        path: 'mesas',
        loadComponent: () => import('./features/tables/tables').then((m) => m.Tables),
      },
      {
        path: 'atendimento/:serviceId',
        loadComponent: () => import('./features/service/service').then((m) => m.Service),
      },
      {
        path: 'atendimento/:serviceId/comanda/:commandId',
        loadComponent: () => import('./features/order/order').then((m) => m.Order),
      },
      {
        path: 'cozinha',
        loadComponent: () => import('./features/kitchen/kitchen').then((m) => m.Kitchen),
      },
      {
        path: 'caixa',
        loadComponent: () =>
          import('./features/cash-register/cash-register').then((m) => m.CashRegister),
      },
      {
        path: 'catalogo',
        loadComponent: () => import('./features/catalog/catalog').then((m) => m.Catalog),
      },
      {
        path: 'motoboy',
        loadComponent: () => import('./features/courier/courier').then((m) => m.Courier),
      },
      { path: '', redirectTo: 'mesas', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
