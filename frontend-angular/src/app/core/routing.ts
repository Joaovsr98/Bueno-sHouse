/** Rota inicial por perfil apos o login (espelha o defaultRouteForRole do React). */
export function defaultRouteForRole(profileName: string | undefined): string {
  switch (profileName) {
    case 'MOTOBOY':
      return '/motoboy';
    case 'COZINHA':
      return '/cozinha';
    case 'CAIXA':
      return '/caixa';
    default:
      return '/mesas';
  }
}
