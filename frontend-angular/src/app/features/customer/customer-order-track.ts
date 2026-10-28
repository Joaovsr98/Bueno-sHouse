import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderResponse } from '../../core/models';
import { currency } from '../../core/format';
import { ORDER_TIMELINE } from '../../core/order-status';

@Component({
  selector: 'app-customer-order-track',
  imports: [RouterLink],
  templateUrl: './customer-order-track.html',
})
export class CustomerOrderTrack implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly currency = currency;
  readonly steps = ORDER_TIMELINE;
  orderId = this.route.snapshot.paramMap.get('orderId')!;
  order = signal<OrderResponse | null>(null);
  error = signal<string | null>(null);
  /** Codigo que o cliente informa ao motoboy para confirmar a entrega. */
  deliveryCode = signal<string | null>(null);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.load();
    this.pollHandle = setInterval(() => this.load(), 10000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  private load(): void {
    this.api.get<OrderResponse>(`/orders/${this.orderId}`).subscribe({
      next: (o) => {
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

  isCancelled(): boolean {
    return this.order()?.status === 'CANCELADO';
  }
}
