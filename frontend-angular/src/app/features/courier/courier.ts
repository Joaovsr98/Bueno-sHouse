import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer } from '@angular/platform-browser';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { DeliveryResponse } from '../../core/models';
import { currency, waitingLabel } from '../../core/format';
import {
  LatLng, RESTAURANT, detourKm, directionsLink, distanceKm, formatDistance, geocode, multiStopLink,
  parseAddressSnapshot, routeEmbedUrl,
} from '../../core/location';
import { Icon } from '../../shared/icon';

const STATUS_LABELS: Record<string, string> = {
  AGUARDANDO_ENTREGADOR: 'Aguardando entregador',
  ACEITA: 'Aceita',
  RETIRADA_NO_RESTAURANTE: 'Retirada no restaurante',
  EM_ROTA: 'Em rota',
  ENTREGUE: 'Entregue',
  NAO_ENTREGUE: 'Não entregue',
  DEVOLVIDA: 'Devolvida',
  CANCELADA: 'Cancelada',
};

/** Ate quanto uma parada a mais pode alongar o trajeto (linha reta) para contar como "no caminho". */
const ON_THE_WAY_KM = 2;

/** Entrega disponivel com o quanto ela desvia do trajeto atual. */
interface Nearby { delivery: DeliveryResponse; detour: number | null; }

/**
 * Area do motoboy. A fila anda por ordem de chegada: sem entrega em maos, ele so pega a
 * que espera ha mais tempo (as outras aparecem "na fila"). Com uma em maos, ve o que esta
 * no caminho e pode levar junto. As entregas dele sobrevivem a recarregar a pagina, e a
 * rota abre no Google Maps com todas as paradas.
 */
