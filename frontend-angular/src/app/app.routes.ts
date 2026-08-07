import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/customer/customer-layout').then((m) => m.CustomerLayout),
    children: [
      {
        path: 'cardapio',
        loadComponent: () => import('./features/customer/customer-menu').then((m) => m.CustomerMenu),
      },
      {
        path: 'carrinho',
        loadComponent: () =>
          import('./features/customer/customer-checkout').then((m) => m.CustomerCheckout),
      },
      {
        path: 'pedidos',
        loadComponent: () =>
          import('./features/customer/customer-orders').then((m) => m.CustomerOrders),
      },
      {
        path: 'pedidos/:orderId',
        loadComponent: () =>
          import('./features/customer/customer-order-track').then((m) => m.CustomerOrderTrack),
      },
      { path: '', redirectTo: 'cardapio', pathMatch: 'full' },
    ],
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
