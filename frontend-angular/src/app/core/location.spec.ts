import { RESTAURANT, directionsLink, distanceKm, formatDistance, geocode, mapEmbedUrl } from './location';

describe('location', () => {
  it('calcula a distancia em linha reta (Pinheiros -> Jardim Angela ~15 km)', () => {
    const km = distanceKm(RESTAURANT, { lat: -23.6888, lng: -46.7693 });
    expect(km).toBeGreaterThan(14.5);
    expect(km).toBeLessThan(16);
  });

  it('formata em metros abaixo de 1 km e com virgula acima', () => {
    expect(formatDistance(0.65)).toBe('650 m');
    expect(formatDistance(8.43)).toBe('8,4 km');
  });

  it('monta os links publicos do Google Maps', () => {
    expect(mapEmbedUrl('Rua Pais Leme')).toContain('output=embed');
    expect(directionsLink('Destino', 'Origem')).toContain('origin=Origem');
    expect(directionsLink('Destino')).not.toContain('origin=');
  });

  it('fica com o resultado da capital e com o CEP mais parecido (nao a rua homonima de Guarulhos)', async () => {
    spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify([
      { lat: '-23.40', lon: '-46.39', address: { city: 'Guarulhos', postcode: '07175-130' } },
      { lat: '-23.70', lon: '-46.77', address: { city: 'São Paulo', postcode: '04800-000' } },
      { lat: '-23.69', lon: '-46.76', address: { city: 'São Paulo', postcode: '04940-070' } },
    ])));

    const where = await geocode({ street: 'Rua Bocaina de Minas', zipCode: '04939-200' });

    expect(where).toEqual({ lat: -23.69, lng: -46.76 });
  });
});