@Component({
  selector: 'app-courier',
  imports: [FormsModule, NgTemplateOutlet, Icon],
  templateUrl: './courier.html',
  styles: [`
    /* Entrega que quase nao alonga o trajeto: destaque verde para levar junto. */
    .on-the-way { border-color: rgba(22, 163, 74, 0.55) !important; }
  `],
})
export class Courier implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly currency = currency;
  readonly formatDistance = formatDistance;
  readonly statusLabels: Partial<Record<string, string>> = STATUS_LABELS;
  readonly onTheWayKm = ON_THE_WAY_KM;

  /** Fila de entregas prontas, da que espera ha mais tempo para a mais nova. */
  pool = signal<DeliveryResponse[]>([]);
  /** Entregas em andamento deste motoboy. */
  mine = signal<DeliveryResponse[]>([]);
  selectedId = signal<number | null>(null);
  error = signal<string | null>(null);
  confirmationCode = '';
  receivedByName = '';
  /** Relogio da tela: o "esperando ha" anda a cada consulta da fila. */
  private readonly now = signal(Date.now());

  readonly active = computed(() => {
    const list = this.mine();
    return list.find((d) => d.id === this.selectedId()) ?? list[0] ?? null;
  });

  /** Sem entrega em maos, a primeira da fila e a unica que ele pode pegar. */
  readonly next = computed(() => (this.mine().length ? null : (this.pool()[0] ?? null)));
  /** As que esperam a vez (sem botao de aceitar). */
  readonly queued = computed(() => (this.mine().length ? [] : this.pool().slice(1)));

  /** Entregas antigas, sem localizacao no servidor: coordenadas buscadas aqui (OpenStreetMap), uma a uma. */
  private readonly coords = signal<Record<string, LatLng | null>>({});
  private geocoding = false;

  /** Localizacao da entrega: a do servidor ou a buscada aqui. undefined = buscando; null = nao achou. */
  private coordsOf(d: DeliveryResponse): LatLng | null | undefined {
    if (d.latitude != null && d.longitude != null) return { lat: d.latitude, lng: d.longitude };
    return this.coords()[d.addressSnapshot];
  }

  /** Endereco para o Google: o completo, ou so o bairro quando ainda esta oculto. */
  private placeOf(d: DeliveryResponse): string {
    return d.addressHidden ? `${d.neighborhoodSnapshot}, São Paulo - SP` : d.addressSnapshot;
  }

  /**
   * Destino do mapa embutido: as coordenadas, assim o Google sempre traca a rota (com
   * endereco digitado errado ele mostrava o mapa-mundi) e ela bate com a distancia
   * exibida. Sem coordenadas, vai o endereco escrito; undefined = localizando.
   */
  private readonly mapTarget = computed<string | null | undefined>(() => {
    const d = this.active() ?? this.next();
    if (!d) return null;
    const at = this.coordsOf(d);
    if (at === undefined) return undefined;
    return at ? `${at.lat},${at.lng}` : this.placeOf(d);
  });

  /** Mapa embutido da cozinha ate o destino (so recarrega quando o destino muda). */
  readonly routeEmbed = computed(() => {
    const to = this.mapTarget();
    return to ? this.sanitizer.bypassSecurityTrustResourceUrl(routeEmbedUrl(RESTAURANT.fallbackAddress, to)) : null;
  });

  /** Disponiveis ordenadas pelo desvio que causam no trajeto da entrega aberta. */
  readonly nearby = computed<Nearby[]>(() => {
    const d = this.active();
    const to = d ? this.coordsOf(d) : null;
    return this.pool()
      .map((p) => {
        const stop = this.coordsOf(p);
        return { delivery: p, detour: to && stop ? Math.max(0, detourKm(RESTAURANT, stop, to)) : null };
      })
      .sort((a, b) => (a.detour ?? 99) - (b.detour ?? 99));
  });

  /** Distancia da cozinha ate o endereco (para a lista de disponiveis). */
  distanceOf(d: DeliveryResponse): string | null {
    const at = this.coordsOf(d);
    return at ? formatDistance(distanceKm(RESTAURANT, at)) : null;
  }

  /** "esperando ha 12 min" desde que o pedido ficou pronto. */
  waiting(d: DeliveryResponse): string | null {
    return waitingLabel(d.releasedAt, this.now());
  }

  /** Rota no Google Maps: com varias entregas aceitas, uma parada para cada (a mais longe por ultimo). */
  routeLink(): string {
    const d = this.active();
    if (!d) return '';
    const fromKitchen = d.status === 'ACEITA' || d.status === 'RETIRADA_NO_RESTAURANTE';
    const origin = fromKitchen ? RESTAURANT.fallbackAddress : null; // em rota: parte de onde o motoboy esta
    const all = this.mine();
    if (all.length < 2) return directionsLink(d.addressSnapshot, origin ?? undefined);
    const byDistance = [...all].sort((a, b) => this.kmFromKitchen(this.coordsOf(a)) - this.kmFromKitchen(this.coordsOf(b)));
    const last = byDistance[byDistance.length - 1];
    return multiStopLink(origin, byDistance.slice(0, -1).map((x) => x.addressSnapshot), last.addressSnapshot);
  }

  private kmFromKitchen(at: LatLng | null | undefined): number {
    return at ? distanceKm(RESTAURANT, at) : 0;
  }

  /** Abre no Google Maps o destino de uma entrega da fila (ponto aproximado), saindo da cozinha. */
  previewLink(d: DeliveryResponse): string {
    const at = this.coordsOf(d);
    return directionsLink(at ? `${at.lat},${at.lng}` : this.placeOf(d), RESTAURANT.fallbackAddress);
  }

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.loadMine();
    this.loadPool();
    this.pollHandle = setInterval(() => {
      this.now.set(Date.now());
      this.loadPool();
    }, 10000);
    // So entregas antigas, sem localizacao no servidor, sao localizadas aqui (devagar: o
    // OpenStreetMap pede no maximo 1 consulta por segundo).
    effect(() => {
      const missing = [...this.mine(), ...this.pool()].filter((d) => d.latitude == null);
      untracked(() => this.geocodeMissing(missing));
    });
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  private async geocodeMissing(list: DeliveryResponse[]): Promise<void> {
    if (this.geocoding) return;
    this.geocoding = true;
    try {
      for (const d of list) {
        const address = d.addressSnapshot;
        if (address in this.coords()) continue;
        // Endereco oculto (entrega na fila): so o bairro, como o servidor manda.
        const at = await geocode(d.addressHidden ? { neighborhood: d.neighborhoodSnapshot } : parseAddressSnapshot(address));
        this.coords.update((c) => ({ ...c, [address]: at }));
        await new Promise((r) => setTimeout(r, 1100));
      }
    } finally {
      this.geocoding = false;
    }
  }

  loadPool(): void {
    this.api.get<DeliveryResponse[]>('/deliveries/available/details').subscribe({
      next: (list) => this.pool.set(list),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar entregas')),
    });
  }

  loadMine(selectId?: number): void {
    this.api.get<DeliveryResponse[]>('/deliveries/mine').subscribe({
      next: (list) => {
        this.mine.set(list);
        if (selectId != null) this.selectedId.set(selectId);
        else if (!list.some((d) => d.id === this.selectedId())) this.selectedId.set(list[0]?.id ?? null);
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar suas entregas')),
    });
  }

  accept(id: number): void {
    this.api.post<DeliveryResponse>(`/deliveries/${id}/accept`).subscribe({
      next: (d) => {
        this.error.set(null);
        this.loadMine(this.active()?.id ?? d.id);
        this.loadPool();
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'Erro ao aceitar entrega'));
        this.loadPool(); // a fila pode ter andado (outro motoboy pegou antes)
      },
    });
  }

  /** Atualiza a entrega na lista "minhas" depois de uma acao. */
  private replace(d: DeliveryResponse): void {
    this.mine.update((list) => list.map((x) => (x.id === d.id ? d : x)));
  }

  pickup(): void {
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/pickup`).subscribe({
      next: (d) => this.replace(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao confirmar retirada')),
    });
  }

  leave(): void {
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/leave`).subscribe({
      next: (d) => this.replace(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao sair para entrega')),
    });
  }

  confirm(): void {
    this.api
      .post<DeliveryResponse>(`/deliveries/${this.active()!.id}/confirm`, {
        confirmationCode: this.confirmationCode,
        receivedByName: this.receivedByName,
      })
      .subscribe({
        next: () => {
          this.confirmationCode = '';
          this.receivedByName = '';
          this.loadMine();
          this.loadPool();
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao confirmar entrega')),
      });
  }

  reportFailure(): void {
    const reason = window.prompt('Motivo da não entrega:');
    if (!reason) return;
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/failure`, { reason }).subscribe({
      next: (d) => this.replace(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao registrar falha')),
    });
  }

  canReportFailure(status: string): boolean {
    return status === 'EM_ROTA' || status === 'NAO_ENTREGUE';
  }
}
