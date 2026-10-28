import { DeliveryZoneResponse } from './models';

export interface ZoneGroup {
  region: string;
  zones: DeliveryZoneResponse[];
}

/** Ordem das regioes no seletor: da mais perto da cozinha para a mais longe. */
const REGION_ORDER = ['Zona Oeste', 'Centro', 'Zona Sul', 'Zona Norte', 'Zona Leste'];

/** Agrupa os bairros atendidos por regiao (zoneName), em ordem alfabetica dentro de cada uma. */
export function groupZones(zones: DeliveryZoneResponse[]): ZoneGroup[] {
  const byRegion = new Map<string, DeliveryZoneResponse[]>();
  for (const z of zones.filter((z) => z.active)) {
    const list = byRegion.get(z.zoneName) ?? [];
    list.push(z);
    byRegion.set(z.zoneName, list);
  }
  const rank = (r: string) => (REGION_ORDER.includes(r) ? REGION_ORDER.indexOf(r) : REGION_ORDER.length);
  return [...byRegion.entries()]
    .sort(([a], [b]) => rank(a) - rank(b) || a.localeCompare(b))
    .map(([region, list]) => ({ region, zones: list.sort((a, b) => a.neighborhood.localeCompare(b.neighborhood)) }));
}
