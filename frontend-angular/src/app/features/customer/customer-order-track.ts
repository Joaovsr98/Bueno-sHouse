import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderResponse } from '../../core/models';
import { currency } from '../../core/format';
import { ORDER_TIMELINE } from '../../core/order-status';
import { OrderAlert, orderAlert } from '../../core/order-alerts';
import { BurgerGame } from '../../shared/burger-game';

@Component({
  selector: 'app-customer-order-track',
  imports: [RouterLink, BurgerGame],
  templateUrl: './customer-order-track.html',
})
export class CustomerOrderTrack implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly currency = currency;
  /** Etapas do pedido; na retirada no balcao nao ha "saiu para entrega" e o fim e "Retirado". */
  get steps() {
    if (this.order()?.channel !== 'RETIRADA') return ORDER_TIMELINE;
    return ORDER_TIMELINE.filter((s) => s.key !== 'delivering').map((s) =>
      s.key === 'ready' ? { ...s, label: 'Pronto p/ retirar' } : s.key === 'delivered' ? { ...s, label: 'Retirado' } : s,
    );
  }
  orderId = this.route.snapshot.paramMap.get('orderId')!;
  order = signal<OrderResponse | null>(null);
  error = signal<string | null>(null);
  /** Codigo que o cliente informa ao motoboy para confirmar a entrega. */
  deliveryCode = signal<string | null>(null);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  /** Ultima fase vista: so avisa quando muda (nao na primeira carga). */
  private lastStatus: string | null = null;
  /** Pop-up na tela com a fase nova (some sozinho). */
  readonly alert = signal<OrderAlert | null>(null);
  private alertTimer: ReturnType<typeof setTimeout> | null = null;
  /** Navegador aceita notificacao do sistema e o cliente ainda nao respondeu se quer. */
  readonly canAskNotifications = signal(typeof Notification !== 'undefined' && Notification.permission === 'default');

  /** Pede para mostrar a notificacao do sistema (canto da tela / central do celular). */
  async enableNotifications(): Promise<void> {
    if (typeof Notification === 'undefined') return;
    await Notification.requestPermission();
    this.canAskNotifications.set(false);
  }

  private announce(o: OrderResponse): void {
    const a = orderAlert(o.status, o.channel, this.deliveryCode());
    if (!a) return;
    this.alert.set(a);
    if (this.alertTimer) clearTimeout(this.alertTimer);
    this.alertTimer = setTimeout(() => this.alert.set(null), 9000);
    navigator.vibrate?.([200, 100, 200]);
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      try {
        new Notification(`${a.icon} ${a.title}`, { body: a.body, icon: '/emblema.webp', tag: `pedido-${o.id}` });
      } catch {
        // celular sem service worker nao abre notificacao assim: fica o pop-up e a vibracao
      }
    }
  }

  constructor() {
    this.load();
    this.pollHandle = setInterval(() => this.load(), 10000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
    if (this.alertTimer) clearTimeout(this.alertTimer);
  }

  private load(): void {
    this.api.get<OrderResponse>(`/orders/${this.orderId}`).subscribe({
      next: (o) => {
        if (this.lastStatus != null && this.lastStatus !== o.status) this.announce(o);
        this.lastStatus = o.status;
        this.order.set(o);
        if (o.status === 'CANCELADO' && this.pollHandle) clearInterval(this.pollHandle);
        if (o.channel === 'DELIVERY' && this.deliveryCode() == null) this.loadDeliveryCode();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar o pedido')),
    });
  }

  private loadDeliveryCode(): void {
    this.api
      .get<{ confirmationCode: string }>(`/orders/${this.orderId}/delivery-code`)
      .subscribe({
        next: (r) => this.deliveryCode.set(r.confirmationCode),
        // sem codigo (ex.: pedido sem entrega) a tela segue normal
        error: () => this.deliveryCode.set(null),
      });
  }

  /** Mostra o codigo enquanto a entrega ainda nao foi concluida. */
  showDeliveryCode(): boolean {
    const status = this.order()?.status;
    return this.deliveryCode() != null && !['ENTREGUE', 'FINALIZADO', 'CANCELADO'].includes(status ?? '');
  }

  /** Índice da etapa atual do pedido (para pintar a timeline). */
  currentStep(): number {
    const status = this.order()?.status;
    if (!status) return -1;
    const idx = this.steps.findIndex((s) => s.statuses.includes(status));
    return idx;
  }

  /** Pedido ainda em andamento: mostra o joguinho para passar o tempo. */
  waiting(): boolean {
    const status = this.order()?.status;
    return !!status && !['ENTREGUE', 'FINALIZADO', 'CANCELADO'].includes(status);
  }

  isCancelled(): boolean {
    return this.order()?.status === 'CANCELADO';
  }
}
