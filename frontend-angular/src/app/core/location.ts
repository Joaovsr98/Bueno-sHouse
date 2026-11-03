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
 * Coordenadas de um endereco de Sao Paulo pelo OpenStreetMap. Tenta rua + numero e,
 * se nao achar, so a rua (ou o CEP). Devolve null se nada for encontrado ou se o
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
      const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=1&${query}`);
      const found: { lat: string; lon: string }[] = await res.json();
      if (found[0]) return { lat: Number(found[0].lat), lng: Number(found[0].lon) };
    } catch {
      return null;
    }
  }
  return null;
}
