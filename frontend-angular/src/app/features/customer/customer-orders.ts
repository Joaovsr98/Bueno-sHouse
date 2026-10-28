import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderResponse } from '../../core/models';
import { currency } from '../../core/format';
import { orderStatusLabel } from '../../core/order-status';


@Component({
  selector: 'app-customer-orders',
  imports: [RouterLink],
  templateUrl: './customer-orders.html',
})
export class CustomerOrders {
  private readonly api = inject(ApiService);

  readonly currency = currency;
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
    return orderStatusLabel(status);
  }
}
