import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { defaultRouteForRole } from './routing';

/** Perfis da equipe (todos menos CLIENTE). */
export const STAFF_ROLES = ['ADMINISTRADOR', 'GERENTE', 'CAIXA', 'GARCOM', 'COZINHA', 'MOTOBOY'];

/** Quem acessa cada tela da equipe - espelha os @PreAuthorize do backend (RF-040/RF-043). */
export const SCREEN_ROLES: Record<string, string[]> = {
  pedidos: ['ADMINISTRADOR', 'GERENTE', 'CAIXA', 'GARCOM', 'COZINHA'],
  mesas: ['ADMINISTRADOR', 'GERENTE', 'GARCOM', 'CAIXA'],
  cozinha: ['ADMINISTRADOR', 'GERENTE', 'COZINHA'],
  caixa: ['ADMINISTRADOR', 'GERENTE', 'CAIXA'],
  catalogo: ['ADMINISTRADOR', 'GERENTE'],
  motoboy: ['ADMINISTRADOR', 'GERENTE', 'MOTOBOY'],
  estoque: ['ADMINISTRADOR', 'GERENTE'],
  fornecedores: ['ADMINISTRADOR', 'GERENTE'],
  'fichas-tecnicas': ['ADMINISTRADOR', 'GERENTE'],
  dashboard: ['ADMINISTRADOR', 'GERENTE'],
  usuarios: ['ADMINISTRADOR'],
};

export function hasRole(profileName: string | undefined, roles: string[]): boolean {
  return !!profileName && roles.includes(profileName);
}

/**
 * RoleGuard (RF-043): a rota declara `data: { roles: [...] }`. Sem permissao,
 * manda o usuario para a tela inicial do proprio perfil em vez de mostrar
 * uma tela que so devolveria 403. A checagem de verdade continua no servidor.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = (route.data['roles'] as string[] | undefined) ?? [];
  const profile = auth.user()?.profileName;
  if (roles.length === 0 || hasRole(profile, roles)) {
    return true;
  }
  return router.parseUrl(defaultRouteForRole(profile));
};
