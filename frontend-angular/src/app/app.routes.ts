import { Route, Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard, SCREEN_ROLES, STAFF_ROLES } from './core/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'registrar',
    loadComponent: () => import('./features/register/register').then((m) => m.Register),
  },
  {
    path: 'app',
    // Sem authGuard no pai: cardapio publico (RF-001/RF-002, sem login).
    // Carrinho/pedidos exigem login (RF-005/RF-006/RF-007/RF-008), guardados
    // individualmente abaixo.
    loadComponent: () =>
      import('./features/customer/customer-layout').then((m) => m.CustomerLayout),
    children: [
      {
        path: 'cardapio',
        loadComponent: () => import('./features/customer/customer-menu').then((m) => m.CustomerMenu),
      },
      {
        path: 'carrinho',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/customer/customer-checkout').then((m) => m.CustomerCheckout),
      },
      {
        path: 'pedidos',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/customer/customer-orders').then((m) => m.CustomerOrders),
      },
      {
        path: 'pedidos/:orderId',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/customer/customer-order-track').then((m) => m.CustomerOrderTrack),
      },
      { path: '', redirectTo: 'cardapio', pathMatch: 'full' },
    ],
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard],
    data: { roles: STAFF_ROLES },
    loadComponent: () =>
      import('./features/staff-layout/staff-layout').then((m) => m.StaffLayout),
    children: [
      screen('pedidos', () => import('./features/orders/orders').then((m) => m.Orders)),
      screen('mesas', () => import('./features/tables/tables').then((m) => m.Tables)),
      screen('atendimento/:serviceId', () => import('./features/service/service').then((m) => m.Service), 'mesas'),
      screen(
        'atendimento/:serviceId/comanda/:commandId',
        () => import('./features/order/order').then((m) => m.Order),
        'mesas',
      ),
      screen('cozinha', () => import('./features/kitchen/kitchen').then((m) => m.Kitchen)),
      screen('caixa', () => import('./features/cash-register/cash-register').then((m) => m.CashRegister)),
      screen('catalogo', () => import('./features/catalog/catalog').then((m) => m.Catalog)),
      screen('motoboy', () => import('./features/courier/courier').then((m) => m.Courier)),
      screen('estoque', () => import('./features/inventory/inventory').then((m) => m.Inventory)),
      screen('fornecedores', () => import('./features/suppliers/suppliers').then((m) => m.Suppliers)),
      screen('fichas-tecnicas', () => import('./features/recipes/recipes').then((m) => m.Recipes)),
      screen('dashboard', () => import('./features/dashboard/dashboard').then((m) => m.Dashboard)),
      screen('usuarios', () => import('./features/users/users').then((m) => m.Users)),
      { path: '', redirectTo: 'pedidos', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];

/** Rota da equipe protegida pelo RoleGuard com os perfis de SCREEN_ROLES[screenKey]. */
function screen(path: string, loadComponent: Route['loadComponent'], screenKey = path): Route {
  return { path, loadComponent, canActivate: [roleGuard], data: { roles: SCREEN_ROLES[screenKey] } };
}
