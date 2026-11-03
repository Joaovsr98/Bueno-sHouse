import { CEP_PATTERN, CepAddress, CepLookup, maskCep, normalize } from './cep';
import { DeliveryZoneResponse } from './models';

/** Zonas de entrega minimas para os testes (so o que o CepLookup le). */
const ZONES = ['Centro', 'Saúde', 'Pinheiros', 'Jardins'].map(
  (neighborhood, i) => ({ id: i + 1, neighborhood, active: true }) as DeliveryZoneResponse,
);

function emptyAddress(): CepAddress {
  return { street: '', neighborhood: '', city: 'São Paulo', state: 'SP', zipCode: '' };
}

/** Faz o fetch do ViaCEP devolver o JSON informado. */
function fakeViaCep(body: object): void {
  spyOn(window, 'fetch').and.resolveTo(new Response(JSON.stringify(body)));
}

describe('maskCep / CEP_PATTERN', () => {
  it('formata enquanto a pessoa digita e descarta letras', () => {
    expect(maskCep('04144')).toBe('04144');
    expect(maskCep('041440')).toBe('04144-0');
    expect(maskCep('04a14b4000')).toBe('04144-000');
    expect(maskCep('041440001234')).toBe('04144-000');
  });

  it('so aceita o formato completo 00000-000', () => {
    expect(CEP_PATTERN.test('04144-000')).toBeTrue();
    expect(CEP_PATTERN.test('04144000')).toBeFalse();
    expect(CEP_PATTERN.test('abc')).toBeFalse();
  });

  it('normaliza acento e caixa', () => {
    expect(normalize('  Saúde ')).toBe('saude');
    expect(normalize('Consolação')).toBe('consolacao');
  });
});

describe('CepLookup', () => {
  it('preenche rua, cidade e o bairro atendido ("Vila da Saude" -> Saude)', async () => {
    fakeViaCep({ logradouro: 'Rua Carneiro da Cunha', bairro: 'Vila da Saúde', localidade: 'São Paulo', uf: 'SP' });
    const cep = new CepLookup();
    const addr = emptyAddress();

    await cep.onInput('04144000', addr, ZONES);

    expect(cep.status()).toBe('ok');
    expect(addr.zipCode).toBe('04144-000');
    expect(addr.street).toBe('Rua Carneiro da Cunha');
    expect(addr.neighborhood).toBe('Saúde');
    expect(cep.validate(addr)).toBeNull();
  });

  it('entende o nome dos Correios para o Centro ("Se")', async () => {
    fakeViaCep({ logradouro: 'Praça da Sé', bairro: 'Sé', localidade: 'São Paulo', uf: 'SP' });
    const cep = new CepLookup();
    const addr = emptyAddress();

    await cep.onInput('01001-000', addr, ZONES);

    expect(addr.neighborhood).toBe('Centro');
    expect(cep.pickupOnly()).toBeFalse();
  });

  it('bairro sem entrega vira "so retirada" e nao deixa salvar o endereco', async () => {
    fakeViaCep({ logradouro: 'Rua Bocaina de Minas', bairro: 'Jardim Tupi', localidade: 'São Paulo', uf: 'SP' });
    const cep = new CepLookup();
    const addr = emptyAddress();

    await cep.onInput('04939-200', addr, ZONES);

    expect(cep.status()).toBe('out-of-area');
    expect(cep.pickupOnly()).toBeTrue();
    expect(addr.neighborhood).toBe('');
    expect(cep.validate(addr)).toContain('retirar no balcão');
  });

  it('CEP de outra cidade tambem vira "so retirada"', async () => {
    fakeViaCep({ bairro: 'Jardim São Judas Tadeu', localidade: 'Taboão da Serra', uf: 'SP' });
    const cep = new CepLookup();

    await cep.onInput('06786-075', emptyAddress(), ZONES);

    expect(cep.status()).toBe('outside');
    expect(cep.message()).toContain('Taboão da Serra');
  });

  it('CEP inexistente e recusado', async () => {
    fakeViaCep({ erro: true });
    const cep = new CepLookup();
    const addr = emptyAddress();

    await cep.onInput('99999-999', addr, ZONES);

    expect(cep.status()).toBe('not-found');
    expect(cep.validate(addr)).toContain('CEP não encontrado');
  });

  it('com o ViaCEP fora do ar, deixa preencher a mao', async () => {
    spyOn(window, 'fetch').and.rejectWith(new TypeError('sem rede'));
    const cep = new CepLookup();

    await cep.onInput('04144-000', emptyAddress(), ZONES);

    expect(cep.status()).toBe('offline');
    expect(cep.pickupOnly()).toBeFalse();
  });
});
