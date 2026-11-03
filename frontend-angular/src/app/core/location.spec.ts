import {
  RESTAURANT, detourKm, directionsLink, distanceKm, formatDistance, geocode, mapEmbedUrl, multiStopLink, parseAddressSnapshot,
} from './location';

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

  it('le o endereco gravado na entrega', () => {
    expect(parseAddressSnapshot('Rua das Flores, 100 - Centro, Sao Paulo/SP - CEP 01000-000'))
      .toEqual({ street: 'Rua das Flores', number: '100', neighborhood: 'Centro', zipCode: '01000-000' });
    expect(parseAddressSnapshot('saude, 447 (casa) - Saúde, São Paulo/SP - CEP 06786-075'))
      .toEqual({ street: 'saude', number: '447', neighborhood: 'Saúde', zipCode: '06786-075' });
  });

  it('mede o desvio de uma parada: no caminho quase nao alonga, fora do caminho alonga muito', () => {
    const cozinha = { lat: -23.568, lng: -46.698 };
    const destino = { lat: -23.588, lng: -46.698 };
    const noCaminho = { lat: -23.578, lng: -46.699 };
    const foraDoCaminho = { lat: -23.568, lng: -46.65 };
    expect(detourKm(cozinha, noCaminho, destino)).toBeLessThan(0.2);
    expect(detourKm(cozinha, foraDoCaminho, destino)).toBeGreaterThan(5);
  });

  it('monta a rota com paradas no Google Maps', () => {
    const url = multiStopLink(null, ['Rua A, 1', 'Rua B, 2'], 'Rua C, 3');
    expect(url).toContain('waypoints=Rua%20A%2C%201%7CRua%20B%2C%202');
    expect(url).toContain('destination=Rua%20C%2C%203');
    expect(url).not.toContain('origin=');
    expect(url).toContain('travelmode=two-wheeler');
    expect(directionsLink('Destino')).toContain('travelmode=two-wheeler');
  });
});
