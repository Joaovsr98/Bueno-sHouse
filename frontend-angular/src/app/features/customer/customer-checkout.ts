import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { CustomerResponse, CustomerAddressResponse, OrderResponse, UnitResponse } from '../../core/models';
import { currency } from '../../core/format';

@Component({
  selector: 'app-customer-checkout',
  imports: [RouterLink],
  templateUrl: './customer-checkout.html',
})
export class CustomerCheckout {
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly currency = currency;
  readonly lines = this.cart.lines;
  readonly subtotal = this.cart.subtotal;

  addresses = signal<CustomerAddressResponse[]>([]);
  selectedAddressId = signal<number | null>(null);
  error = signal<string | null>(null);
  placing = signal(false);

  constructor() {
    this.loadAddresses();
  }

  private async loadAddresses(): Promise<void> {
    try {
      const me = await firstValueFrom(this.api.get<CustomerResponse>('/customers/me'));
      this.addresses.set(me.addresses);
      const def = me.addresses.find((a) => a.isDefault) ?? me.addresses[0];
      if (def) this.selectedAddressId.set(def.id);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao carregar seus endereços'));
    }
  }

  selectAddress(id: number): void {
    this.selectedAddressId.set(id);
  }

  changeQty(productId: number, delta: number): void {
    this.cart.changeQty(productId, delta);
  }

  remove(productId: number): void {
    this.cart.remove(productId);
  }

  async placeOrder(): Promise<void> {
    if (this.lines().length === 0 || this.selectedAddressId() == null) return;
    this.placing.set(true);
    this.error.set(null);
    try {
      const units = await firstValueFrom(this.api.get<UnitResponse[]>('/units'));
      const unitId = units[0]?.id ?? 1;
      const order = await firstValueFrom(
        this.api.post<OrderResponse>('/orders', {
          unitId,
          channel: 'DELIVERY',
          customerAddressId: this.selectedAddressId(),
          items: this.lines().map((l) => ({ productId: l.productId, quantity: l.quantity })),
        }),
      );
      this.cart.clear();
      this.router.navigate(['/app/pedidos', order.id]);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao finalizar o pedido'));
    } finally {
      this.placing.set(false);
    }
  }
}
