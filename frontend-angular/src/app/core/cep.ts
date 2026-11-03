import { computed, signal } from '@angular/core';
import { DeliveryZoneResponse } from './models';

/** Formato aceito (o mesmo que o backend valida). */
export const CEP_PATTERN = /^\d{5}-\d{3}$/;

/** A cozinha so entrega na capital. */
const DELIVERY_CITY = 'Sao Paulo';

/** Mascara 00000-000 enquanto a pessoa digita (descarta o que nao for numero). */
export function maskCep(value: string): string {
  const digits = (value ?? '').replace(/\D/g, '').slice(0, 8);
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits;
}

/**
 * Nomes dos Correios (ViaCEP) que na lista de entrega aparecem com outro nome: o Centro
 * e "Se" para os Correios e "Jardins" junta varios Jardins. O backend usa a mesma tabela.
 */
export const NEIGHBORHOOD_ALIASES: Record<string, string> = {
  se: 'centro',
  luz: 'centro',
  'santa efigenia': 'centro',
  'jardim paulista': 'jardins',
  'jardim america': 'jardins',
  'jardim europa': 'jardins',
  'cerqueira cesar': 'jardins',
};

/** Compara nomes ignorando acento, caixa e espacos extras ("Saúde" == "saude"). */
export function normalize(text: string): string {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

interface ViaCepResponse {
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean | string;
}

/** Endereco que o formulario preenche (cadastro e checkout usam o mesmo formato). */
export interface CepAddress {
  street: string;
  neighborhood: string;
  city: string;
  state: string;
  zipCode: string;
}

/** outside = outra cidade; out-of-area = Sao Paulo, mas bairro sem entrega (pode retirar no balcao). */
export type CepStatus = 'idle' | 'loading' | 'ok' | 'not-found' | 'outside' | 'out-of-area' | 'offline';

/**
 * CEP do endereco de entrega: mascara, busca no ViaCEP (servico publico dos Correios)
 * e preenchimento automatico de rua, bairro, cidade e UF. CEP de outra cidade ou de bairro
 * sem entrega vira "so retirada"; nos demais, escolhe o bairro atendido. Se o ViaCEP estiver fora do
 * ar, a pessoa preenche a mao e so o formato e conferido.
 */
export class CepLookup {
  readonly status = signal<CepStatus>('idle');
  /** Explicacao para a pessoa: o que foi preenchido ou o que esta errado. */
  readonly message = signal<string | null>(null);
  private lastLookup = '';

  /** CEP valido, mas sem entrega: o cadastro e o checkout oferecem a retirada no balcao. */
  readonly pickupOnly = computed(() => this.status() === 'outside' || this.status() === 'out-of-area');

  /** Chamado a cada digitacao no campo CEP; busca quando os 8 digitos estao completos. */
  async onInput(raw: string, addr: CepAddress, zones: DeliveryZoneResponse[]): Promise<void> {
    addr.zipCode = maskCep(raw);
    if (!CEP_PATTERN.test(addr.zipCode)) {
      this.status.set('idle');
      this.message.set(null);
      this.lastLookup = '';
      return;
    }
    if (addr.zipCode === this.lastLookup) return;
    this.lastLookup = addr.zipCode;
    this.status.set('loading');
    this.message.set('Buscando o endereço...');

    let data: ViaCepResponse;
    try {
      const res = await fetch(`https://viacep.com.br/ws/${addr.zipCode.replace('-', '')}/json/`);
      data = await res.json();
    } catch {
      this.status.set('offline');
      this.message.set('Não conseguimos consultar o CEP agora. Preencha o endereço manualmente.');
      return;
    }
    if (addr.zipCode !== this.lastLookup) return; // a pessoa mudou o CEP durante a busca

    if (data.erro) {
      this.status.set('not-found');
      this.message.set('CEP não encontrado. Confira os números.');
      return;
    }
    if (normalize(data.localidade ?? '') !== normalize(DELIVERY_CITY)) {
      this.status.set('outside');
      this.message.set(`Este CEP é de ${data.localidade}/${data.uf}. Ainda não entregamos aí, mas você pode retirar no balcão em Pinheiros.`);
      return;
    }

    addr.city = data.localidade ?? addr.city;
    addr.state = data.uf ?? addr.state;
    if (data.logradouro) addr.street = data.logradouro;

    // Nome igual, ou um contido no outro ("Alto da Lapa" -> Lapa), so com nomes de 4+ letras
    // para um bairro curto nao casar por acaso.
    const bairro = normalize(data.bairro ?? '');
    const zone = zones.find((z) => normalize(z.neighborhood) === (NEIGHBORHOOD_ALIASES[bairro] ?? bairro))
      ?? zones.find((z) => {
        const n = normalize(z.neighborhood);
        return bairro.length >= 4 && n.length >= 4 && (bairro.includes(n) || n.includes(bairro));
      });
    addr.neighborhood = zone?.neighborhood ?? '';
    if (zone) {
      this.status.set('ok');
      this.message.set(`Endereço encontrado: ${data.bairro || zone.neighborhood}, São Paulo/SP. Falta só o número.`);
    } else if (data.bairro) {
      // Bairro de verdade, mas fora da area: nao deixa trocar por um bairro atendido qualquer.
      this.status.set('out-of-area');
      this.message.set(`Ainda não entregamos em ${data.bairro}, mas você pode retirar no balcão em Pinheiros.`);
    } else {
      // CEP geral da cidade (sem bairro): a pessoa escolhe o bairro na lista.
      this.status.set('ok');
      this.message.set('Endereço encontrado. Escolha o seu bairro na lista.');
    }
  }

  /** Erro que impede salvar o endereco, ou null se esta tudo certo. */
  validate(addr: CepAddress): string | null {
    if (!CEP_PATTERN.test(addr.zipCode)) return 'Informe um CEP válido no formato 00000-000.';
    if (this.status() === 'loading') return 'Aguarde a consulta do CEP terminar.';
    if (this.status() === 'not-found' || this.pickupOnly()) return this.message();
    if (normalize(addr.city) !== normalize(DELIVERY_CITY)) return 'Por enquanto entregamos só na cidade de São Paulo.';
    return null;
  }

  reset(): void {
    this.status.set('idle');
    this.message.set(null);
    this.lastLookup = '';
  }
}
