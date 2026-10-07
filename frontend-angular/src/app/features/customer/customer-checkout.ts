import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { CustomerResponse, CustomerAddressResponse, OrderResponse, UnitResponse } from '../../core/models';
import { currency } from '../../core/format';

@Component({
  selector: 'app-customer-checkout',
  imports: [RouterLink, FormsModule],
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
  customerId = signal<number | null>(null);
  showAddressForm = signal(false);
  savingAddress = signal(false);
  addr = { label: '', street: '', number: '', complement: '', neighborhood: '', city: '', state: '', zipCode: '' };

  constructor() {
    this.loadAddresses();
  }

  private async loadAddresses(): Promise<void> {
    try {
      const me = await firstValueFrom(this.api.get<CustomerResponse>('/customers/me'));
      this.customerId.set(me.id);
      this.addresses.set(me.addresses);
      if (me.addresses.length === 0) this.showAddressForm.set(true);
      const def = me.addresses.find((a) => a.isDefault) ?? me.addresses[0];
      if (def) this.selectedAddressId.set(def.id);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao carregar seus endereços'));
    }
  }

  async saveAddress(): Promise<void> {
    const id = this.customerId();
    const a = this.addr;
    if (id == null) return;
    if (!a.street || !a.number || !a.neighborhood || !a.city || !a.state || !a.zipCode) {
      this.error.set('Preencha rua, número, bairro, cidade, UF e CEP');
      return;
    }
    this.savingAddress.set(true);
    this.error.set(null);
    try {
      const created = await firstValueFrom(
        this.api.post<CustomerAddressResponse>(`/customers/${id}/addresses`, {
          ...a,
          label: a.label || 'Casa',
          isDefault: this.addresses().length === 0,
        }),
      );
      this.addresses.update((list) => [...list, created]);
      this.selectedAddressId.set(created.id);
      this.showAddressForm.set(false);
      this.addr = { label: '', street: '', number: '', complement: '', neighborhood: '', city: '', state: '', zipCode: '' };
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao salvar o endereço'));
    } finally {
      this.savingAddress.set(false);
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
