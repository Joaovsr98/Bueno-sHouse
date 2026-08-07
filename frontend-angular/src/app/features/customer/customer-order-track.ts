import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderResponse } from '../../core/models';
import { currency } from '../../core/format';

// Etapas visiveis para o cliente (mapeamento dos status internos -> 5 estados
// academicos: recebido → preparando → pronto → a caminho → entregue).
const STEPS = [
  { key: 'received', label: 'Recebido', statuses: ['RECEBIDO'] },
  { key: 'preparing', label: 'Em preparo', statuses: ['ENVIADO_PARA_COZINHA', 'EM_PREPARO', 'PARCIALMENTE_PRONTO'] },
  { key: 'ready', label: 'Pronto', statuses: ['PRONTO', 'AGUARDANDO_ENTREGADOR', 'RETIRADA_NO_RESTAURANTE'] },
  { key: 'delivering', label: 'A caminho', statuses: ['SAIU_PARA_ENTREGA', 'EM_ROTA'] },
  { key: 'delivered', label: 'Entregue', statuses: ['ENTREGUE', 'FINALIZADO'] },
];

@Component({
  selector: 'app-customer-order-track',
  imports: [RouterLink],
  templateUrl: './customer-order-track.html',
})
export class CustomerOrderTrack implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly currency = currency;
  readonly steps = STEPS;
  orderId = this.route.snapshot.paramMap.get('orderId')!;
  order = signal<OrderResponse | null>(null);
  error = signal<string | null>(null);

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
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar o pedido')),
    });
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
