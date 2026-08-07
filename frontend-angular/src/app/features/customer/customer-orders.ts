import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderResponse } from '../../core/models';
import { currency } from '../../core/format';

const STATUS_LABELS: Record<string, string> = {
  RECEBIDO: 'Recebido',
  ENVIADO_PARA_COZINHA: 'Enviado para a cozinha',
  EM_PREPARO: 'Em preparo',
  PARCIALMENTE_PRONTO: 'Quase pronto',
  PRONTO: 'Pronto',
  AGUARDANDO_ENTREGADOR: 'Aguardando entregador',
  SAIU_PARA_ENTREGA: 'Saiu para entrega',
  ENTREGUE: 'Entregue',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
};

@Component({
  selector: 'app-customer-orders',
  imports: [RouterLink],
  templateUrl: './customer-orders.html',
})
export class CustomerOrders {
  private readonly api = inject(ApiService);

  readonly currency = currency;
  readonly statusLabels = STATUS_LABELS;
  orders = signal<OrderResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor() {
    this.api.get<OrderResponse[]>('/orders/mine').subscribe({
      next: (o) => {
        this.orders.set(o);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'Erro ao carregar seus pedidos'));
        this.loading.set(false);
      },
    });
  }

  label(status: string): string {
    return this.statusLabels[status] ?? status;
  }
}
