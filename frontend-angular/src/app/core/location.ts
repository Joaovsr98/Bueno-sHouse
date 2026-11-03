/**
 * Onde fica a cozinha e quanto o cliente esta longe dela.
 *
 * O endereco exibido vem da unidade (GET /api/units); as coordenadas da Rua Pais Leme,
 * em Pinheiros, ficam aqui porque o banco nao guarda latitude/longitude. Mapa e rota
 * usam links publicos do Google Maps (sem chave de API); a localizacao do cliente vem
 * do OpenStreetMap (Nominatim), servico gratuito de geocodificacao.
 */
export const RESTAURANT = {
  /** Usado no mapa e na rota quando a unidade ainda nao carregou. */
  fallbackAddress: 'Rua Pais Leme - Pinheiros, São Paulo - SP, 05424-150',
  lat: -23.5682,
  lng: -46.6985,
};

export interface LatLng {
  lat: number;
  lng: number;
}

/** Mapa do Google embutido (iframe), centrado no endereco. */
export function mapEmbedUrl(address: string): string {
  return `https://maps.google.com/maps?q=${encodeURIComponent(address)}&z=16&output=embed`;
}

/** Abre o endereco no Google Maps. */
export function mapLink(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

/** Rota no Google Maps; sem origem, o proprio Google usa a localizacao de quem abriu. */
export function directionsLink(destination: string, origin?: string): string {
  const from = origin ? `&origin=${encodeURIComponent(origin)}` : '';
  return `https://www.google.com/maps/dir/?api=1${from}&destination=${encodeURIComponent(destination)}`;
}

/** Distancia em linha reta (formula de Haversine), em km. */
export function distanceKm(a: LatLng, b: LatLng): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}

/** "1,8 km" / "650 m". */
export function formatDistance(km: number): string {
  return km < 1 ? `${Math.round(km * 100) * 10} m` : `${km.toFixed(1).replace('.', ',')} km`;
}

/**
 * Retangulo da cidade de Sao Paulo (oeste, norte, leste, sul). A busca fica presa nele:
 * "city=São Paulo" sozinho tambem casa com o estado e devolvia ruas de mesmo nome em
 * Guarulhos ou Osasco.
 */
const SAO_PAULO_VIEWBOX = '-46.83,-23.35,-46.36,-24.01';

interface NominatimPlace {
  lat: string;
  lon: string;
  address?: { postcode?: string; city?: string; town?: string; municipality?: string };
}

/** Quantos digitos iniciais do CEP batem: ruas homonimas na mesma cidade tem CEPs diferentes. */
function cepScore(place: NominatimPlace, zipCode?: string): number {
  const want = (zipCode ?? '').replace(/\D/g, '');
  const got = (place.address?.postcode ?? '').replace(/\D/g, '');
  let n = 0;
  while (n < want.length && n < got.length && want[n] === got[n]) n++;
  return n;
}

/**
 * Coordenadas de um endereco da cidade de Sao Paulo pelo OpenStreetMap. Tenta rua +
 * numero, depois o CEP e por fim o bairro; entre os resultados, fica com o da capital
 * cujo CEP mais se parece com o informado. Devolve null se nada for encontrado ou se o
 * servico estiver fora do ar.
 */
export async function geocode(parts: { street?: string; number?: string; neighborhood?: string; zipCode?: string }): Promise<LatLng | null> {
  const attempts: string[] = [];
  if (parts.street) {
    attempts.push(`street=${encodeURIComponent(`${parts.number ?? ''} ${parts.street}`.trim())}&city=S%C3%A3o%20Paulo&country=Brasil`);
  }
  if (parts.zipCode) attempts.push(`postalcode=${encodeURIComponent(parts.zipCode)}&country=Brasil`);
  if (parts.neighborhood) attempts.push(`q=${encodeURIComponent(`${parts.neighborhood}, São Paulo, SP`)}`);

  for (const query of attempts) {
    try {
      const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=10&addressdetails=1`
        + `&viewbox=${SAO_PAULO_VIEWBOX}&bounded=1&${query}`);
      const found: NominatimPlace[] = await res.json();
      const inCity = found.filter((p) => (p.address?.city ?? p.address?.municipality ?? p.address?.town) === 'São Paulo');
      const candidates = inCity.length ? inCity : found;
      if (candidates.length) {
        const best = candidates.reduce((a, b) => (cepScore(b, parts.zipCode) > cepScore(a, parts.zipCode) ? b : a));
        return { lat: Number(best.lat), lng: Number(best.lon) };
      }
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * Le o endereco gravado na entrega ("Rua das Flores, 100 (apto 2) - Centro, Sao Paulo/SP -
 * CEP 01000-000") e devolve as partes que o geocode usa.
 */
export function parseAddressSnapshot(snapshot: string): { street?: string; number?: string; neighborhood?: string; zipCode?: string } {
  const m = /^(.*?),\s*([^\s(]+)(?:\s*\([^)]*\))?\s*-\s*(.*?),\s*.*?\/\w{2}\s*-\s*CEP\s*(\d{5}-?\d{3})/.exec(snapshot ?? '');
  if (!m) return { street: snapshot };
  return { street: m[1].trim(), number: m[2], neighborhood: m[3].trim(), zipCode: m[4] };
}

/** Rota com paradas no Google Maps (ate 9 paradas no computador, 3 no celular). */
export function multiStopLink(origin: string | null, stops: string[], destination: string): string {
  const from = origin ? `&origin=${encodeURIComponent(origin)}` : '';
  const via = stops.length ? `&waypoints=${stops.map(encodeURIComponent).join('%7C')}` : '';
  return `https://www.google.com/maps/dir/?api=1${from}&destination=${encodeURIComponent(destination)}${via}&travelmode=driving`;
}

/** Mapa embutido com o trajeto de um ponto a outro (sem chave de API). */
export function routeEmbedUrl(origin: string, destination: string): string {
  return `https://maps.google.com/maps?saddr=${encodeURIComponent(origin)}&daddr=${encodeURIComponent(destination)}&output=embed`;
}

/**
 * Quanto uma parada a mais alonga o trajeto da cozinha ate o destino, em linha reta:
 * (cozinha -> parada) + (parada -> destino) - (cozinha -> destino).
 */
export function detourKm(from: LatLng, stop: LatLng, to: LatLng): number {
  return distanceKm(from, stop) + distanceKm(stop, to) - distanceKm(from, to);
}
