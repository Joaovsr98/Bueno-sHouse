import { Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../core/api.service';
import { UnitResponse } from '../core/models';
import { CEP_PATTERN, maskCep } from '../core/cep';
import { RESTAURANT, directionsLink, distanceKm, formatDistance, geocode, mapEmbedUrl, mapLink } from '../core/location';
import { Icon } from './icon';

/**
 * "Onde estamos": endereco da cozinha, mapa do Google, rota e a distancia ate o CEP
 * do cliente. Serve tanto para quem pede entrega quanto para quem quer conhecer o lugar.
 */
@Component({
  selector: 'app-location-card',
  imports: [Icon],
  template: `
    <section class="glass rounded-3xl overflow-hidden grid grid-cols-1 lg:grid-cols-[1fr_1.15fr]" aria-labelledby="onde-estamos">
      <div class="p-6 sm:p-8 flex flex-col gap-5">
        <div>
          <p class="eyebrow">Onde estamos</p>
          <h2 id="onde-estamos" class="mt-3 text-3xl font-bold uppercase leading-[0.95]">Vem conhecer<br /><span class="text-ember">a nossa chapa.</span></h2>
        </div>

        <div class="space-y-2 text-sm">
          <p class="flex items-start gap-2.5 text-neutral-200">
            <span class="text-brand-400 mt-0.5"><app-icon name="pin" [size]="16" /></span>{{ address() }}
          </p>
          @if (phone()) {
            <p class="flex items-center gap-2.5 text-neutral-400">
              <span class="text-brand-400"><app-icon name="clock" [size]="16" /></span>Pedidos e dúvidas: {{ phone() }}
            </p>
          }
        </div>

        <div class="flex flex-wrap gap-2">
          <a [href]="directions()" target="_blank" rel="noopener" class="btn-brand rounded-xl px-4 py-2.5 text-sm inline-flex items-center gap-2">
            Como chegar <app-icon name="arrow" [size]="15" />
          </a>
          <a [href]="openMap()" target="_blank" rel="noopener" class="btn-ghost !text-sm !py-2.5">Abrir no Google Maps</a>
        </div>

        <div class="border-t border-white/10 pt-5 space-y-3">
          <label class="field-label" for="cep-distancia">Quanto você está longe da gente?</label>
          <div class="flex gap-2">
            <input id="cep-distancia" #cepInput (input)="onCep(cepInput)" inputmode="numeric" maxlength="9" placeholder="Seu CEP"
              class="field py-2.5 !w-40" />
            @if (status() === 'loading') { <span class="self-center text-xs text-neutral-500">Calculando...</span> }
          </div>
          @if (result(); as r) {
            <p class="text-sm text-neutral-300" aria-live="polite">
              <strong class="text-brand-300 font-display text-lg">{{ r.distance }}</strong> em linha reta até
              {{ r.place }}.
              <a [href]="r.route" target="_blank" rel="noopener" class="text-brand-400 hover:text-brand-300 whitespace-nowrap">Ver a rota de verdade</a>
            </p>
          } @else if (status() === 'error') {
            <p class="text-xs text-red-400" aria-live="polite">{{ errorText() }}</p>
          } @else {
            <p class="text-xs text-neutral-500">Digite o CEP para ver a distância e a rota até a cozinha.</p>
          }
        </div>
      </div>

      <div class="relative min-h-72 lg:min-h-full bg-ink-900">
        <iframe [src]="embed()" title="Mapa com a localização do Bueno's House" loading="lazy"
          referrerpolicy="no-referrer-when-downgrade" class="absolute inset-0 w-full h-full border-0 map-dark"></iframe>
      </div>
    </section>
  `,
  styles: [`
    /* Mapa claro do Google puxado para o tema escuro sem perder a leitura das ruas. */
    .map-dark { filter: invert(0.9) hue-rotate(180deg) saturate(0.7) brightness(0.95); }
  `],
})
export class LocationCard {
  private readonly api = inject(ApiService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly address = signal(RESTAURANT.fallbackAddress);
  readonly phone = signal<string | null>(null);
  readonly embed = computed(() => this.sanitizer.bypassSecurityTrustResourceUrl(mapEmbedUrl(this.address())));
  readonly openMap = computed(() => mapLink(this.address()));
  readonly directions = computed(() => directionsLink(this.address()));

  readonly status = signal<'idle' | 'loading' | 'done' | 'error'>('idle');
  readonly errorText = signal('');
  readonly result = signal<{ distance: string; place: string; route: string } | null>(null);
  private lastCep = '';

  constructor() {
    firstValueFrom(this.api.get<UnitResponse[]>('/units'))
      .then((units) => {
        const unit = units[0];
        if (unit?.address) this.address.set(unit.address);
        if (unit?.phone) this.phone.set(unit.phone);
      })
      .catch(() => undefined); // sem API: fica o endereco padrao
  }

  async onCep(input: HTMLInputElement): Promise<void> {
    const cep = maskCep(input.value);
    input.value = cep;
    if (!CEP_PATTERN.test(cep)) {
      this.status.set('idle');
      this.result.set(null);
      this.lastCep = '';
      return;
    }
    if (cep === this.lastCep) return;
    this.lastCep = cep;
    this.status.set('loading');
    this.result.set(null);

    try {
      const data = await (await fetch(`https://viacep.com.br/ws/${cep.replace('-', '')}/json/`)).json();
      if (cep !== this.lastCep) return;
      if (data.erro) return this.fail('CEP não encontrado. Confira os números.');
      const where = await geocode({ street: data.logradouro, neighborhood: data.bairro, zipCode: cep });
      if (cep !== this.lastCep) return;
      if (!where) return this.fail('Não conseguimos localizar este CEP no mapa agora.');

      const place = [data.logradouro, data.bairro, data.localidade].filter(Boolean).join(', ');
      this.result.set({
        distance: formatDistance(distanceKm(RESTAURANT, where)),
        place,
        route: directionsLink(this.address(), `${place}, ${data.uf}, ${cep}`),
      });
      this.status.set('done');
    } catch {
      this.fail('Não conseguimos consultar o CEP agora. Tente de novo em instantes.');
    }
  }

  private fail(text: string): void {
    this.errorText.set(text);
    this.status.set('error');
  }
}
